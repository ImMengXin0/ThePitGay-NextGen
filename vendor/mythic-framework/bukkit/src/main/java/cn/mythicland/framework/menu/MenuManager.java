package cn.mythicland.framework.menu;

import cn.mythicland.framework.bukkit.ListenerComponent;
import cn.mythicland.framework.bukkit.task.ScheduledTask;
import cn.mythicland.framework.bukkit.task.TaskScheduler;
import cn.mythicland.framework.component.ComponentArguments;
import cn.mythicland.framework.component.ComponentResolver;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

@InjectComponent
@ListenerComponent
public final class MenuManager implements Listener, ManagedLifecycle {
    private final JavaPlugin plugin;
    private final ComponentResolver componentResolver;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, OpenMenuSession> openMenus;
    private long tickCounter;
    private Predicate<Player> interactionAllowed = player -> true;

    public MenuManager(
            JavaPlugin plugin,
            ComponentResolver componentResolver,
            TaskScheduler taskScheduler
    ) {
        this.plugin = plugin;
        this.componentResolver = componentResolver;
        this.taskScheduler = taskScheduler;
        this.openMenus = new ConcurrentHashMap<>();
    }

    public void interactionAllowed(Predicate<Player> interactionAllowed) {
        this.interactionAllowed = interactionAllowed;
    }

    public void open(Player player, AbstractMenu menu) {
        if (player == null || menu == null) return;
        this.taskScheduler.executeSyncIfNeeded(() -> this.openNow(player, menu));
    }

    public <T extends AbstractMenu> void open(
            Player player,
            Class<T> menuType,
            ComponentArguments<?> arguments
    ) {
        this.open(player, this.componentResolver.create(menuType, arguments));
    }

    public AbstractMenu currentMenu(Player player) {
        if (player == null) return null;
        OpenMenuSession session = this.openMenus.get(player.getUniqueId());
        return session == null ? null : session.menu();
    }

    public void refresh(Player player) {
        if (player == null) return;
        this.taskScheduler.executeSyncIfNeeded(() -> {
            OpenMenuSession session = this.openMenus.get(player.getUniqueId());
            if (session != null) this.refreshInPlace(player, session, false);
        });
    }

    public void close(Player player) {
        if (player == null) return;
        this.taskScheduler.executeSyncIfNeeded(() -> this.closeNow(player));
    }

    public void closeNextTick(Player player) {
        if (player == null) return;
        this.taskScheduler.executeSyncLater(() -> this.closeNow(player), 1L);
    }

    public boolean hasOpenMenu(UUID playerUniqueId) {
        return playerUniqueId != null && this.openMenus.containsKey(playerUniqueId);
    }

    public boolean isManagedInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MenuHolder;
    }

    public void clearSessions() {
        this.taskScheduler.executeSyncIfNeeded(this::clearSessionsNow);
    }

    @Override
    public void unload() {
        this.clearSessionsNow();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        this.handleClick(event);
    }

    public void handleClick(InventoryClickEvent event) {
        MenuInteraction interaction = this.resolveMenuInteraction(event);
        if (interaction == null) return;
        Player player = interaction.player();
        MenuHolder holder = interaction.holder();
        event.setCancelled(true);
        if (!this.interactionAllowed.test(player)) return;
        ClickType clickType = ClickType.fromClickType(event.getClick());
        if (clickType == null) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) return;
        if (rawSlot >= interaction.topInventory().getSize()) {
            if (holder.menu() instanceof PlayerInventoryMenuHandler inventoryHandler
                    && inventoryHandler.handlePlayerInventoryClick(player, event, this)) {
                this.refreshIfCurrent(player, holder);
            }
            return;
        }
        AbstractButton button = holder.contents().getButton(rawSlot);
        if (button == null) {
            if (holder.menu().shouldRefreshOnEmptySlotClick(player, rawSlot, clickType)) {
                this.refreshIfCurrent(player, holder);
            }
            return;
        }
        button.onClick(player, clickType, this);
        OpenMenuSession session = this.openMenus.get(player.getUniqueId());
        if (session == null || session.holder() != holder) return;
        if (button.shouldClose(player, clickType)) {
            this.closeNextTick(player);
        } else if (button.shouldRefresh(player, clickType)) {
            this.refreshInPlace(player, session, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        this.handleDrag(event);
    }

    public void handleDrag(InventoryDragEvent event) {
        MenuInteraction interaction = this.resolveMenuInteraction(event);
        if (interaction == null) return;
        if (!this.interactionAllowed.test(interaction.player())) {
            event.setCancelled(true);
            return;
        }
        if (interaction.holder().menu() instanceof PlayerInventoryMenuHandler inventoryHandler
                && inventoryHandler.handlePlayerInventoryDrag(interaction.player(), event, this)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        this.handleClose(event);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MenuHolder holder = this.findMenuHolder(event.getInventory(), player.getUniqueId());
        if (holder == null) return;
        OpenMenuSession session = this.openMenus.get(player.getUniqueId());
        if (session != null && session.holder() == holder) {
            this.openMenus.remove(player.getUniqueId(), session);
            this.clearPageSession(player, session.menu());
        }
        holder.close(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        this.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(this.plugin)) this.clearSessions();
    }

    @ScheduledTask(delayTicks = 1L, periodTicks = 1L)
    private void tickAutoRefresh() {
        this.tickCounter++;
        for (Map.Entry<UUID, OpenMenuSession> entry : this.openMenus.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            OpenMenuSession session = entry.getValue();
            if (!(session.menu() instanceof AutoRefreshMenu autoRefreshMenu)) continue;
            if (!autoRefreshMenu.shouldAutoRefresh(player)) continue;
            long interval = Math.max(1L, autoRefreshMenu.autoRefreshIntervalTicks(player));
            if (this.tickCounter - session.lastRefreshTick() < interval) continue;
            if (this.findMenuHolder(player.getOpenInventory().getTopInventory(), player.getUniqueId())
                    != session.holder()) continue;
            session.lastRefreshTick(this.tickCounter);
            this.refreshInPlace(player, session, true);
        }
    }

    public void handleQuit(Player player) {
        if (player == null) return;
        UUID playerUniqueId = player.getUniqueId();
        OpenMenuSession session = this.openMenus.remove(playerUniqueId);
        if (session != null) this.clearPageSession(player, session.menu());
        PaginatedMenuSessionRegistry.clear(playerUniqueId);
        MenuHolder holder = session == null
                ? this.findMenuHolder(player.getOpenInventory().getTopInventory(), playerUniqueId)
                : session.holder();
        if (holder != null) holder.close(player);
    }

    private void openNow(Player player, AbstractMenu menu) {
        MenuHolder holder = null;
        try {
            Menu contents = menu.buildMenu(player);
            holder = new MenuHolder(player.getUniqueId(), menu, contents);
            Inventory inventory = Bukkit.createInventory(holder, contents.getSize(), menu.resolveTitle(player));
            holder.bind(inventory);
            for (Map.Entry<Integer, AbstractButton> entry : contents.getButtons().entrySet()) {
                inventory.setItem(entry.getKey(), entry.getValue().buildDisplayItem(player));
            }
        } catch (RuntimeException | Error exception) {
            this.closeAfterFailure(holder, player, exception);
            throw exception;
        }

        OpenMenuSession previousSession = this.openMenus.get(player.getUniqueId());
        if (previousSession != null) {
            try {
                previousSession.holder().close(player);
            } catch (RuntimeException | Error exception) {
                this.openMenus.remove(player.getUniqueId(), previousSession);
                this.clearPageSession(player, previousSession.menu());
                this.closeAfterFailure(holder, player, exception);
                throw exception;
            }
            this.openMenus.remove(player.getUniqueId(), previousSession);
            if (previousSession.menu() != menu) this.clearPageSession(player, previousSession.menu());
        }

        if (menu instanceof AbstractPaginatedMenu<?> paginatedMenu) paginatedMenu.markOpened(player);
        OpenMenuSession session = new OpenMenuSession(menu, holder, this.tickCounter);
        this.openMenus.put(player.getUniqueId(), session);
        try {
            player.openInventory(holder.getInventory());
        } catch (RuntimeException | Error exception) {
            this.openMenus.remove(player.getUniqueId(), session);
            this.clearPageSession(player, menu);
            this.closeAfterFailure(holder, player, exception);
            throw exception;
        }
    }

    private void closeNow(Player player) {
        OpenMenuSession session = this.openMenus.remove(player.getUniqueId());
        try {
            if (session != null) {
                this.clearPageSession(player, session.menu());
                session.holder().close(player);
            }
        } finally {
            player.closeInventory();
        }
    }

    private void clearSessionsNow() {
        Throwable failure = null;
        for (HumanEntity viewer : Bukkit.getOnlinePlayers()) {
            if (!(viewer instanceof Player player)) continue;
            MenuHolder holder = this.findMenuHolder(
                    viewer.getOpenInventory().getTopInventory(), player.getUniqueId()
            );
            if (holder == null) continue;
            try {
                this.openMenus.remove(player.getUniqueId());
                this.clearPageSession(player, holder.menu());
                holder.close(player);
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            } finally {
                viewer.closeInventory();
            }
        }
        this.openMenus.clear();
        PaginatedMenuSessionRegistry.clearAll();
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private MenuInteraction resolveMenuInteraction(InventoryInteractEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return null;
        Inventory topInventory = event.getView().getTopInventory();
        MenuHolder holder = this.findMenuHolder(topInventory, player.getUniqueId());
        if (holder == null || holder.menu().allowsInventoryInteraction()) return null;
        return new MenuInteraction(player, topInventory, holder);
    }

    private void refreshIfCurrent(Player player, MenuHolder expectedHolder) {
        OpenMenuSession session = this.openMenus.get(player.getUniqueId());
        if (session != null && session.holder() == expectedHolder) {
            this.refreshInPlace(player, session, false);
        }
    }

    private void refreshInPlace(
            Player player,
            OpenMenuSession session,
            boolean allowIncrementalRefresh
    ) {
        AbstractMenu menu = session.menu();
        MenuHolder holder = session.holder();
        Inventory inventory = holder.getInventory();
        if (inventory == null) return;
        if (allowIncrementalRefresh && menu instanceof AutoRefreshMenu autoRefreshMenu
                && autoRefreshMenu.refreshInPlace(player, inventory, holder.contents())) {
            if (autoRefreshMenu.shouldForceInventorySync(player)) player.updateInventory();
            return;
        }
        Menu rebuilt = menu.buildMenu(player);
        if (inventory.getSize() != rebuilt.getSize()
                || !player.getOpenInventory().getTitle().equals(menu.resolveTitle(player))) {
            this.open(player, menu);
            return;
        }
        holder.replaceContents(rebuilt);
        for (int slot = 0; slot < rebuilt.getSize(); slot++) {
            AbstractButton button = rebuilt.getButtons().get(slot);
            ItemStack item = button == null ? null : button.buildDisplayItem(player);
            if (!Objects.equals(inventory.getItem(slot), item)) inventory.setItem(slot, item);
        }
        if (menu instanceof AutoRefreshMenu autoRefreshMenu
                && autoRefreshMenu.shouldForceInventorySync(player)) player.updateInventory();
    }

    private void clearPageSession(Player player, AbstractMenu menu) {
        if (menu instanceof AbstractPaginatedMenu<?> paginatedMenu) paginatedMenu.clearPageSession(player);
    }

    private void closeAfterFailure(MenuHolder holder, Player player, Throwable failure) {
        if (holder == null) return;
        try {
            holder.close(player);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private MenuHolder findMenuHolder(Inventory inventory, UUID viewerUniqueId) {
        if (inventory == null || viewerUniqueId == null) return null;
        if (!(inventory.getHolder() instanceof MenuHolder holder)) return null;
        return viewerUniqueId.equals(holder.viewerUniqueId()) ? holder : null;
    }

    private static final class OpenMenuSession {
        private final AbstractMenu menu;
        private final MenuHolder holder;
        private long lastRefreshTick;

        private OpenMenuSession(AbstractMenu menu, MenuHolder holder, long lastRefreshTick) {
            this.menu = menu;
            this.holder = holder;
            this.lastRefreshTick = lastRefreshTick;
        }

        private AbstractMenu menu() {
            return this.menu;
        }

        private MenuHolder holder() {
            return this.holder;
        }

        private long lastRefreshTick() {
            return this.lastRefreshTick;
        }

        private void lastRefreshTick(long tick) {
            this.lastRefreshTick = tick;
        }
    }

    private record MenuInteraction(Player player, Inventory topInventory, MenuHolder holder) {
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID viewerUniqueId;
        private final AbstractMenu menu;
        private Menu contents;
        private Inventory inventory;

        private MenuHolder(UUID viewerUniqueId, AbstractMenu menu, Menu contents) {
            this.viewerUniqueId = viewerUniqueId;
            this.menu = menu;
            this.contents = contents;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID viewerUniqueId() {
            return this.viewerUniqueId;
        }

        private AbstractMenu menu() {
            return this.menu;
        }

        private Menu contents() {
            return this.contents;
        }

        private void replaceContents(Menu contents) {
            this.contents.clearButtons();
            this.contents = contents;
        }

        private void close(Player player) {
            Inventory closingInventory = this.inventory;
            this.inventory = null;
            try {
                if (closingInventory != null) this.menu.onClose(player, closingInventory);
            } finally {
                this.contents.clearButtons();
            }
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
