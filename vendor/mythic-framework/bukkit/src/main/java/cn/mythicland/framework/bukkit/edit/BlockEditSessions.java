package cn.mythicland.framework.bukkit.edit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public final class BlockEditSessions implements Listener, AutoCloseable {
    private final Map<UUID, Object> owners = new HashMap<>();
    private final Map<UUID, Route> routes = new HashMap<>();
    private final Map<UUID, Location> interactions = new HashMap<>();
    private final Set<BlockBreakEvent> breaks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final TemporaryItemMarkers markers;

    public BlockEditSessions() {
        this(new TemporaryItemMarkers());
    }

    public BlockEditSessions(TemporaryItemMarkers markers) {
        this.markers = markers;
    }

    public static Location lowerRightCorner(Location block, float yaw) {
        int facing = Math.floorMod((int) Math.floor((yaw + 45) / 90), 4);
        return new Location(block.getWorld(), block.getBlockX() + (facing == 1 || facing == 2 ? 1 : 0),
                block.getBlockY(), block.getBlockZ() + (facing == 2 || facing == 3 ? 1 : 0));
    }

    public TemporaryItemMarkers markers() {
        return this.markers;
    }

    public boolean claim(UUID player, Object owner) {
        return this.owners.putIfAbsent(player, owner) == null;
    }

    public void release(UUID player, Object owner) {
        if (!this.owners.remove(player, owner)) return;
        Route route = this.routes.remove(player);
        this.interactions.remove(player);
        if (route != null) route.cleanup.run();
    }

    public <T> Points<T> points() {
        return new Points<>(this.markers);
    }

    public void route(
            UUID player,
            Object owner,
            Predicate<Player> allowed,
            BiConsumer<Player, Location> select,
            Consumer<Player> undo,
            boolean clearOnWorldChange,
            Runnable cleanup
    ) {
        if (this.owners.get(player) != owner) throw new IllegalStateException("Block edit session is not owned by caller");
        this.routes.put(player, new Route(allowed, select, undo, clearOnWorldChange, cleanup));
    }

    public boolean handle(BlockBreakEvent event) {
        if (this.breaks.contains(event)) return true;
        Player player = event.getPlayer();
        Route route = this.routes.get(player.getUniqueId());
        if (route == null || !route.allowed.test(player)) return false;
        event.setCancelled(true);
        this.breaks.add(event);
        Location block = event.getBlock().getLocation();
        if (!block.equals(this.interactions.remove(player.getUniqueId()))) route.select.accept(player, block);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Route route = this.routes.get(player.getUniqueId());
        if (route == null || !route.allowed.test(player)) return;
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Location block = event.getClickedBlock().getLocation();
            this.interactions.put(player.getUniqueId(), block);
            route.select.accept(player, block);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            route.undo.accept(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        handle(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        release(player, this.owners.get(player));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        Route route = this.routes.get(player);
        if (route != null && route.clearOnWorldChange) release(player, this.owners.get(player));
    }

    public void tick() {
        this.breaks.clear();
        this.interactions.clear();
        this.markers.keepAlive();
    }

    @Override
    public void close() {
        for (UUID player : List.copyOf(this.owners.keySet())) release(player, this.owners.get(player));
    }

    private record Route(
            Predicate<Player> allowed,
            BiConsumer<Player, Location> select,
            Consumer<Player> undo,
            boolean clearOnWorldChange,
            Runnable cleanup
    ) {
    }

    public static final class Points<T> implements AutoCloseable {
        private final TemporaryItemMarkers markers;
        private final it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<Object, Entry<T>> points =
                new it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap<>();

        private Points(TemporaryItemMarkers markers) {
            this.markers = markers;
        }

        public int size() {
            return this.points.size();
        }

        public boolean isEmpty() {
            return this.points.isEmpty();
        }

        public T get(Object key) {
            Entry<T> entry = this.points.get(key);
            return entry == null ? null : entry.value;
        }

        public Object lastKey() {
            return isEmpty() ? null : this.points.lastKey();
        }

        public List<T> values() {
            List<T> values = new ArrayList<>(this.points.size());
            for (Entry<T> point : this.points.values()) values.add(point.value);
            return values;
        }

        public void put(Object key, T value, Location position, Material material) {
            remove(key);
            this.points.put(key, new Entry<>(value, this.markers.add(position, material)));
        }

        public T remove(Object key) {
            Entry<T> removed = this.points.remove(key);
            if (removed == null) return null;
            this.markers.remove(removed.item);
            return removed.value;
        }

        public T undo() {
            if (isEmpty()) return null;
            return remove(this.points.lastKey());
        }

        @Override
        public void close() {
            for (Entry<T> entry : this.points.values()) this.markers.remove(entry.item);
            this.points.clear();
        }

        private record Entry<T>(T value, Item item) {
        }
    }
}
