package cn.mythicland.framework.bukkit.item;

import cn.mythicland.framework.menu.MenuManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PlayerItemHoldings {
    private final MenuManager menuManager;

    public PlayerItemHoldings(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public int count(Player player, Predicate<ItemStack> matches) {
        ItemStack cursor = player.getItemOnCursor();
        return this.count(player.getInventory(), matches)
                + (this.matches(cursor, matches) ? cursor.getAmount() : 0)
                + this.count(this.topInventory(player), matches);
    }

    public boolean updateFirst(Player player, Predicate<ItemStack> matches, Consumer<ItemStack> update) {
        if (this.updateFirst(player.getInventory(), matches, update)) return true;
        ItemStack cursor = player.getItemOnCursor();
        if (this.matches(cursor, matches)) {
            ItemStack updated = cursor.clone();
            update.accept(updated);
            player.setItemOnCursor(updated);
            return true;
        }
        return this.updateFirst(this.topInventory(player), matches, update);
    }

    private Inventory topInventory(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory == player.getInventory() || this.menuManager.isManagedInventory(inventory)) return null;
        return inventory;
    }

    private int count(Inventory inventory, Predicate<ItemStack> matches) {
        if (inventory == null) return 0;
        int total = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.matches(item, matches)) total += item.getAmount();
        }
        return total;
    }

    private boolean updateFirst(Inventory inventory, Predicate<ItemStack> matches, Consumer<ItemStack> update) {
        if (inventory == null) return false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!this.matches(item, matches)) continue;
            ItemStack updated = item.clone();
            update.accept(updated);
            inventory.setItem(slot, updated);
            return true;
        }
        return false;
    }

    private boolean matches(ItemStack item, Predicate<ItemStack> matches) {
        return item != null && item.getType() != Material.AIR && matches.test(item);
    }
}
