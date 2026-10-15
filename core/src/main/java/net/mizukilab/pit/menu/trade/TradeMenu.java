package net.mizukilab.pit.menu.trade;

import net.mizukilab.pit.menu.GlassPaneButton;
import net.mizukilab.pit.menu.trade.button.*;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class TradeMenu extends Menu {
    private static final int[] SELF = {0, 1, 2, 3, 9, 10, 11, 12};
    private static final int[] TARGET = {5, 6, 7, 8, 14, 15, 16, 17};
    private final TradeManager tradeManager;
    public TradeMenu(TradeManager tradeManager) { this.tradeManager = tradeManager; }
    public boolean belongsTo(TradeManager trade) { return tradeManager == trade; }
    @Override public String getTitle(Player player) {
        return "交易 - " + (player.equals(tradeManager.getPlayerA()) ? tradeManager.getPlayerB() : tradeManager.getPlayerA()).getName();
    }
    @Override public int getSize() { return 27; }
    @Override public boolean isReadOnly() { return true; }
    @Override public boolean isAutoUpdate() { return !tradeManager.isEnd(); }
    @Override public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        if (!tradeManager.isActive()) return buttons;
        boolean a = player.equals(tradeManager.getPlayerA());
        buttons.put(21, new SelfConfirmButton(tradeManager));
        buttons.put(23, new TargetConfirmButton(tradeManager));
        buttons.put(18, new CoinButton(tradeManager));
        putOffer(buttons, a ? tradeManager.getAItems() : tradeManager.getBItems(),
                a ? tradeManager.getACoins() : tradeManager.getBCoins(), SELF, true);
        putOffer(buttons, a ? tradeManager.getBItems() : tradeManager.getAItems(),
                a ? tradeManager.getBCoins() : tradeManager.getACoins(), TARGET, false);
        for (int slot : new int[]{4, 13, 22}) buttons.put(slot, new GlassPaneButton());
        return buttons;
    }
    private void putOffer(Map<Integer, Button> buttons, List<ItemStack> items, double coins, int[] slots, boolean self) {
        int offset = coins > 0 ? 1 : 0;
        if (coins > 0) buttons.put(slots[0], new CoinDisplayButton(
                new ItemBuilder(Material.GOLD_INGOT).name("&6" + (long) coins + " 硬币").build(), true, tradeManager, self));
        for (int i = 0; i < items.size() && i + offset < slots.length; i++)
            buttons.put(slots[i + offset], new ItemDisplayButton(items.get(i), tradeManager, self, i));
    }
    @Override public void onClose(Player player) {
        if (player.equals(tradeManager.getPlayerA()) && tradeManager.isAEditingCoins()) return;
        if (player.equals(tradeManager.getPlayerB()) && tradeManager.isBEditingCoins()) return;
        tradeManager.onPlayerCancel(player);
    }
    @Override public void onClickEvent(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !tradeManager.isActive()) return;
        int top = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < top || event.getRawSlot() >= top + 36) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) return;
        tradeManager.addItem(player, event.getSlot());
    }
}
