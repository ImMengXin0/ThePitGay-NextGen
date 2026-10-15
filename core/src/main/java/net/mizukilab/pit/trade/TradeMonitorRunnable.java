package net.mizukilab.pit.trade;

import cn.charlotte.pit.data.temp.TradeRequest;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/5 0:30
 */
public class TradeMonitorRunnable extends BukkitRunnable {

    private final static List<TradeRequest> tradeRequests = new ObjectArrayList<>();

    @Override
    public void run() {
        tradeRequests.removeIf(tradeRequest -> tradeRequest.getCooldown().hasExpired()
                || !tradeRequest.getPlayer().isOnline() || !tradeRequest.getTarget().isOnline());
    }

    public static List<TradeRequest> getTradeRequests() {
        return tradeRequests;
    }

    public static void removeRequests(UUID player) {
        tradeRequests.removeIf(request -> request.getPlayer().getUniqueId().equals(player)
                || request.getTarget().getUniqueId().equals(player));
    }
}
