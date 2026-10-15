package net.mizukilab.pit.menu.trade.button;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class CoinButton extends Button {
    private final TradeManager tradeManager;
    public CoinButton(TradeManager trade) { tradeManager = trade; }
    @Override public ItemStack getButtonItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        return new ItemBuilder(Material.GOLD_INGOT).name("&6硬币交易")
                .lore("&7每日硬币交易: " + (long) profile.getTradeLimit().getAmount() + "/50000",
                        "&7每日交易次数: " + profile.getTradeLimit().getTimes() + "/25", "&e点击设置金额，输入 0 取回硬币。").build();
    }
    @Override public void clicked(Player player, int slot, ClickType click, int hotbar, ItemStack current) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        long token = tradeManager.beginCoinEdit(player);
        if (token < 0) return;
        try {
            ThePit.getInstance().getSignGui().open(player, new String[]{"", "~~~~~~~~~~~~~~", "在上方", "输入交易金额"},
                    (responding, lines) -> tradeManager.finishCoinEdit(responding, token, lines));
        } catch (RuntimeException failure) {
            tradeManager.finishCoinEdit(player, token, null);
        }
    }
}
