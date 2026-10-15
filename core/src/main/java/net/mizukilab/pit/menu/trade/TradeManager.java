package net.mizukilab.pit.menu.trade;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.TradeData;
import cn.charlotte.pit.data.sub.PlayerInv;
import net.mizukilab.pit.medal.impl.challenge.FirstTradeMedal;
import net.mizukilab.pit.trade.TradeInventory;
import net.mizukilab.pit.trade.TradeMonitorRunnable;
import net.mizukilab.pit.util.cooldown.Cooldown;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** A single server-thread session owns both escrows and both menu instances. */
public class TradeManager {
    public static final Map<UUID, TradeManager> trades = new HashMap<>();
    private final Player playerA, playerB;
    private final PlayerProfile profileA, profileB;
    private final TradeMenu menuA, menuB;
    private final List<ItemStack> aItems = new ArrayList<>(), bItems = new ArrayList<>();
    private double aCoins, bCoins;
    private boolean aConfirm, bConfirm, aEditingCoins, bEditingCoins, end;
    private Cooldown aPutCooldown = new Cooldown(5, TimeUnit.SECONDS);
    private Cooldown bPutCooldown = new Cooldown(5, TimeUnit.SECONDS);
    private long revision, aEditToken, bEditToken;

    public static String startFailure(Player a, Player b) {
        if (a == null || b == null || !a.isOnline() || !b.isOnline() || a.isDead() || b.isDead())
            return "§c双方必须在线且存活才能交易。";
        if (a.getUniqueId().equals(b.getUniqueId())) return "§c不能与自己交易。";
        if (trades.containsKey(a.getUniqueId()) || trades.containsKey(b.getUniqueId()))
            return "§c你或对方已有正在进行的交易，请先结束原交易。";
        PlayerProfile pa = PlayerProfile.getPlayerProfileByUuid(a.getUniqueId());
        PlayerProfile pb = PlayerProfile.getPlayerProfileByUuid(b.getUniqueId());
        if (!pa.isLoaded() || !pb.isLoaded()) return "§c玩家档案尚未加载。";
        if (pa.isTempInvUsing() || pb.isTempInvUsing()) return "§c使用临时背包时无法交易。";
        return null;
    }

    public static TradeManager start(Player a, Player b) {
        String failure = startFailure(a, b);
        if (failure != null) { a.sendMessage(failure); return null; }
        TradeManager trade = new TradeManager(a, b);
        trade.openMenu(a);
        trade.openMenu(b);
        return trade;
    }

    public TradeManager(Player a, Player b) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Trades must start on the server thread");
        String failure = startFailure(a, b);
        if (failure != null) throw new IllegalStateException(failure);
        playerA = a; playerB = b;
        profileA = PlayerProfile.getPlayerProfileByUuid(a.getUniqueId());
        profileB = PlayerProfile.getPlayerProfileByUuid(b.getUniqueId());
        menuA = new TradeMenu(this); menuB = new TradeMenu(this);
        trades.put(a.getUniqueId(), this); trades.put(b.getUniqueId(), this);
        TradeMonitorRunnable.removeRequests(a.getUniqueId());
        TradeMonitorRunnable.removeRequests(b.getUniqueId());
    }

    public boolean isActive() {
        return !end && trades.get(playerA.getUniqueId()) == this && trades.get(playerB.getUniqueId()) == this;
    }
    private boolean participant(Player player) { return playerA.equals(player) || playerB.equals(player); }
    private boolean usable(Player player) {
        return Bukkit.isPrimaryThread() && participant(player) && isActive()
                && playerA.isOnline() && playerB.isOnline() && !playerA.isDead() && !playerB.isDead()
                && profileA.isLoaded() && profileB.isLoaded()
                && PlayerProfile.getPlayerProfileByUuid(playerA.getUniqueId()) == profileA
                && PlayerProfile.getPlayerProfileByUuid(playerB.getUniqueId()) == profileB
                && !profileA.isTempInvUsing() && !profileB.isTempInvUsing();
    }
    public void openMenu(Player player) {
        if (!usable(player) || (player.equals(playerA) ? aEditingCoins : bEditingCoins)) return;
        TradeMenu menu = player.equals(playerA) ? menuA : menuB;
        menu.openMenu(player);
        if (isActive() && Menu.find(player, player.getOpenInventory().getTopInventory()) != menu) onPlayerCancel(player);
    }
    private void refreshMenus() { if (isActive()) { openMenu(playerA); openMenu(playerB); } }
    private void changed() {
        revision++; aConfirm = bConfirm = false;
        aPutCooldown = new Cooldown(5, TimeUnit.SECONDS);
        bPutCooldown = new Cooldown(5, TimeUnit.SECONDS);
    }

    public boolean addItem(Player player, int slot) {
        if (!usable(player) || slot < 0 || slot >= 36 || aEditingCoins || bEditingCoins) return false;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return false;
        if (!ItemUtil.canTrade(item) && !player.hasPermission("pit.admin")) {
            player.sendMessage("§c此物品无法用于交易！"); return false;
        }
        List<ItemStack> items = player.equals(playerA) ? aItems : bItems;
        double coins = player.equals(playerA) ? aCoins : bCoins;
        if (items.size() >= (coins > 0 ? 7 : 8)) {
            player.sendMessage("§c你无法再放入更多物品了！"); return false;
        }
        ItemStack offered = item.clone();
        player.getInventory().setItem(slot, null);
        items.add(offered);
        TradeInventory.snapshot(player, player.equals(playerA) ? profileA : profileB);
        changed(); refreshMenus();
        return true;
    }

    public boolean removeItem(Player player, int index, long expectedRevision) {
        if (!usable(player) || revision != expectedRevision || aEditingCoins || bEditingCoins) return false;
        List<ItemStack> items = player.equals(playerA) ? aItems : bItems;
        if (index < 0 || index >= items.size()) return false;
        ItemStack[] result = TradeInventory.withItems(player.getInventory(), List.of(items.get(index)));
        if (result == null) { player.sendMessage("§c背包空间不足，请腾出空间或取消交易领取退还物品。"); return false; }
        player.getInventory().setContents(result);
        items.remove(index);
        TradeInventory.snapshot(player, player.equals(playerA) ? profileA : profileB);
        changed(); refreshMenus();
        return true;
    }

    public boolean setCoins(Player player, double coins) {
        if (!usable(player) || !Double.isFinite(coins) || coins < 0 || coins > 50000 || coins != Math.floor(coins)) return false;
        boolean a = player.equals(playerA);
        PlayerProfile owner = a ? profileA : profileB;
        double previous = a ? aCoins : bCoins;
        if (coins > 0 && (a ? aItems : bItems).size() >= 8) {
            player.sendMessage("§c请先取回一组物品，给硬币留出位置。"); return false;
        }
        if (!Double.isFinite(owner.getCoins()) || coins > owner.getCoins() + previous) {
            player.sendMessage("§c你的硬币不足！"); return false;
        }
        double total = coins + (a ? bCoins : aCoins);
        profileA.getTradeLimit().refresh(); profileB.getTradeLimit().refresh();
        if (coins > 0 && (profileA.getTradeLimit().getAmount() + total > 50000
                || profileB.getTradeLimit().getAmount() + total > 50000)) {
            player.sendMessage("§c超过了双方其中一人的每日交易硬币上限。"); return false;
        }
        owner.setCoins(owner.getCoins() + previous - coins);
        if (a) aCoins = coins; else bCoins = coins;
        changed(); refreshMenus();
        return true;
    }

    public long beginCoinEdit(Player player) {
        if (!usable(player) || (player.equals(playerA) ? aEditingCoins : bEditingCoins)) return -1;
        changed();
        if (player.equals(playerA)) { aEditingCoins = true; aEditToken++; }
        else { bEditingCoins = true; bEditToken++; }
        player.closeInventory(); refreshMenus();
        return player.equals(playerA) ? aEditToken : bEditToken;
    }
    public void finishCoinEdit(Player player, long token, String[] lines) {
        if (!usable(player)) return;
        boolean a = player.equals(playerA);
        if (!(a ? aEditingCoins : bEditingCoins) || token != (a ? aEditToken : bEditToken)) return;
        if (a) aEditingCoins = false; else bEditingCoins = false;
        try {
            if (lines == null || lines.length == 0) throw new NumberFormatException();
            long amount = Long.parseLong(lines[0].replace("\"", "").trim());
            if (amount < 0 || amount > 50000) throw new NumberFormatException();
            setCoins(player, amount);
        } catch (NumberFormatException invalid) { player.sendMessage("§c请输入 0 到 50000 之间的整数金额！"); }
        openMenu(player);
    }
    public void confirm(Player player, boolean confirm) {
        if (!usable(player) || aEditingCoins || bEditingCoins) return;
        if (confirm && (!(player.equals(playerA) ? aPutCooldown : bPutCooldown).hasExpired()
                || (aItems.isEmpty() && bItems.isEmpty() && aCoins == 0 && bCoins == 0))) return;
        if (player.equals(playerA)) aConfirm = confirm; else bConfirm = confirm;
        if (aConfirm && bConfirm) doTrade();
        refreshMenus();
    }

    private void doTrade() {
        if (!usable(playerA) || !aConfirm || !bConfirm || aEditingCoins || bEditingCoins) return;
        if (Menu.find(playerA, playerA.getOpenInventory().getTopInventory()) != menuA
                || Menu.find(playerB, playerB.getOpenInventory().getTopInventory()) != menuB) {
            onPlayerCancel(playerA); return;
        }
        profileA.getTradeLimit().refresh(); profileB.getTradeLimit().refresh();
        if (!profileA.getCombatTimer().hasExpired() || !profileB.getCombatTimer().hasExpired()
                || profileA.getTradeLimit().getTimes() >= 25 || profileB.getTradeLimit().getTimes() >= 25
                || profileA.getTradeLimit().getAmount() + aCoins + bCoins > 50000
                || profileB.getTradeLimit().getAmount() + aCoins + bCoins > 50000) {
            message("§c当前状态或每日交易限制不允许完成交易。"); onPlayerCancel(playerA); return;
        }
        ItemStack[] beforeA = TradeInventory.copy(playerA.getInventory().getContents());
        ItemStack[] beforeB = TradeInventory.copy(playerB.getInventory().getContents());
        ItemStack[] afterA = TradeInventory.withItems(playerA.getInventory(), bItems);
        ItemStack[] afterB = TradeInventory.withItems(playerB.getInventory(), aItems);
        if (afterA == null || afterB == null) {
            changed(); message("§c双方其中一人的背包空间不足，交易尚未成交，请腾出空间后重新确认。"); return;
        }
        double balanceA = profileA.getCoins(), balanceB = profileB.getCoins();
        if (!Double.isFinite(balanceA + bCoins) || !Double.isFinite(balanceB + aCoins)) {
            onPlayerCancel(playerA); return;
        }
        List<ItemStack> paidA = copies(aItems), paidB = copies(bItems);
        double paidACoins = aCoins, paidBCoins = bCoins;
        // Finalize accounting before messages, close events, scheduling or database work.
        end = true;
        try {
            playerA.getInventory().setContents(afterA);
            playerB.getInventory().setContents(afterB);
            profileA.setCoins(balanceA + bCoins); profileB.setCoins(balanceB + aCoins);
        } catch (RuntimeException failure) {
            playerA.getInventory().setContents(beforeA); playerB.getInventory().setContents(beforeB);
            profileA.setCoins(balanceA); profileB.setCoins(balanceB);
            end = false; onPlayerCancel(playerA);
            throw failure;
        }
        clearEscrow(); unregister();
        recordLimit(profileA, paidACoins + paidBCoins); recordLimit(profileB, paidACoins + paidBCoins);
        TradeInventory.snapshot(playerA, profileA); TradeInventory.snapshot(playerB, profileB);
        closeMenus();
        try {
            recordTrade(playerA, playerB, paidA, paidB, paidACoins, paidBCoins);
            message("§a交易成功！物品和硬币已到账。");
            FirstTradeMedal medal = new FirstTradeMedal();
            medal.addProgress(profileA, 1); medal.addProgress(profileB, 1);
        } catch (RuntimeException failure) {
            ThePit.getInstance().getLogger().log(Level.SEVERE, "Trade completed but notification/logging failed", failure);
        }
    }
    public static void recordLimit(PlayerProfile profile, double amount) {
        profile.getTradeLimit().setAmount(profile.getTradeLimit().getAmount() + amount);
        profile.getTradeLimit().setTimes(profile.getTradeLimit().getTimes() + 1);
    }
    public static void recordTrade(Player a, Player b, List<ItemStack> aItems, List<ItemStack> bItems, double aCoins, double bCoins) {
        TradeData data = new TradeData(a.getUniqueId().toString(), b.getUniqueId().toString(), a.getName(), b.getName());
        data.setAPaidCoin((long) aCoins); data.setBPaidCoin((long) bCoins);
        PlayerInv ai = new PlayerInv(), bi = new PlayerInv();
        ai.setContents(copies(aItems).toArray(new ItemStack[36])); bi.setContents(copies(bItems).toArray(new ItemStack[36]));
        data.setAPaidItem(ai); data.setBPaidItem(bi);
        data.setCompleteTime(System.currentTimeMillis()); data.save();
    }
    public void onPlayerCancel(Player player) {
        if (!Bukkit.isPrimaryThread() || end || !participant(player)) return;
        end = true;
        List<ItemStack> refundA = copies(aItems), refundB = copies(bItems);
        double refundACoins = aCoins, refundBCoins = bCoins;
        clearEscrow(); unregister();
        profileA.setCoins(profileA.getCoins() + refundACoins); profileB.setCoins(profileB.getCoins() + refundBCoins);
        TradeInventory.refund(playerA, profileA, refundA); TradeInventory.refund(playerB, profileB, refundB);
        TradeInventory.snapshot(playerA, profileA); TradeInventory.snapshot(playerB, profileB);
        closeMenus(); message("§c交易已取消，物品和硬币已退还。");
    }
    private void clearEscrow() {
        aItems.clear(); bItems.clear(); aCoins = bCoins = 0;
        aConfirm = bConfirm = aEditingCoins = bEditingCoins = false;
        revision++; aEditToken++; bEditToken++;
    }
    private void unregister() {
        if (trades.get(playerA.getUniqueId()) == this) trades.remove(playerA.getUniqueId());
        if (trades.get(playerB.getUniqueId()) == this) trades.remove(playerB.getUniqueId());
    }
    private void closeMenus() {
        if (Menu.find(playerA) instanceof TradeMenu menu && menu.belongsTo(this)) playerA.closeInventory();
        if (Menu.find(playerB) instanceof TradeMenu menu && menu.belongsTo(this)) playerB.closeInventory();
    }
    private void message(String text) { playerA.sendMessage(text); playerB.sendMessage(text); }
    private static List<ItemStack> copies(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>(items.size());
        for (ItemStack item : items) result.add(item.clone());
        return result;
    }
    public static void cancelAll() {
        for (TradeManager trade : new ArrayList<>(trades.values())) trade.onPlayerCancel(trade.playerA);
    }
    public static void cancelFor(Player player) {
        TradeManager trade = trades.get(player.getUniqueId());
        if (trade != null) trade.onPlayerCancel(player);
    }
    public Player getPlayerA() { return playerA; }
    public Player getPlayerB() { return playerB; }
    public List<ItemStack> getAItems() { return Collections.unmodifiableList(copies(aItems)); }
    public List<ItemStack> getBItems() { return Collections.unmodifiableList(copies(bItems)); }
    public double getACoins() { return aCoins; }
    public double getBCoins() { return bCoins; }
    public void setACoins(double coins) { setCoins(playerA, coins); }
    public void setBCoins(double coins) { setCoins(playerB, coins); }
    public boolean isAConfirm() { return aConfirm; }
    public boolean isBConfirm() { return bConfirm; }
    public void setAConfirm(boolean confirm) { confirm(playerA, confirm); }
    public void setBConfirm(boolean confirm) { confirm(playerB, confirm); }
    public boolean isAEditingCoins() { return aEditingCoins; }
    public boolean isBEditingCoins() { return bEditingCoins; }
    public void setAEditingCoins(boolean editing) { if (isActive()) aEditingCoins = editing; }
    public void setBEditingCoins(boolean editing) { if (isActive()) bEditingCoins = editing; }
    public Cooldown getAPutCooldown() { return aPutCooldown; }
    public Cooldown getBPutCooldown() { return bPutCooldown; }
    public void setAPutCooldown(Cooldown cooldown) { aPutCooldown = Objects.requireNonNull(cooldown); }
    public void setBPutCooldown(Cooldown cooldown) { bPutCooldown = Objects.requireNonNull(cooldown); }
    public boolean isEnd() { return end; }
    public void setEnd(boolean end) { if (end) onPlayerCancel(playerA); }
    public long getRevision() { return revision; }
    protected boolean canEqual(Object other) { return other instanceof TradeManager; }
}
