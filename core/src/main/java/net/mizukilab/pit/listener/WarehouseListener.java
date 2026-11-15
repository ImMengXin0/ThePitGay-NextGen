package net.mizukilab.pit.listener;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.mizukilab.pit.parm.AutoRegister;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Click-to-transfer storage with server-side container identity, ownership and quantity checks. */
@AutoRegister
public class WarehouseListener implements Listener {
    private boolean isWarehouse(Inventory inventory) {
        return inventory.getHolder() instanceof PlayerWarehouse.WarehouseHolder;
    }

    private boolean owns(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof PlayerWarehouse.WarehouseHolder holder)) return false;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        return profile.isLoaded() && profile.getWarehouse() == holder.getOwner()
                && inventory.equals(holder.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isWarehouse(event.getInventory()) && event.getPlayer() instanceof Player player
                && !owns(player, event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        Inventory warehouse = event.getView().getTopInventory();
        if (!isWarehouse(warehouse)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !owns(player, warehouse)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) return;
        if (event.getCursor() != null && !event.getCursor().getType().isAir()) return;
        Inventory source = event.getClickedInventory();
        if (source == null) return;
        int slot = event.getSlot();
        boolean deposit = source.equals(player.getInventory()) && slot >= 0 && slot < 36;
        if (!deposit && !source.equals(warehouse)) return;
        ItemStack item = source.getItem(slot);
        if (item == null || item.getType().isAir() || PlayerWarehouse.isBarrierPlaceholder(item)) return;
        if (deposit && !ItemUtil.canSaveEnderChest(item)) {
            player.sendMessage("§c该物品不能寄存!");
            return;
        }
        int moved = transfer(source, slot, deposit ? warehouse : player.getInventory(), deposit);
        if (moved == 0) player.sendMessage(deposit ? "§c寄存箱已满!" : "§c背包已满，无法取出!");
        else player.playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1.0f, 1.0f);
    }

    /** Fill compatible stacks, then free slots, retaining any remainder at its source. */
    static int transfer(Inventory source, int slot, Inventory destination, boolean deposit) {
        ItemStack original = source.getItem(slot);
        ItemStack remaining = original.clone();
        int size = deposit ? destination.getSize() : 36;
        for (int pass = 0; pass < 2 && remaining.getAmount() > 0; pass++) {
            for (int i = 0; i < size && remaining.getAmount() > 0; i++) {
                ItemStack existing = destination.getItem(i);
                boolean empty = existing == null || existing.getType().isAir()
                        || deposit && PlayerWarehouse.isBarrierPlaceholder(existing);
                if (pass == 0 && (empty || !existing.isSimilar(remaining))) continue;
                if (pass == 1 && !empty) continue;
                int capacity = Math.min(remaining.getMaxStackSize(), destination.getMaxStackSize());
                int amount = Math.min(remaining.getAmount(), empty ? capacity : Math.max(0, capacity - existing.getAmount()));
                if (amount == 0) continue;
                ItemStack updated = empty ? remaining.clone() : existing.clone();
                updated.setAmount((empty ? 0 : existing.getAmount()) + amount);
                destination.setItem(i, updated);
                remaining.setAmount(remaining.getAmount() - amount);
            }
        }
        int moved = original.getAmount() - remaining.getAmount();
        if (moved > 0) source.setItem(slot, remaining.getAmount() > 0 ? remaining
                : deposit ? null : PlayerWarehouse.createBarrierPlaceholder());
        return moved;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && owns(player, event.getInventory())) {
            PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).save(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isWarehouse(event.getView().getTopInventory())) event.setCancelled(true);
    }
}
