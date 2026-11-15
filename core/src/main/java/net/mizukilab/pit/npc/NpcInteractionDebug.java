package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import cn.mythicland.npclib.api.events.NPCInteractEvent;
import cn.mythicland.npclib.internal.NPCManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Short, administrator-requested traces; observes packets without changing cancellation. */
public final class NpcInteractionDebug {
    private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private static PacketAdapter packets;

    private record Session(long expires, AtomicInteger remaining) { }
    private NpcInteractionDebug() { }

    public static void start(Player player) {
        var plugin = ThePit.getInstance();
        Session session = new Session(System.nanoTime() + TimeUnit.SECONDS.toNanos(60), new AtomicInteger(120));
        sessions.put(player.getUniqueId(), session);
        if (packets == null) {
            packets = new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Client.USE_ENTITY) {
                @Override public void onPacketReceiving(PacketEvent event) {
                    trace(event.getPlayer(), "packet", () -> "entity=" + event.getPacket().getIntegers().readSafely(0)
                            + " cancelled=" + event.isCancelled());
                }
            };
            ProtocolLibrary.getProtocolManager().addPacketListener(packets);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sessions.remove(player.getUniqueId(), session);
            removeObserverIfIdle();
        }, 1200L);
        player.sendMessage("§eNPC 点击诊断已开启 60 秒。请关闭菜单并右键目标 NPC，随后提供后台 [NPC-CLICK] 日志。");
        trace(player, "start", () -> "patch=2026-11-15-menu-trace version=" + plugin.getDescription().getVersion());
        for (AbstractPitNPC model : plugin.getNpcFactory().getPitNpc()) {
            if (!(model.getNpc() instanceof cn.mythicland.npclib.internal.NPCBase npc)) continue;
            trace(player, "state", () -> "npc=" + model.getNpcInternalName() + " entity=" + npc.getEntityId()
                    + " registered=" + (NPCManager.getByEntityId(npc.getEntityId()) == npc)
                    + " visible=" + npc.isShown(player)
                    + " world=" + npc.getWorld().getName()
                    + " distance=" + (player.getWorld().equals(npc.getWorld())
                    ? Math.sqrt(player.getLocation().distanceSquared(npc.getLocation())) : "other-world"));
        }
        traceHandlers(player, "game-listeners", NPCInteractEvent.getHandlerList());
        traceHandlers(player, "fancy-listeners", de.oliver.fancynpcs.api.events.NpcInteractEvent.getHandlerList());
        traceHandlers(player, "paper-listeners", com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent.getHandlerList());
        traceHandlers(player, "inventory-listeners", org.bukkit.event.inventory.InventoryOpenEvent.getHandlerList());
        try {
            // Fancy 2.10 added this event; keep the normal 2.9 API baseline usable.
            Class<?> pre = Class.forName("de.oliver.fancynpcs.api.events.NpcPreInteractEvent", false,
                    de.oliver.fancynpcs.api.FancyNpcsPlugin.class.getClassLoader());
            traceHandlers(player, "fancy-pre-listeners", (org.bukkit.event.HandlerList) pre.getMethod("getHandlerList").invoke(null));
        } catch (ClassNotFoundException olderFancy) {
            trace(player, "fancy-pre-listeners", () -> "not-supported-by-this-Fancy-version");
        } catch (ReflectiveOperationException failure) {
            trace(player, "fancy-pre-listeners", failure::toString);
        }
    }

    private static void traceHandlers(Player player, String stage, org.bukkit.event.HandlerList handlers) {
        trace(player, stage, () -> java.util.Arrays.stream(handlers.getRegisteredListeners())
                .map(listener -> listener.getPlugin().getName() + ":" + listener.getListener().getClass().getName()
                        + ":" + listener.getPriority()).collect(java.util.stream.Collectors.joining(", ")));
    }

    public static void trace(Player player, String stage, Supplier<String> detail) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        Session session = sessions.get(id);
        if (session == null) return;
        if (System.nanoTime() - session.expires >= 0) {
            sessions.remove(id, session);
            return;
        }
        if (session.remaining.getAndDecrement() <= 0) return;
        ThePit.getInstance().getLogger().info("[NPC-CLICK] player=" + player.getName() + " stage=" + stage + " " + detail.get());
    }

    public static void forget(Player player) {
        sessions.remove(player.getUniqueId());
        removeObserverIfIdle();
    }

    private static void removeObserverIfIdle() {
        if (sessions.isEmpty() && packets != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(packets);
            packets = null;
        }
    }

    public static void close() {
        sessions.clear();
        removeObserverIfIdle();
    }
}
