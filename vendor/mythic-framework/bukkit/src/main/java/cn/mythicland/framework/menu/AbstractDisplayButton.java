package cn.mythicland.framework.menu;

import org.bukkit.entity.Player;

/**
 * A button that only renders an item and intentionally has no click action.
 */
public abstract class AbstractDisplayButton extends AbstractButton {
    @Override
    public final void onClick(Player player, ClickType clickType, MenuManager menuManager) {
    }
}
