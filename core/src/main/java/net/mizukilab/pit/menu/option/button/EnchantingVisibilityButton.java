package net.mizukilab.pit.menu.option.button;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class EnchantingVisibilityButton extends Button {
    @Override
    public ItemStack getButtonItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        boolean canChange = profile.isSupporter() || PlayerUtil.isStaff(player);
        if (!canChange) {
            profile.getPlayerOption().setEnchantingVisibility(true);
        }
        return new ItemBuilder(Material.ENCHANTMENT_TABLE).name("&a隐私选项: 附魔台物品")
                .lore("&7允许其他玩家通过 /view 查看你的附魔台物品。",
                        "&7包括附魔物品、附魔书和材料。",
                        "&7本人和管理人员始终可以查看。", " ",
                        "&7当前: " + (profile.getPlayerOption().isEnchantingVisibility() ? "&a展示" : "&c隐藏"),
                        canChange ? "&e点击切换此选项!" : "&c需要&e天坑乱斗会员").build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded()) return;
        if (!profile.isSupporter() && !PlayerUtil.isStaff(player)) return;
        profile.getPlayerOption().setEnchantingVisibility(!profile.getPlayerOption().isEnchantingVisibility());
    }

    @Override
    public boolean shouldUpdate(Player player, int slot, ClickType clickType) {
        return true;
    }
}
