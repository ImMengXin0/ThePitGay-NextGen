package net.mizukilab.pit.menu.offer.button;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.OfferData;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.trade.TradeInventory;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.logging.Level;

public class OfferButton extends Button {
    private final Player target;
    private final OfferData expected;
    public OfferButton(Player target) {
        this(target, PlayerProfile.getPlayerProfileByUuid(target.getUniqueId()).getOfferData());
    }
    public OfferButton(Player target, OfferData offer) { this.target = target; expected = offer; }
    @Override public ItemStack getButtonItem(Player player) {
        return new ItemBuilder(Material.STAINED_CLAY).durability(5).name("&e接受交易报价")
                .lore("&7卖家: " + target.getName(), "&7价格: &6" + (long) expected.getPrice() + " 硬币",
                        "&e点击接受，成交前将再次检查报价。").build();
    }
    @Override public void clicked(Player player, int slot, ClickType click, int hotbar, ItemStack current) {
        if (!Bukkit.isPrimaryThread() || (click != ClickType.LEFT && click != ClickType.RIGHT)
                || !player.isOnline() || !target.isOnline() || player.isDead() || target.isDead()
                || player.getUniqueId().equals(target.getUniqueId())) return;
        PlayerProfile buyer = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        PlayerProfile seller = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        if (!buyer.isLoaded() || !seller.isLoaded() || seller.getOfferData() != expected
                || expected == null || !expected.hasActiveOffer() || !player.getUniqueId().equals(expected.getBuyer())
                || buyer.isTempInvUsing() || seller.isTempInvUsing()
                || TradeManager.trades.containsKey(player.getUniqueId()) || TradeManager.trades.containsKey(target.getUniqueId())) {
            player.sendMessage("§c报价已失效或双方正在进行其他交易。"); return;
        }
        double price = expected.getPrice(), buyerCoins = buyer.getCoins(), sellerCoins = seller.getCoins();
        buyer.getTradeLimit().refresh(); seller.getTradeLimit().refresh();
        if (!Double.isFinite(price) || price <= 0 || price > 50000 || price != Math.floor(price)
                || !Double.isFinite(buyerCoins) || !Double.isFinite(sellerCoins + price) || buyerCoins < price
                || !buyer.getCombatTimer().hasExpired() || !seller.getCombatTimer().hasExpired()
                || buyer.getTradeLimit().getTimes() >= 25 || seller.getTradeLimit().getTimes() >= 25
                || buyer.getTradeLimit().getAmount() + price > 50000 || seller.getTradeLimit().getAmount() + price > 50000) {
            player.sendMessage("§c硬币不足、处于战斗状态或超过今日交易限制。"); return;
        }
        ItemStack item = expected.getItemStack();
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return;
        ItemStack[] before = TradeInventory.copy(player.getInventory().getContents());
        ItemStack[] after = TradeInventory.withItems(player.getInventory(), List.of(item));
        if (after == null) { player.sendMessage("§c你的背包空间不足！"); return; }
        // Consume this exact offer before closing inventories or firing any other callbacks.
        seller.setOfferData(new OfferData());
        try {
            player.getInventory().setContents(after);
            buyer.setCoins(buyerCoins - price); seller.setCoins(sellerCoins + price);
        } catch (RuntimeException failure) {
            player.getInventory().setContents(before);
            buyer.setCoins(buyerCoins); seller.setCoins(sellerCoins);
            seller.setOfferData(expected);
            throw failure;
        }
        TradeManager.recordLimit(buyer, price); TradeManager.recordLimit(seller, price);
        TradeInventory.snapshot(player, buyer);
        player.closeInventory();
        try {
            TradeManager.recordTrade(player, target, List.of(), List.of(item), price, 0);
            player.sendMessage("§a交易成功！");
            target.sendMessage("§a" + player.getName() + " 接受了你的报价，硬币已到账。");
            var medal = new net.mizukilab.pit.medal.impl.challenge.FirstTradeMedal();
            medal.addProgress(buyer, 1);
            medal.addProgress(seller, 1);
        } catch (RuntimeException failure) {
            ThePit.getInstance().getLogger().log(Level.SEVERE, "Offer completed but notification/logging failed", failure);
        }
    }
}
