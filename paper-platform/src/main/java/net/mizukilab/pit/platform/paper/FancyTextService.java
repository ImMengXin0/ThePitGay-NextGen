package net.mizukilab.pit.platform.paper;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.property.Visibility;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Plugin-owned, nonpersistent FancyHolograms displays. All mutations run on Bukkit's main thread. */
public final class FancyTextService implements Listener, AutoCloseable {
    private final HologramManager manager;
    private final Set<TextHandle> handles = new LinkedHashSet<>();
    private final BukkitTask visibilityTask;
    private boolean closed;

    public FancyTextService(JavaPlugin owner) {
        requireMainThread();
        manager = FancyHologramsPlugin.get().getHologramManager();
        Bukkit.getPluginManager().registerEvents(this, owner);
        visibilityTask = Bukkit.getScheduler().runTaskTimer(owner,
                () -> List.copyOf(handles).forEach(TextHandle::refreshViewers), 5L, 5L);
    }

    public TextHandle create(Location location, List<String> text) {
        requireMainThread();
        if (closed) throw new IllegalStateException("Hologram service is closed");
        Objects.requireNonNull(location.getWorld(), "hologram world");
        TextHologramData data = new TextHologramData("thepit_" + UUID.randomUUID(), location.clone());
        data.setText(format(text));
        data.setPersistent(false);
        data.setVisibility(Visibility.MANUAL);
        data.setVisibilityDistance(64);
        data.setBackground(Color.fromARGB(0));
        data.setTextShadow(true);
        data.setBillboard(Display.Billboard.CENTER);
        TextHandle handle = new TextHandle(data, manager.create(data));
        handles.add(handle);
        return handle;
    }

    static List<String> format(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        return lines.stream().map(line -> MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacySection().deserialize(
                        Objects.requireNonNullElse(line, "").replaceAll("(?i)&([0-9a-fk-orx])", "§$1"))))
                .toList();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID viewer = event.getPlayer().getUniqueId();
        for (TextHandle handle : handles) {
            handle.viewers.forget(viewer);
            handle.shown.remove(viewer);
            Visibility.ManualVisibility.removeDistantViewer(handle.hologram, viewer);
        }
    }

    @Override
    public void close() {
        requireMainThread();
        if (closed) return;
        closed = true;
        visibilityTask.cancel();
        for (TextHandle handle : List.copyOf(handles)) handle.close();
        HandlerList.unregisterAll(this);
    }

    public final class TextHandle implements AutoCloseable {
        private final TextHologramData data;
        private final Hologram hologram;
        private final ViewerPolicy viewers = new ViewerPolicy();
        private final Set<UUID> shown = new HashSet<>();
        private boolean deleted;

        private TextHandle(TextHologramData data, Hologram hologram) {
            this.data = data;
            this.hologram = hologram;
        }

        public void setText(List<String> lines) {
            requireMainThread();
            if (deleted) return;
            List<String> formatted = format(lines);
            if (data.getText().equals(formatted)) return;
            data.setText(formatted);
            hologram.forceUpdate();
            hologram.refreshForViewersInWorld();
        }

        public void setLocation(Location location) {
            requireMainThread();
            if (deleted) return;
            Objects.requireNonNull(location.getWorld(), "hologram world");
            if (data.getLocation().equals(location)) return;
            // Hide before changing worlds so the old world's client entity is removed.
            if (!data.getLocation().getWorld().equals(location.getWorld())) hideShown();
            data.setLocation(location.clone());
            hologram.forceUpdate();
            hologram.refreshForViewersInWorld();
            refreshViewers();
        }

        public Location location() { return data.getLocation().clone(); }

        public void showAll() {
            requireMainThread();
            if (deleted) return;
            viewers.showAll();
            refreshViewers();
        }

        public void showOnly(Collection<? extends Player> players) {
            requireMainThread();
            if (deleted) return;
            viewers.showOnly(players.stream().map(Player::getUniqueId).toList());
            refreshViewers();
        }

        public void show(Player player) {
            requireMainThread();
            if (deleted) return;
            viewers.show(player.getUniqueId());
            refreshViewers();
        }

        public void hide(Player player) {
            requireMainThread();
            if (deleted) return;
            viewers.hide(player.getUniqueId());
            refreshViewers();
        }

        public Set<UUID> shownViewers() { return Set.copyOf(shown); }
        public boolean isDeleted() { return deleted; }

        public void refreshViewers() {
            requireMainThread();
            if (deleted) return;
            Location location = data.getLocation();
            double rangeSquared = (double) data.getVisibilityDistance() * data.getVisibilityDistance();
            Collection<? extends Player> candidates = viewers.isPublic() ? Bukkit.getOnlinePlayers()
                    : viewers.privateCandidates(shown).stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
            for (Player player : candidates) {
                UUID id = player.getUniqueId();
                boolean visible = viewers.accepts(id) && player.getWorld().equals(location.getWorld())
                        && player.getLocation().distanceSquared(location) <= rangeSquared;
                if (visible && shown.add(id)) {
                    Visibility.ManualVisibility.addDistantViewer(hologram, id);
                    hologram.forceShowHologram(player);
                } else if (!visible && shown.remove(id)) {
                    Visibility.ManualVisibility.removeDistantViewer(hologram, id);
                    hologram.forceHideHologram(player);
                }
            }
        }

        private void hideShown() {
            for (UUID id : new ArrayList<>(shown)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) hologram.forceHideHologram(player);
                Visibility.ManualVisibility.removeDistantViewer(hologram, id);
            }
            shown.clear();
        }

        @Override
        public void close() {
            requireMainThread();
            if (deleted) return;
            deleted = true;
            hideShown();
            viewers.clear();
            Visibility.ManualVisibility.remove(hologram);
            hologram.deleteHologram();
            handles.remove(this);
        }
    }

    static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Paper display mutations require the main thread");
    }
}
