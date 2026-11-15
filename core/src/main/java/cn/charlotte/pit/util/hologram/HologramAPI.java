package cn.charlotte.pit.util.hologram;

import cn.charlotte.pit.ThePit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stable game API backed by FancyHolograms on the High branch. */
public abstract class HologramAPI {
    private static final CopyOnWriteArrayList<Hologram> holograms = new CopyOnWriteArrayList<>();
    public static boolean isMAGA = true;

    public static Hologram createHologram(Location location, String text) {
        Hologram hologram = ThePit.api.createHologram(location, text);
        register(hologram);
        return hologram;
    }

    static void register(Hologram hologram) { holograms.addIfAbsent(hologram); }

    public static boolean removeHologram(Location location, String text) {
        for (Hologram hologram : holograms) {
            if (hologram.getLocation().equals(location) && java.util.Objects.equals(hologram.getText(), text)) {
                hologram.deSpawn();
                return true;
            }
        }
        return false;
    }

    public static boolean removeHologram(Hologram hologram) { return holograms.remove(hologram); }
    public static Collection<Hologram> getHolograms() { return List.copyOf(holograms); }

    protected static boolean spawn(Hologram hologram, Collection<? extends Player> receivers) {
        // A world/chunk refresh must retain the original viewer permissions.
        hologram.update();
        return hologram.isSpawned();
    }

    protected static boolean despawn(Hologram hologram, Collection<? extends Player> receivers) {
        receivers.forEach(hologram::hide);
        return true;
    }

    public static void shutdown() {
        for (Hologram hologram : getHolograms()) hologram.deSpawn();
        holograms.clear();
    }

    public static boolean is1_8() { return false; }
    public static boolean packetsEnabled() { return true; }
}
