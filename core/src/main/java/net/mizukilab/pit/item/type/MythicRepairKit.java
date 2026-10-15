package net.mizukilab.pit.item.type;

import net.mizukilab.pit.item.AbstractPitItem;
import net.mizukilab.pit.item.IMythicItem;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Creator Misoryan
 * @Date 2021/5/29 17:30
 */

public class MythicRepairKit extends AbstractPitItem implements Listener {
    private static final Pattern LIFE_LINE = Pattern.compile(
            "^(?:§.)*生命[:：]\\s*(?:§.)*\\d+(?:§.)*\\s*/\\s*(?:§.)*\\d+");

    public static ItemStack toItemStack0() {
        return new ItemBuilder(Material.SHEARS)
                .name("&d神话工匠包")
                .lore(
                        "&e特殊物品",
                        "&c无法用于交易",
                        "&7手持要修复的神话物品,在背包内右键点击神话工匠包,",
                        "&7即可恢复手上的神话物品全部生命.",
                        "&7也可用鼠标拿起工匠包,右键背包内的神话物品.",
                        "&7(每次修复消耗一个,满生命时不消耗)"
                )
                .internalName("mythic_repair_kit")
                .canSaveToEnderChest(true)
                .canTrade(false)
                .shiny()
                .buildWithUnbreakable();
    }

    @Override
    public String getInternalName() {
        return "mythic_repair_kit";
    }

    @Override
    public String getItemDisplayName() {
        return "剪刀";
    }

    @Override
    public Material getItemDisplayMaterial() {
        return Material.SHEARS;
    }

    @Override
    public ItemStack toItemStack() {
        return toItemStack0();
    }

    @Override
    public void loadFromItemStack(ItemStack item) {

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled() || event.getClick() != ClickType.RIGHT
                || !(event.getWhoClicked() instanceof Player player)
                || !(event.getClickedInventory() instanceof PlayerInventory)
                || !event.getClickedInventory().equals(player.getInventory())
                || event.getView().getType() != InventoryType.CRAFTING
                || event.getSlot() < 0 || event.getSlot() >= 36) return;

        // Only the owner's ordinary backpack is a repair surface, never a menu preview.
        ItemStack clicked = player.getInventory().getItem(event.getSlot());
        ItemStack cursor = player.getItemOnCursor();
        boolean cursorKit = isKit(cursor);
        boolean clickedKit = isKit(clicked);
        if (cursorKit == clickedKit) return;
        boolean cursorTarget = clickedKit && !isEmpty(cursor);
        ItemStack target = cursorKit ? clicked : cursorTarget ? cursor : player.getItemInHand();
        if (cursorKit && isEmpty(target)) return; // Allow placing the kit into an empty slot.
        event.setCancelled(true);

        NBTTagCompound extra = ItemUtil.getExtra(target);
        String internal = ItemUtil.getInternalName0(extra);
        if (isEmpty(target) || target.getAmount() != 1 || internal == null
                || !Utils.needToCheck(internal) || "lucky_chestplate".equals(internal)
                || !extra.hasKeyOfType("maxLive", 99) || !extra.hasKeyOfType("live", 99)
                || extra.getInt("maxLive") <= 0 || extra.getInt("live") < 0) {
            player.sendMessage(CC.translate("&c请在自己的背包中选择一件可修复的神话物品，幸运之甲无法修复。"));
            return;
        }
        int maxLive = extra.getInt("maxLive");
        if (extra.getInt("live") >= maxLive) {
            player.sendMessage(CC.translate("&e这件神话物品的生命已满，无需修复。"));
            return;
        }

        // Edit a detached NBT copy: rebuilding an item drops extension tags and unknown enchants.
        ItemStack repaired = repairCopy(target, maxLive);
        ItemStack remaining = consumeOne(cursorKit ? cursor : clicked);
        if (cursorKit) {
            event.setCurrentItem(repaired);
            player.setItemOnCursor(remaining);
        } else {
            if (cursorTarget) player.setItemOnCursor(repaired);
            else player.setItemInHand(repaired);
            event.setCurrentItem(remaining);
        }
        IMythicItem.clearCache(target);
        player.updateInventory();
        player.sendMessage(CC.translate("&d&l神话工匠包! &7你的神话物品现已完全修复。"));
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private static boolean isKit(ItemStack stack) {
        return !isEmpty(stack) && "mythic_repair_kit".equals(ItemUtil.getInternalName(stack));
    }

    private static ItemStack consumeOne(ItemStack stack) {
        if (stack.getAmount() == 1) return null;
        ItemStack remaining = stack.clone();
        remaining.setAmount(stack.getAmount() - 1);
        return remaining;
    }

    private static ItemStack repairCopy(ItemStack stack, int maxLive) {
        var nms = CraftItemStack.asNMSCopy(stack);
        nms.setTag((NBTTagCompound) nms.getTag().clone());
        NBTTagCompound tag = nms.getTag();
        tag.getCompound("extra").setInt("live", maxLive);
        NBTTagCompound display = tag.getCompound("display");
        NBTTagList oldLore = display.getList("Lore", 8), lore = new NBTTagList();
        String life = "§7生命: §a" + maxLive + "§7/" + maxLive;
        boolean replaced = false;
        for (int i = 0; i < oldLore.size(); i++) {
            String line = oldLore.getString(i);
            Matcher match = LIFE_LINE.matcher(line);
            if (!replaced && match.find()) {
                line = life + line.substring(match.end());
                replaced = true;
            }
            lore.add(new NBTTagString(line));
        }
        if (!replaced) lore.add(new NBTTagString(life));
        display.set("Lore", lore);
        tag.set("display", display);
        return CraftItemStack.asBukkitCopy(nms);
    }
}
