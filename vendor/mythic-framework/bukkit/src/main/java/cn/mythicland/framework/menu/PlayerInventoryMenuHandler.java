package cn.mythicland.framework.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public interface PlayerInventoryMenuHandler {
    boolean handlePlayerInventoryClick(Player player, InventoryClickEvent event, MenuManager menuManager);

    default boolean handlePlayerInventoryDrag(Player player, InventoryDragEvent event, MenuManager menuManager) {
        return false;
    }
}
