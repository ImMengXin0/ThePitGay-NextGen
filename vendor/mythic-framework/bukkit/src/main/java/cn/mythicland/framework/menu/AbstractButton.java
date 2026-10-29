package cn.mythicland.framework.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public abstract class AbstractButton {
    public abstract ItemStack buildDisplayItem(Player player);

    public abstract void onClick(Player player, ClickType clickType, MenuManager menuManager);

    public boolean shouldRefresh(Player player, ClickType clickType) {
        return false;
    }

    public boolean shouldClose(Player player, ClickType clickType) {
        return false;
    }
}
