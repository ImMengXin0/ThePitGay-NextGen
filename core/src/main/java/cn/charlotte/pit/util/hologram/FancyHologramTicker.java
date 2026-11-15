package cn.charlotte.pit.util.hologram;

import org.bukkit.scheduler.BukkitRunnable;

/** Keeps attachments and legacy per-player view handlers on Paper's server thread. */
public final class FancyHologramTicker extends BukkitRunnable {
    @Override public void run() {
        for (Hologram hologram : HologramAPI.getHolograms()) {
            if (hologram instanceof FancyHologramAdapter fancy) fancy.tick();
        }
    }
}
