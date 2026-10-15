package net.mizukilab.pit.util.nametag;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class NametagThread extends BukkitRunnable {

    private NametagHandler handler;

    /**
     * Nametag Thread.
     *
     * @param handler instance.
     */
    public NametagThread(NametagHandler handler) {
        this.handler = handler;
        Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(), this, 0, 20);
    }

    @Override
    public void run() {
        if (!ThePit.getInstance().isEnabled()) {
            return;
        }
        tick();
    }

    /**
     * Thread Tick Logic.
     */
    private void tick() {
        if (this.handler.getAdapter() == null) {
            return;
        }

        NametagAdapter adapter = this.handler.getAdapter();
        boolean shared = adapter.isViewerIndependent();
        boolean prepared = false;
        java.util.List<BufferedNametag> plates = null;
        for (Player player : this.handler.getPlugin().getServer().getOnlinePlayers()) {
            NametagBoard board = this.handler.getBoards().get(player.getUniqueId());

            // This shouldn't happen, but just in case.
            if (board != null) {
                if (shared) {
                    if (!prepared) {
                        plates = adapter.getPlate(player);
                        prepared = true;
                    }
                    board.update(plates);
                } else {
                    board.update();
                }
            }
        }
    }
}
