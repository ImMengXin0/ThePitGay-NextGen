package net.mizukilab.pit.menu.trade.button;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.util.menu.buttons.DisplayButton;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class CoinDisplayButton extends DisplayButton {
    private final TradeManager tradeManager;
    private final boolean self;
    private final long revision;
    public CoinDisplayButton(ItemStack item, boolean cancel, TradeManager trade, boolean self) {
        super(item, true); this.tradeManager = trade; this.self = self; this.revision = trade.getRevision();
    }
    @Override public void clicked(Player player, int slot, ClickType click, int hotbar, ItemStack current) {
        if (self && (click == ClickType.LEFT || click == ClickType.RIGHT) && revision == tradeManager.getRevision())
            tradeManager.setCoins(player, 0);
    }
}
