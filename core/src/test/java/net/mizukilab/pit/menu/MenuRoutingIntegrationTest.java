package net.mizukilab.pit.menu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerOption;
import cn.charlotte.pit.perk.PerkFactory;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.handlers.TickHandler;
import net.mizukilab.pit.menu.option.PlayerOptionMenu;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.menu.trade.button.TargetConfirmButton;
import net.mizukilab.pit.menu.viewer.StatusViewerMenu;
import net.mizukilab.pit.menu.viewer.button.EnchantingViewerMenu;
import net.mizukilab.pit.menu.viewer.button.PitStatusButton;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.ButtonListener;
import net.mizukilab.pit.util.menu.Menu;
import net.mizukilab.pit.util.menu.MenuUpdateTask;
import net.mizukilab.pit.util.menu.pagination.PageButton;
import net.mizukilab.pit.util.menu.pagination.PaginatedMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real CraftBukkit inventory wrappers, with player/container transport and item conversion isolated. */
public class MenuRoutingIntegrationTest extends BukkitDataTestSupport {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<CraftItemStack> itemCopies;
    private final ButtonListener listener = new ButtonListener();
    private final Map<UUID, Surface> surfaces = new HashMap<>();

    @Before
    public void setupRouting() {
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        itemCopies = mockStatic(CraftItemStack.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                .thenAnswer(call -> new CraftInventoryCustom(call.getArgument(0), (int) call.getArgument(1), call.getArgument(2)));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> {
            Surface surface = surfaces.get(call.getArgument(0));
            return surface == null ? null : surface.player;
        });
    }

    @After
    public void teardownRouting() {
        for (Surface surface : surfaces.values()) {
            Menu menu = Menu.find(surface.player);
            if (menu != null) {
                menu.setClosedByMenu(true);
                listener.onInventoryClose(new InventoryCloseEvent(surface.view));
            }
            Menu.currentlyOpenedMenus.remove(surface.player.getUniqueId());
            TradeManager.trades.remove(surface.player.getUniqueId());
        }
        itemCopies.close();
        pit.close();
        bukkit.close();
    }

    private class Surface {
        final Player player = mock(Player.class);
        final PlayerInventory bottom = mock(PlayerInventory.class);
        final Inventory crafting = mock(Inventory.class);
        InventoryView view;
        Inventory requested;

        Surface() {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getDisplayName()).thenReturn("Tester");
            when(player.isOnline()).thenReturn(true);
            when(player.getInventory()).thenReturn(bottom);
            when(bottom.getSize()).thenReturn(36);
            when(crafting.getType()).thenReturn(InventoryType.CRAFTING);
            view = viewOf(crafting);
            when(player.getOpenInventory()).thenAnswer(call -> view);
            doAnswer(call -> {
                listener.onInventoryClose(new InventoryCloseEvent(view));
                view = viewOf(crafting);
                return null;
            }).when(player).closeInventory();
            when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
                requested = call.getArgument(0);
                listener.onInventoryClose(new InventoryCloseEvent(view));
                // ContainerChest.getBukkitView() creates a new wrapper for the same IInventory.
                view = viewOf(new CraftInventory(((CraftInventory) requested).getInventory()));
                return view;
            });
            surfaces.put(player.getUniqueId(), this);
        }

        InventoryView viewOf(Inventory top) {
            return new InventoryView() {
                @Override public Inventory getTopInventory() { return top; }
                @Override public Inventory getBottomInventory() { return bottom; }
                @Override public HumanEntity getPlayer() { return player; }
                @Override public InventoryType getType() { return top.getType(); }
            };
        }

        InventoryClickEvent click(int raw, ClickType type) {
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    raw, type, InventoryAction.PICKUP_ALL);
            listener.onButtonPress(event);
            return event;
        }
    }

    private Button button() {
        Button button = mock(Button.class);
        when(button.shouldCancel(any(), anyInt(), any())).thenReturn(true);
        return button;
    }

    private Menu menu(Map<Integer, Button> buttons) {
        return spy(new Menu() {
            @Override public String getTitle(Player player) { return "Routing"; }
            @Override public int getSize() { return 27; }
            @Override public Map<Integer, Button> getButtons(Player player) { return new HashMap<>(buttons); }
        });
    }

    @Test
    public void differentWrappersDispatchClicksRefreshInPlaceAndCloseTheCorrectMenu() throws Exception {
        Surface surface = new Surface();
        Button button = button();
        Menu menu = menu(Map.of(10, button));
        menu.setAutoUpdate(true);
        menu.openMenu(surface.player);
        assertNotSame(surface.requested, surface.view.getTopInventory());
        assertEquals(surface.requested, surface.view.getTopInventory());
        assertSame(menu, Menu.find(surface.player, surface.view.getTopInventory()));
        assertTrue(surface.click(10, ClickType.LEFT).isCancelled());
        verify(button).clicked(surface.player, 10, ClickType.LEFT, -1, null);
        new MenuUpdateTask().run();
        var tickMenus = TickHandler.class.getDeclaredMethod("tickMenus");
        tickMenus.setAccessible(true);
        tickMenus.invoke(new TickHandler());
        verify(surface.player, times(1)).openInventory(any(Inventory.class));
        verify(menu, never()).onClose(surface.player);
        surface.player.closeInventory();
        assertNull(Menu.find(surface.player));
        verify(menu, times(1)).onClose(surface.player);
    }

    @Test
    public void settingsButtonChangesStoredOptionThroughTheActualClickListener() {
        Surface surface = new Surface();
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            PlayerProfile profile = mock(PlayerProfile.class);
            PlayerOption option = new PlayerOption();
            when(profile.isLoaded()).thenReturn(true);
            when(profile.getPlayerOption()).thenReturn(option);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(surface.player.getUniqueId())).thenReturn(profile);
            Button setting = new PlayerOptionMenu().getButtons(surface.player).get(22);
            Menu menu = menu(Map.of(22, setting));
            menu.openMenu(surface.player);
            surface.click(22, ClickType.LEFT);
            assertTrue(option.isEnchantingVisibility());
            when(profile.isSupporter()).thenReturn(true);
            surface.click(22, ClickType.LEFT);
            assertFalse(option.isEnchantingVisibility());
            surface.click(22, ClickType.RIGHT);
            assertTrue(option.isEnchantingVisibility());
            verify(surface.player, times(1)).openInventory(any(Inventory.class));
        }
    }

    @Test
    public void offlineProfileEntryOpensReadOnlyEnchantingMenuAndReturnsToProfile() {
        Surface surface = new Surface();
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class);
             var statusLabels = mockConstruction(PitStatusButton.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
            PlayerProfile saved = new PlayerProfile(UUID.randomUUID(), "OfflineTarget");
            assertFalse(saved.isLoaded());
            new StatusViewerMenu(saved).openMenu(surface.player);
            assertTrue(surface.click(33, ClickType.LEFT).isCancelled());
            Menu current = Menu.find(surface.player);
            assertTrue(current instanceof EnchantingViewerMenu);
            assertSame(current, Menu.find(surface.player, surface.view.getTopInventory()));
            assertTrue(current.getButtons(surface.player).containsKey(11));
            assertTrue(current.isReadOnly());
            assertTrue(surface.click(11, ClickType.SHIFT_LEFT).isCancelled());
            surface.click(22, ClickType.LEFT);
            assertTrue(Menu.find(surface.player) instanceof StatusViewerMenu);
            verify(surface.player, times(3)).openInventory(any(Inventory.class));
            profiles.verify(() -> PlayerProfile.loadPlayerProfileByUuid(any(UUID.class)), never());
            profiles.verify(() -> PlayerProfile.loadPlayerProfileByName(anyString()), never());
        }
    }

    @Test
    public void navigationDoesNotReopenOldMenuOrConfuseSameTitleContainers() {
        Surface surface = new Surface();
        Button next = button(), action = button();
        Menu first = menu(Map.of(10, next)), second = menu(Map.of(10, action));
        doAnswer(call -> { second.openMenu(surface.player); return null; })
                .when(next).clicked(any(), anyInt(), any(), anyInt(), any());
        when(next.shouldUpdate(any(), anyInt(), any())).thenReturn(true);
        first.openMenu(surface.player);
        InventoryView oldView = surface.view;
        surface.click(10, ClickType.LEFT);
        assertSame(second, Menu.find(surface.player, surface.view.getTopInventory()));
        assertNotEquals(oldView.getTopInventory(), surface.view.getTopInventory());
        listener.onInventoryClose(new InventoryCloseEvent(oldView));
        assertSame(second, Menu.find(surface.player));
        surface.click(10, ClickType.LEFT);
        verify(action).clicked(surface.player, 10, ClickType.LEFT, -1, null);
        verify(first, times(1)).onClose(surface.player);
        verify(surface.player, times(2)).openInventory(any(Inventory.class));
    }

    @Test
    public void externalInventoryWithSameTitleAndSizeNeverReceivesCoreButtonsOrRefresh() {
        Surface surface = new Surface();
        Button button = button();
        Menu menu = menu(Map.of(10, button));
        menu.setAutoUpdate(true);
        menu.openMenu(surface.player);
        InventoryView oldView = surface.view;
        // Even a stale menu index must not claim a different container with the same owner/title/size.
        surface.view = surface.viewOf(new CraftInventoryCustom(surface.player, 27, "Routing"));
        assertNull(Menu.find(surface.player, surface.view.getTopInventory()));
        assertFalse(surface.click(10, ClickType.LEFT).isCancelled());
        listener.onInventoryClose(new InventoryCloseEvent(surface.view));
        new MenuUpdateTask().run();
        verify(button, never()).clicked(any(), anyInt(), any(), anyInt(), any());
        verify(menu, never()).onClose(any());
        verify(surface.player, times(1)).openInventory(any(Inventory.class));
        listener.onInventoryClose(new InventoryCloseEvent(oldView));
        assertNull(Menu.find(surface.player));
    }

    @Test
    public void bottomInventoryReachesInputHandlerWithoutDispatchingTopButtonAtSameSlot() {
        Surface surface = new Surface();
        Button button = button();
        Menu menu = menu(Map.of(0, button));
        menu.openMenu(surface.player);
        InventoryClickEvent event = surface.click(27, ClickType.LEFT);
        assertTrue(event.isCancelled());
        verify(menu).onClickEvent(event);
        verify(button, never()).clicked(any(), anyInt(), any(), anyInt(), any());
    }

    @Test
    public void readOnlyMenuAcceptsNavigationButBlocksHotbarShiftAndDragWithNewWrapper() {
        Surface surface = new Surface();
        Button button = button();
        Menu menu = menu(Map.of(10, button));
        doReturn(true).when(menu).isReadOnly();
        menu.openMenu(surface.player);
        assertTrue(surface.click(10, ClickType.NUMBER_KEY).isCancelled());
        assertTrue(surface.click(10, ClickType.SHIFT_LEFT).isCancelled());
        assertTrue(surface.click(27, ClickType.LEFT).isCancelled());
        verify(button, never()).clicked(any(), anyInt(), any(), anyInt(), any());
        assertTrue(surface.click(10, ClickType.LEFT).isCancelled());
        verify(button).clicked(surface.player, 10, ClickType.LEFT, -1, null);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(surface.player);
        when(drag.getView()).thenReturn(surface.view);
        listener.onInventoryDrag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void actualPageButtonChangesPageAndDispatchesTheNewPageItem() {
        Surface surface = new Surface();
        Map<Integer, Button> all = new HashMap<>();
        for (int i = 0; i < 19; i++) all.put(i, button());
        PaginatedMenu menu = spy(new PaginatedMenu() {
            @Override public String getPrePaginatedTitle(Player player) { return "Pages"; }
            @Override public Map<Integer, Button> getAllPagesButtons(Player player) { return all; }
        });
        doAnswer(call -> {
            Map<Integer, Button> rendered = (Map<Integer, Button>) call.callRealMethod();
            for (Map.Entry<Integer, Button> entry : rendered.entrySet()) {
                if (entry.getValue() instanceof PageButton) {
                    Button navigation = spy(entry.getValue());
                    doReturn(null).when(navigation).getButtonItem(any());
                    entry.setValue(navigation);
                }
            }
            return rendered;
        }).when(menu).getButtons(any(Player.class));
        menu.openMenu(surface.player);
        surface.click(8, ClickType.LEFT);
        assertEquals(2, menu.getPage());
        assertSame(menu, Menu.find(surface.player, surface.view.getTopInventory()));
        surface.click(9, ClickType.LEFT);
        verify(all.get(18)).clicked(surface.player, 9, ClickType.LEFT, -1, null);
    }

    @Test
    public void addingTradeItemRefreshesBothPlayersWithoutCancellingTheTrade() {
        Surface first = new Surface(), second = new Surface();
        List<Player> cancelled = new ArrayList<>();
        // Observe cancellation without invoking database/chat/item-return services in this routing test.
        try (MockedStatic<ItemUtil> items = mockStatic(ItemUtil.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             var targetLabels = mockConstruction(TargetConfirmButton.class)) {
            PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
            when(profile.isLoaded()).thenReturn(true);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(any(UUID.class))).thenReturn(profile);
            TradeManager trade = new TradeManager(first.player, second.player) {
                @Override public void onPlayerCancel(Player player) { cancelled.add(player); }
            };
            trade.openMenu(first.player);
            trade.openMenu(second.player);
            Menu firstMenu = Menu.find(first.player), secondMenu = Menu.find(second.player);
            assertSame(firstMenu, Menu.find(first.player, first.view.getTopInventory()));
            assertSame(secondMenu, Menu.find(second.player, second.view.getTopInventory()));
            assertNotNull(firstMenu);
            assertNotNull(secondMenu);
            ItemStack offered = mock(ItemStack.class);
            when(offered.getType()).thenReturn(Material.DIAMOND_SWORD);
            when(offered.getAmount()).thenReturn(1);
            when(offered.clone()).thenReturn(offered);
            when(first.bottom.getItem(9)).thenReturn(offered);
            items.when(() -> ItemUtil.canTrade(offered)).thenReturn(true);
            assertTrue(first.click(first.view.getTopInventory().getSize(), ClickType.LEFT).isCancelled());
            assertEquals(1, trade.getAItems().size());
            assertSame(offered, trade.getAItems().get(0));
            assertSame(firstMenu, Menu.find(first.player));
            assertSame(secondMenu, Menu.find(second.player));
            assertTrue(cancelled.isEmpty());
            verify(first.player, times(1)).openInventory(any(Inventory.class));
            verify(second.player, times(1)).openInventory(any(Inventory.class));
            first.player.closeInventory();
            assertEquals(List.of(first.player), cancelled);
        }
    }

    @Test public void blankMenuSlotsCannotAcceptCursorItemsOrDraggedItems() {
        Surface surface = new Surface(); Menu menu = menu(Map.of(10, button())); menu.openMenu(surface.player);
        assertTrue(surface.click(0, ClickType.LEFT).isCancelled());
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(surface.player); when(drag.getView()).thenReturn(surface.view);
        when(drag.getRawSlots()).thenReturn(java.util.Set.of(0, 30));
        listener.onInventoryDrag(drag); verify(drag).setCancelled(true);
    }
    @Test public void shiftTakeUsesVanillaTransferAndNeverAddsACopyWhileLeavingSource() {
        Surface surface = new Surface(); Button take = mock(Button.class);
        Menu menu = menu(Map.of(10, take)); menu.openMenu(surface.player);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(surface.player); when(event.getView()).thenReturn(surface.view);
        when(event.getClickedInventory()).thenReturn(surface.view.getTopInventory());
        when(event.getSlot()).thenReturn(10); when(event.getRawSlot()).thenReturn(10);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT); when(event.getCurrentItem()).thenReturn(mock(ItemStack.class));
        listener.onButtonPress(event);
        verify(surface.bottom, never()).addItem(any(ItemStack[].class));
    }
    @Test public void menuCancellationCannotBeUndoneByAMovableButton() {
        Surface surface = new Surface(); Button take = mock(Button.class); Menu menu = menu(Map.of(10, take));
        doAnswer(call -> { ((InventoryClickEvent)call.getArgument(0)).setCancelled(true); return null; }).when(menu).onClickEvent(any());
        menu.openMenu(surface.player); assertTrue(surface.click(10,ClickType.LEFT).isCancelled());
    }
    @Test public void replacingMenuInInputHandlerDoesNotDispatchOldButton() {
        Surface surface = new Surface(); Button old = button(); Menu menu = menu(Map.of(10,old)); Menu next = menu(Map.of());
        doAnswer(call -> { next.openMenu(surface.player); return null; }).when(menu).onClickEvent(any());
        menu.openMenu(surface.player); surface.click(10,ClickType.LEFT);
        assertSame(next,Menu.find(surface.player)); verify(old,never()).clicked(any(),anyInt(),any(),anyInt(),any());
    }
    @Test public void explicitlyEditableSlotsStillAcceptDragAndBottomOnlyDragIsUnaffected() {
        Surface surface = new Surface(); Menu menu = menu(Map.of(10, mock(Button.class))); menu.openMenu(surface.player);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(surface.player); when(drag.getView()).thenReturn(surface.view);
        when(drag.getRawSlots()).thenReturn(java.util.Set.of(10,30)); when(drag.getType()).thenReturn(DragType.EVEN);
        listener.onInventoryDrag(drag); verify(drag,never()).setCancelled(true);
    }
}
