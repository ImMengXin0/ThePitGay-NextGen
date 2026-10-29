package cn.mythicland.framework.menu;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public abstract class AbstractMenu {
    public abstract String buildTitle(Player player);

    protected abstract int resolveRowCount(Player player);

    protected void populateMenu(Player player, Menu menu) {
    }

    public boolean shouldRefreshOnEmptySlotClick(Player player, int slot, ClickType clickType) {
        return false;
    }

    public boolean allowsInventoryInteraction() {
        return false;
    }

    public void onClose(Player player, Inventory inventory) {
    }

    public final String resolveTitle(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', this.buildTitle(player));
        return title.length() <= 32 ? title : title.substring(0, 32);
    }

    public final Menu buildMenu(Player player) {
        int rows = cn.mythicland.framework.math.Clamps.clamp(this.resolveRowCount(player), 1, 6);
        Menu menu = new Menu(rows * 9);
        MenuSlotCompiler.populate(this, player, menu);
        this.populateMenu(player, menu);
        return menu;
    }

    protected final int slot(int row, int column) {
        if (row < 0 || column < 0 || column > 8) {
            throw new IllegalArgumentException("Invalid menu position: row=" + row + ", column=" + column);
        }
        return row * 9 + column;
    }
}
