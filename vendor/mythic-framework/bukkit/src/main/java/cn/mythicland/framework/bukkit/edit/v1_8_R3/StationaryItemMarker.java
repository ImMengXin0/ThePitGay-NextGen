package cn.mythicland.framework.bukkit.edit.v1_8_R3;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Namespace retained for source compatibility with existing game code. */
public final class StationaryItemMarker {
    private StationaryItemMarker() {}
    public static Item spawn(Location position, ItemStack stack) {
        return java.util.Objects.requireNonNull(position.getWorld()).dropItem(position, stack.clone(), item -> {
            item.setVelocity(new Vector());
            item.setGravity(false);
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setWillAge(false);
            item.setPersistent(false);
        });
    }
}
