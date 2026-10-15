package net.mizukilab.pit.trade;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/** Plans storage changes on copies before either side of a trade is modified. */
public final class TradeInventory {
    private TradeInventory() {}

    public static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] result = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            result[i] = contents[i] == null ? null : contents[i].clone();
        }
        return result;
    }

    public static ItemStack[] withItems(PlayerInventory inventory, List<ItemStack> items) {
        ItemStack[] result = copy(inventory.getContents());
        for (ItemStack item : items) {
            if (!insert(result, item, inventory.getMaxStackSize())) return null;
        }
        return result;
    }

    private static boolean insert(ItemStack[] contents, ItemStack item, int inventoryMax) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return false;
        int remaining = item.getAmount();
        int max = Math.max(1, Math.min(inventoryMax, item.getMaxStackSize()));
        // Armor slots are never storage, including on servers exposing 40 slots here.
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            ItemStack existing = contents[i];
            if (existing != null && existing.isSimilar(item) && existing.getAmount() < max) {
                int put = Math.min(remaining, max - existing.getAmount());
                existing.setAmount(existing.getAmount() + put);
                remaining -= put;
                if (remaining == 0) return true;
            }
        }
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            if (contents[i] == null || contents[i].getType() == Material.AIR) {
                ItemStack placed = item.clone();
                placed.setAmount(Math.min(remaining, max));
                contents[i] = placed;
                remaining -= placed.getAmount();
                if (remaining == 0) return true;
            }
        }
        return remaining == 0;
    }

    public static void snapshot(Player player, PlayerProfile profile) {
        profile.setInventory(InventoryUtil.playerInventoryFromPlayer(player));
    }

    /** Unfittable refunds stay in the same persisted player record; nothing is dropped. */
    public static void refund(Player player, PlayerProfile profile, List<ItemStack> items) {
        List<String> pending = new ArrayList<>(profile.getTradeReturns());
        for (ItemStack item : items) pending.add(InventoryUtil.serializeItemStack(item));
        profile.setTradeReturns(pending);
        claimReturns(player, profile);
    }

    public static void claimReturns(Player player, PlayerProfile profile) {
        if (profile.isTempInvUsing()) return;
        List<String> pending = new ArrayList<>(profile.getTradeReturns());
        ItemStack[] contents = copy(player.getInventory().getContents());
        boolean changed = false;
        for (var iterator = pending.iterator(); iterator.hasNext();) {
            String encoded = iterator.next();
            ItemStack item;
            try { item = InventoryUtil.deserializeItemStack(encoded); }
            catch (RuntimeException invalid) { continue; }
            ItemStack[] attempt = copy(contents);
            if (insert(attempt, item, player.getInventory().getMaxStackSize())) {
                contents = attempt;
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(contents);
            profile.setTradeReturns(pending);
            snapshot(player, profile);
        }
        if (!pending.isEmpty()) {
            player.sendMessage("§e交易退还物品已保留，请腾出背包空间后输入 /tradeclaim 领取。");
        }
    }
}
