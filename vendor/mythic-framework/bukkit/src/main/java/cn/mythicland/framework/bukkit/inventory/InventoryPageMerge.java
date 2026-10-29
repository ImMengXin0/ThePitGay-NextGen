package cn.mythicland.framework.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class InventoryPageMerge {
    private InventoryPageMerge() {
    }

    public static void mergeVisiblePage(
            ItemStack[] storage,
            ItemStack[] visiblePage,
            int page,
            int pageSize,
            int unlockedSlots,
            ItemStack lockedPlaceholder
    ) {
        if (storage == null || visiblePage == null) return;
        int offset = page * pageSize;
        if (offset < 0 || offset >= storage.length) return;
        int slots = Math.min(
                pageSize,
                Math.min(visiblePage.length, storage.length - offset)
        );
        int unlocked = cn.mythicland.framework.math.Clamps.clamp(unlockedSlots, 0, slots);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack itemStack = visiblePage[slot];
            if (slot >= unlocked && isLockedOrEmpty(itemStack, lockedPlaceholder)) continue;
            storage[offset + slot] = cloneOrNull(itemStack);
        }
    }

    private static boolean isLockedOrEmpty(ItemStack itemStack, ItemStack lockedPlaceholder) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return true;
        return lockedPlaceholder != null && itemStack.getType() == lockedPlaceholder.getType();
    }

    private static ItemStack cloneOrNull(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return null;
        return itemStack.clone();
    }
}
