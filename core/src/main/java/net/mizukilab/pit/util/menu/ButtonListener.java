package net.mizukilab.pit.util.menu;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.util.exception.CancelOp;
import nya.Skip;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.PlayerInventory;

//@AutoRegister
@Skip
public class ButtonListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onButtonPress(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Menu openMenu = Menu.find(player, event.getView().getTopInventory());

        if (openMenu != null) {
            if (openMenu.isReadOnly()) {
                event.setCancelled(true);
                openMenu.onClickEvent(event);
                if (Menu.find(player, event.getView().getTopInventory()) != openMenu) return;
                if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    Button button = openMenu.getButtons().get(event.getRawSlot());
                    if (button != null && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                        button.clicked(player, event.getRawSlot(), event.getClick(), event.getHotbarButton(), event.getCurrentItem());
                    }
                }
                return;
            }
            if (event.getClickedInventory() instanceof PlayerInventory) {
                event.setCancelled(true);
            }


            openMenu.onClickEvent(event);
            // An extension may replace/close the inventory while processing its input.
            if (Menu.find(player, event.getView().getTopInventory()) != openMenu) {
                event.setCancelled(true);
                return;
            }
            if (event.getSlot() != event.getRawSlot()) {
                if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
                    event.setCancelled(true);
                }

                return;
            }

            if (openMenu.getButtons().containsKey(event.getSlot())) {
                Button button = openMenu.getButtons().get(event.getSlot());
                boolean cancel = button.shouldCancel(player, event.getSlot(), event.getClick());

                // Preserve the menu/other listener's cancellation. For explicitly
                // movable slots let Bukkit transfer the item and update its source.
                if (cancel) event.setCancelled(true);

                button.clicked(player, event.getSlot(), event.getClick(), event.getHotbarButton(), event.getCurrentItem());

                if (Menu.currentlyOpenedMenus.containsKey(player.getUniqueId())) {
                    Menu newMenu = Menu.currentlyOpenedMenus.get(player.getUniqueId());

                    if (newMenu == openMenu) {
                        boolean buttonUpdate = button.shouldUpdate(player, event.getSlot(), event.getClick());

                        if (buttonUpdate) {
                            openMenu.setClosedByMenu(true);
                            newMenu.openMenu(player);
                        }
                    }
                }

                if (event.isCancelled()) {
                    Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), player::updateInventory, 1L);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Menu menu = Menu.find(player, event.getView().getTopInventory());
            if (menu == null) return;
            if (menu.isReadOnly()) {
                event.setCancelled(true);
                return;
            }
            ClickType dragClick = event.getType() == org.bukkit.event.inventory.DragType.SINGLE
                    ? ClickType.RIGHT : ClickType.LEFT;
            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= topSize) continue;
                Button button = menu.getButtons().get(slot);
                if (button == null || button.shouldCancel(player, slot, dragClick)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Menu openMenu = Menu.find(player, event.getInventory());

        if (openMenu != null) {
            try {
                openMenu.onClose(player);
                Menu.forget(player, openMenu);
            } catch (CancelOp e){
                Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                    if (Menu.find(player) != openMenu) return;
                    InventoryType type = player.getOpenInventory().getTopInventory().getType();
                    if (player.isOnline() && !player.isDead()
                            && (type == InventoryType.CRAFTING || type == InventoryType.PLAYER)) {
                        if (player.openInventory(event.getInventory()) != null) {
                            openMenu.openMenu(player);
                            return;
                        }
                    }
                    openMenu.setClosedByMenu(true);
                    openMenu.onClose(player);
                    Menu.forget(player, openMenu);
                });
            }
        }
    }

}
