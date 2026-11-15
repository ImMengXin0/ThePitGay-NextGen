package net.mizukilab.pit.util.sign;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client sign input with world/session validation and restoration on every exit. */
public class SignGui implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final PacketAdapter packets;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private boolean closed;
    private static final class Session {
        final Location location;
        final SignGUIListener response;
        final AtomicBoolean submitted = new AtomicBoolean();
        BukkitTask timeout;
        Session(Location location, SignGUIListener response) { this.location = location; this.response = response; }
    }
    public SignGui(JavaPlugin plugin) {
        this.plugin = plugin;
        packets = new PacketAdapter(plugin, PacketType.Play.Client.UPDATE_SIGN) {
            @Override public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                Session session = sessions.get(player.getUniqueId());
                if (session == null) return;
                var pos = event.getPacket().getBlockPositionModifier().readSafely(0);
                Location at = session.location;
                if (pos == null || pos.getX() != at.getBlockX() || pos.getY() != at.getBlockY()
                        || pos.getZ() != at.getBlockZ()) return;
                event.setCancelled(true);
                String[] lines = event.getPacket().getStringArrays().readSafely(0);
                if (lines == null || lines.length != 4 || !session.submitted.compareAndSet(false, true)) return;
                String[] copy = lines.clone();
                Runnable finish = () -> {
                    if (!sessions.remove(player.getUniqueId(), session)) return;
                    restore(player, session);
                    if (player.isOnline() && player.getWorld().equals(at.getWorld())) session.response.onSignDone(player, copy);
                };
                if (Bukkit.isPrimaryThread()) finish.run();
                else Bukkit.getScheduler().runTask(plugin, finish);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(packets);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    static String plainText(String json) { return TextComponent.toPlainText(ComponentSerializer.parse(json)); }
    private void restore(Player player, Session session) {
        if (session.timeout != null) session.timeout.cancel();
        if (player.isOnline() && player.getWorld().equals(session.location.getWorld()))
            player.sendBlockChange(session.location, session.location.getBlock().getBlockData());
    }
    public void cancel(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Sign cancellation requires the main thread");
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) restore(player, session);
    }
    public void open(Player player, String[] messages, SignGUIListener response) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Sign input requires the main thread");
        if (closed) throw new IllegalStateException("Sign input is closed");
        if (messages.length != 4) throw new IllegalArgumentException("A sign requires four lines");
        cancel(player);
        Location at = player.getLocation().getBlock().getLocation().add(0, -3, 0);
        at.setY(Math.max(player.getWorld().getMinHeight(), at.getY()));
        Session session = new Session(at, Objects.requireNonNull(response));
        sessions.put(player.getUniqueId(), session);
        try {
            player.sendBlockChange(at, Material.OAK_SIGN.createBlockData());
            player.sendSignChange(at, messages.clone());
            ((CraftPlayer) player).getHandle().connection.send(new ClientboundOpenSignEditorPacket(
                    new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()), true));
            session.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sessions.remove(player.getUniqueId(), session)) restore(player, session);
            }, 1200);
        } catch (RuntimeException failure) {
            cancel(player);
            throw new IllegalStateException("Unable to open sign editor", failure);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { cancel(event.getPlayer()); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) cancel(player);
        }
        sessions.values().forEach(session -> { if (session.timeout != null) session.timeout.cancel(); });
        sessions.clear();
        ProtocolLibrary.getProtocolManager().removePacketListener(packets);
        HandlerList.unregisterAll(this);
    }
    public interface SignGUIListener { void onSignDone(Player player, String[] lines); }
}
