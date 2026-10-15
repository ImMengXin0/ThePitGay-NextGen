package net.mizukilab.pit.menu.trade.button;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.util.menu.buttons.DisplayButton;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class ItemDisplayButton extends DisplayButton {
    private final TradeManager tradeManager;
    private final boolean self;
    private final int index;
    private final long revision;
    public ItemDisplayButton(ItemStack item, TradeManager trade, boolean self) {
        this(item, trade, self, -1);
    }
    public ItemDisplayButton(ItemStack item, TradeManager trade, boolean self, int index) {
        super(item.clone(), true);
        this.tradeManager = trade; this.self = self; this.index = index; this.revision = trade.getRevision();
    }
    @Override public ItemStack getButtonItem(Player player) { return getItemStack().clone(); }
    @Override public void clicked(Player player, int slot, ClickType click, int hotbar, ItemStack current) {
        if (!self || (click != ClickType.LEFT && click != ClickType.RIGHT)) return;
        int selected = index >= 0 ? index : (player.equals(tradeManager.getPlayerA())
                ? tradeManager.getAItems() : tradeManager.getBItems()).indexOf(getItemStack());
        tradeManager.removeItem(player, selected, revision);
    }
}
