package net.mizukilab.pit.menu.viewer.button;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class EnchantingViewerButton extends Button {
    private final EnchantingViewerMenu menu;

    public EnchantingViewerButton(PlayerProfile profile) {
        menu = new EnchantingViewerMenu(profile);
    }

    @Override
    public ItemStack getButtonItem(Player player) {
        String unavailable = menu.getUnavailableReason(player);
        return new ItemBuilder(Material.ENCHANTMENT_TABLE).name("&d查询玩家附魔台内物品")
                .lore("&7查看这名玩家的附魔物品、附魔书和材料。",
                        "&7在线时实时更新，离线时查看保存的物品。",
                        "&7只能查看，无法取出物品。", " ",
                        unavailable == null ? "&e点击查看!" : "&c" + unavailable).build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        String unavailable = menu.getUnavailableReason(player);
        if (unavailable != null) {
            player.sendMessage("§c" + unavailable);
            return;
        }
        menu.openMenu(player);
    }
}
