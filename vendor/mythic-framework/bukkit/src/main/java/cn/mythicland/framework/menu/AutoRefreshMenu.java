package cn.mythicland.framework.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface AutoRefreshMenu {
    default boolean shouldAutoRefresh(Player player) {
        return true;
    }

    default long autoRefreshIntervalTicks(Player player) {
        return 20L;
    }

    default boolean refreshInPlace(Player player, Inventory inventory, Menu contents) {
        return false;
    }

    default boolean shouldForceInventorySync(Player player) {
        return false;
    }
}
