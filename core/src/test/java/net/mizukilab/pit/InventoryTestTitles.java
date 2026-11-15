package net.mizukilab.pit;

import org.bukkit.craftbukkit.inventory.CraftInventory;

/** MinecraftInventory is package private, but its title is the one sent to the client. */
public final class InventoryTestTitles {
    private InventoryTestTitles() { }
    public static String title(CraftInventory inventory) {
        try {
            var method = inventory.getInventory().getClass().getMethod("getTitle");
            method.setAccessible(true);
            return (String) method.invoke(inventory.getInventory());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
