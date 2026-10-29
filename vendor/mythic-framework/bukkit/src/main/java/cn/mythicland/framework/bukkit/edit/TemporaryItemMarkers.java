package cn.mythicland.framework.bukkit.edit;

import cn.mythicland.framework.bukkit.edit.v1_8_R3.StationaryItemMarker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

public final class TemporaryItemMarkers implements Listener, AutoCloseable {
    private final Map<Item, Location> positions = new IdentityHashMap<>();
    private final BiFunction<Location, ItemStack, Item> spawn;

    public TemporaryItemMarkers() {
        this(StationaryItemMarker::spawn);
    }

    public TemporaryItemMarkers(BiFunction<Location, ItemStack, Item> spawn) {
        this.spawn = spawn;
    }

    public Item add(Location position, Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(List.of(UUID.randomUUID().toString()));
        stack.setItemMeta(meta);
        Item item = this.spawn.apply(position, stack);
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setVelocity(new Vector());
        this.positions.put(item, position.clone());
        return item;
    }

    public boolean contains(Item item) {
        return this.positions.containsKey(item);
    }

    public void remove(Item item) {
        this.positions.remove(item);
        item.remove();
    }

    public void removeResidual(Location position, Material material, double radius) {
        for (Entity entity : position.getWorld().getNearbyEntities(position, radius, radius, radius)) {
            if (!(entity instanceof Item item)) continue;
            ItemStack stack = item.getItemStack();
            if (stack.getType() != material || !stack.hasItemMeta()) continue;
            ItemMeta meta = stack.getItemMeta();
            if (!meta.hasLore() || meta.getLore().size() != 1) continue;
            try {
                UUID.fromString(meta.getLore().get(0));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            remove(item);
        }
    }

    public void keepAlive() {
        for (Map.Entry<Item, Location> entry : this.positions.entrySet()) {
            Item item = entry.getKey();
            if (!item.isValid()) continue;
            Location position = entry.getValue();
            Location current = item.getLocation();
            if (!current.getWorld().equals(position.getWorld()) || current.distanceSquared(position) > 0)
                item.teleport(position);
            if (item.getVelocity().lengthSquared() > 0) item.setVelocity(new Vector());
            if (item.getTicksLived() > 20) item.setTicksLived(1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (contains(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (contains(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMerge(ItemMergeEvent event) {
        if (contains(event.getEntity()) || contains(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDespawn(ItemDespawnEvent event) {
        if (contains(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && contains(item)) event.setCancelled(true);
    }

    @Override
    public void close() {
        for (Item item : List.copyOf(this.positions.keySet())) remove(item);
    }

}
