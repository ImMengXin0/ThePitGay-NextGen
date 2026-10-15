package net.mizukilab.pit.util.menu;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Menu {

    public static final Map<UUID, Menu> currentlyOpenedMenus = new ConcurrentHashMap<>();
    private static final Map<UUID, Inventory> currentlyOpenedInventories = new ConcurrentHashMap<>();
    private Map<Integer, Button> buttons = new HashMap<>();
    private boolean autoUpdate = false;
    private boolean updateAfterClick = true;
    private boolean closedByMenu = false;
    private boolean placeholder = false;
    private Button placeholderButton = Button.placeholder(Material.STAINED_GLASS_PANE, (byte) 15, " ");
    public static Menu find(Player player) {
        return currentlyOpenedMenus.get(player.getUniqueId());
    }

    public static Menu find(Player player, Inventory inventory) {
        // ContainerChest exposes a new CraftInventory wrapper for the same backing
        // inventory. Bukkit equality compares that backing inventory, not its title.
        return inventory != null && inventory.equals(currentlyOpenedInventories.get(player.getUniqueId()))
                ? find(player) : null;
    }

    static void forget(Player player, Menu menu) {
        if (currentlyOpenedMenus.remove(player.getUniqueId(), menu)) {
            currentlyOpenedInventories.remove(player.getUniqueId());
        }
    }
    private ItemStack createItemStack(Player player, Button button) {
        ItemStack item = button.getButtonItem(player);

        if (item == null) {
            return new ItemStack(Material.AIR);
        }

        return item;
    }

    public void openMenu(final Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                openMenu(player);
            });
            return;
        }


        try {
            this.buttons = this.getButtons(player);

            Menu previousMenu = Menu.currentlyOpenedMenus.get(player.getUniqueId());
            Inventory inventory = null;
            int size = this.getSize() == -1 ? this.size(this.buttons) : this.getSize();
            boolean update = false;
            String title = CC.translate(this.getTitle(player));

            if (title.length() > 32) {
                title = title.substring(0, 32);
            }

            if (player.getOpenInventory() != null) {
                if (previousMenu == null) {
                    player.closeInventory();
                } else {
                    int previousSize = player.getOpenInventory().getTopInventory().getSize();

                    if (previousMenu == this && find(player, player.getOpenInventory().getTopInventory()) == this
                            && previousSize == size && player.getOpenInventory().getTopInventory().getTitle().equals(title)) {
                        inventory = player.getOpenInventory().getTopInventory();
                        update = true;
                    } else {
                        previousMenu.setClosedByMenu(true);
                        player.closeInventory();
                    }
                }
            }

            if (inventory == null) {
                inventory = Bukkit.createInventory(player, size, title);
            }
            inventory.setContents(new ItemStack[inventory.getSize()]);

            currentlyOpenedMenus.put(player.getUniqueId(), this);
            currentlyOpenedInventories.put(player.getUniqueId(), inventory);

            for (Map.Entry<Integer, Button> buttonEntry : this.buttons.entrySet()) {
                inventory.setItem(buttonEntry.getKey(), createItemStack(player, buttonEntry.getValue()));
            }

            if (this.isPlaceholder()) {
                for (int index = 0; index < size; index++) {
                    if (this.buttons.get(index) == null) {
                        this.buttons.put(index, this.placeholderButton);
                        inventory.setItem(index, this.placeholderButton.getButtonItem(player));
                    }
                }
            }

            if (update) {
                player.updateInventory();
            } else {
                if (player.openInventory(inventory) == null) {
                    forget(player, this);
                    this.setClosedByMenu(true);
                    this.onClose(player);
                    return;
                }
            }

            this.onOpen(player);
            this.setClosedByMenu(false);
        } catch (Exception e) {
            CC.printError(player, e);
        }
    }

    public int size(Map<Integer, Button> buttons) {
        int highest = 0;

        for (int buttonValue : buttons.keySet()) {
            if (buttonValue > highest) {
                highest = buttonValue;
            }
        }

        return (int) (Math.ceil((highest + 1) / 9D) * 9D);
    }

    public int getSlot(int x, int y) {
        return ((9 * y) + x);
    }

    public int getSize() {
        return -1;
    }

    public abstract String getTitle(Player player);

    public abstract Map<Integer, Button> getButtons(Player player);

    public void onOpen(Player player) {
    }

    public void onClose(Player player) {
    }

    public void onClickEvent(InventoryClickEvent event) {

    }

    /** Read-only menus may navigate through buttons but cannot transfer items. */
    public boolean isReadOnly() {
        return false;
    }

    public boolean isAutoUpdate() {
        return this.autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public boolean isUpdateAfterClick() {
        return this.updateAfterClick;
    }

    public void setUpdateAfterClick(boolean updateAfterClick) {
        this.updateAfterClick = updateAfterClick;
    }

    public boolean isClosedByMenu() {
        return this.closedByMenu;
    }

    public void setClosedByMenu(boolean closedByMenu) {
        this.closedByMenu = closedByMenu;
    }

    public boolean isPlaceholder() {
        return this.placeholder;
    }

    public void setPlaceholder(boolean placeholder) {
        this.placeholder = placeholder;
    }

    public Button getPlaceholderButton() {
        return this.placeholderButton;
    }

    public void setPlaceholderButton(Button placeholderButton) {
        this.placeholderButton = placeholderButton;
    }

    public Map<Integer, Button> getButtons() {
        return this.buttons;
    }

    public void setButtons(Map<Integer, Button> buttons) {
        this.buttons = buttons;
    }
}
