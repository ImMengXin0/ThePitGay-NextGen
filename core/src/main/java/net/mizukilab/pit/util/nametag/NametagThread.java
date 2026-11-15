package net.mizukilab.pit.util.nametag;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class NametagThread extends BukkitRunnable {

    private final NametagHandler handler;

    /**
     * Nametag Thread.
     *
     * @param handler instance.
     */
    public NametagThread(NametagHandler handler) {
        this.handler = handler;
        runTaskTimer(handler.getPlugin(), 0L, 20L);
    }

    @Override
    public void run() {
        if (!handler.getPlugin().isEnabled()) {
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
