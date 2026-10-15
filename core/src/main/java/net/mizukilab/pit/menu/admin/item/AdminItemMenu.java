package net.mizukilab.pit.menu.admin.item;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.*;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.map.kingsquests.item.MiniCake;
import net.mizukilab.pit.menu.admin.item.button.MythicItemButton;
import net.mizukilab.pit.menu.admin.item.button.PitItemButton;
import net.mizukilab.pit.menu.admin.item.button.RedPacketButton;
import net.mizukilab.pit.menu.admin.item.button.ShopItemButton;
import net.mizukilab.pit.menu.shop.button.type.*;
import net.mizukilab.pit.util.FuncsKt;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/17 22:41
 */
public class AdminItemMenu extends Menu {

    private final boolean admin;

    public AdminItemMenu() {
        this.admin = true;
    }

    public AdminItemMenu(boolean isAdmin) {
        this.admin = isAdmin;
    }

    @Override
    public String getTitle(Player player) {
        return "Pit物品";
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        if (admin) return getAdminButtons();
        Map<Integer, Button> buttonMap = new HashMap<>();
        try {
            buttonMap.put(0, new MythicItemButton(0));
            buttonMap.put(1, new MythicItemButton(1));
            buttonMap.put(2, new MythicItemButton(2));
            buttonMap.put(3, new MythicItemButton(3));
            buttonMap.put(4, new ShopItemButton(Material.DIAMOND_HELMET, "shopItem"));
            buttonMap.put(5, new ShopItemButton(Material.DIAMOND_CHESTPLATE, "shopItem"));
            buttonMap.put(6, new ShopItemButton(Material.DIAMOND_LEGGINGS, "shopItem"));
            buttonMap.put(7, new ShopItemButton(Material.DIAMOND_BOOTS, "shopItem"));
            buttonMap.put(8, new ShopItemButton(Material.DIAMOND_SWORD, "shopItem"));
            buttonMap.put(9, new ShopItemButton(Material.OBSIDIAN, "shopItem", 64));

            buttonMap.put(10, new MythicItemButton(8));
            buttonMap.put(11, new GoldPickaxeShopButton());
            buttonMap.put(12, new CombatSpadeShopButton());
            buttonMap.put(13, new MythicItemButton(4));
            buttonMap.put(14, new PantsBundleShopButton());


        } catch (Exception e) {
            CC.printError(player, e);
        }


        return buttonMap;
    }

    private Map<Integer, Button> getAdminButtons() {
        Map<Integer, Button> buttons = new HashMap<>();
        addRow(buttons, 0, "§d神话装备", (byte) 10,
                new MythicItemButton(0), new MythicItemButton(1), new MythicItemButton(3),
                new MythicItemButton(2), new MythicItemButton(4), coloredPants(MythicColor.RAGE),
                coloredPants(MythicColor.AQUA), coloredPants(MythicColor.DARK_GREEN));
        addRow(buttons, 1, "§f天使套与特殊防具", (byte) 0,
                new PitItemButton(() -> new AngelHelmet().toItemStack()),
                new PitItemButton(() -> new AngelChestplate().toItemStack()),
                new PitItemButton(() -> new AngelLeggings().toItemStack()),
                new PitItemButton(() -> new AngelBoots().toItemStack()),
                new PitItemButton(() -> {
                    AngelChestplate item = new AngelChestplate();
                    item.setLive(12);
                    return item.toItemStack();
                }),
                new PitItemButton(() -> new ArmageddonBoots().toItemStack()),
                new PitItemButton(() -> {
                    ArmageddonBoots item = new ArmageddonBoots();
                    item.setLive(3);
                    return item.toItemStack();
                }),
                new PitItemButton(() -> new GoldenHelmet().toItemStack()));
        addRow(buttons, 2, "§b常规装备与武器", (byte) 3,
                new ShopItemButton(Material.DIAMOND_HELMET, "shopItem"),
                new ShopItemButton(Material.DIAMOND_CHESTPLATE, "shopItem"),
                new ShopItemButton(Material.DIAMOND_LEGGINGS, "shopItem"),
                new ShopItemButton(Material.DIAMOND_BOOTS, "shopItem"),
                new ShopItemButton(Material.DIAMOND_SWORD, "shopItem"),
                new GoldPickaxeShopButton(), new CombatSpadeShopButton(),
                new PitItemButton(() -> new JewelSword().toItemStack()));
        addRow(buttons, 3, "§a附魔与强化", (byte) 5,
                new PitItemButton(() -> ((MythicEnchantingTable) FuncsKt.getInstance(ThePit.getInstance()
                        .getItemFactor().getItemMap().get("enchant_table_mobile"))).toItemStack()),
                new PitItemButton(MythicRepairKit::toItemStack0),
                new PitItemButton(() -> new TotallyLegitGem().toItemStack()),
                new PitItemButton(() -> new GlobalAttentionGem().toItemStack()),
                new PitItemButton(() -> new LuckyGem().toItemStack()),
                new MythicItemButton(11), new PitItemButton(ChunkOfVileItem::toItemStack),
                new PitItemButton(FunkyFeather::toItemStack));
        addRow(buttons, 4, "§e消耗与奖励", (byte) 4,
                new PitItemButton(JumpBoostPotion::toItemStack), new PitItemButton(BountySolventPotion::toItemStack),
                new PitItemButton(FourInARowGadget::toItemStack), new PitItemButton(PitCactus::toItemStack),
                new RedPacketButton(), new PitItemButton(() -> new UberDrop().toItemStack()),
                new PitItemButton(() -> MiniCake.INSTANCE.toItemStack()),
                new PitItemButton(() -> new MusicalRune().toItemStack()));
        addRow(buttons, 5, "§6收纳、方块与染色", (byte) 1,
                new PantsBundleShopButton(), new SwordBundleShopButton(), new BowBundleShopButton(),
                new ShopItemButton(Material.TNT, "tnt", 64), new ShopItemButton(Material.OBSIDIAN, "shopItem", 64),
                new MythicItemButton(8));
        return buttons;
    }

    private static PitItemButton coloredPants(MythicColor color) {
        return new PitItemButton(() -> {
            MythicLeggingsItem item = new MythicLeggingsItem();
            item.setColor(color);
            return item.toItemStack();
        });
    }

    private static void addRow(Map<Integer, Button> buttons, int row, String title, byte color, Button... items) {
        if (row < 0 || row >= 6 || items.length > 8) throw new IllegalArgumentException("Invalid item menu row");
        buttons.put(row * 9, Button.placeholder(Material.STAINED_GLASS_PANE, color, title));
        for (int i = 0; i < items.length; i++) buttons.put(row * 9 + 1 + i, items[i]);
    }

    @Override
    public void onClickEvent(InventoryClickEvent event) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            final PlayerInventory inv = (PlayerInventory) event.getClickedInventory();
            inv.setItem(event.getSlot(), new ItemStack(Material.AIR));
        }
    }
}
