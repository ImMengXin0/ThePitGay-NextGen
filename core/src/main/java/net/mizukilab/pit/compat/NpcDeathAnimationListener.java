package net.mizukilab.pit.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import net.mizukilab.pit.npc.NpcIdentity;
import cn.mythicland.npclib.internal.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 1.8 client-only cleanup: never cancels damage, death, drops or bot respawning. */
final class NpcDeathAnimationListener extends PacketAdapter implements Listener {
    private static final long RETAIN_NANOS = TimeUnit.SECONDS.toNanos(10);
    private final ProtocolManager protocol;
    // Packet callbacks only consult IDs, including packets sent off the main thread.
    private final ConcurrentHashMap<Integer, TrackedNpc> tracked = new ConcurrentHashMap<>();
    private BukkitTask cleanup;
    private boolean active;

    NpcDeathAnimationListener(Plugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.ENTITY_STATUS,
                PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.NAMED_ENTITY_SPAWN,
                PacketType.Play.Server.SPAWN_ENTITY_LIVING);
        protocol = ProtocolLibrary.getProtocolManager();
    }

    void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, getPlugin());
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) track(entity);
        protocol.addPacketListener(this);
        // Only expire cached IDs; no repeating world/NPC registry scans.
        cleanup = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            long now = System.nanoTime();
            tracked.entrySet().removeIf(entry -> {
                TrackedNpc npc = entry.getValue();
                Entity entity = npc.entity.get();
                if (npc.retireAt == 0 && (entity == null || !entity.isValid())) npc.retireAt = now + RETAIN_NANOS;
                return npc.expired(now);
            });
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) { trackLater(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { trackLater(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { retire(event.getPlayer(), false); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) { track(event.getEntity()); }

    private void trackLater(Entity entity) {
        Bukkit.getScheduler().runTask(getPlugin(), () -> { if (active && entity.isValid()) track(entity); });
    }

    private void track(Entity entity) {
        if (entity instanceof LivingEntity && NpcIdentity.isNpc(entity)) {
            tracked.compute(entity.getEntityId(), (id, existing) -> existing != null && existing.entity.get() == entity
                    ? existing : new TrackedNpc(entity));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        track(event.getEntity());
        retire(event.getEntity(), true);
    }

    private void retire(Entity entity, boolean death) {
        if (entity == null) return;
        TrackedNpc npc = tracked.get(entity.getEntityId());
        if (npc == null) return;
        npc.retireAt = System.nanoTime() + RETAIN_NANOS;
        if (death && npc.dead.compareAndSet(false, true)) {
            PacketContainer destroy = destroyPacket(entity.getEntityId());
            for (Player viewer : entity.getWorld().getPlayers()) {
                if (!tracked.containsKey(viewer.getEntityId())) sendDestroy(viewer, destroy);
            }
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;
        PacketContainer packet = event.getPacket();
        Integer id = packet.getIntegers().readSafely(0);
        if (id == null) return;
        TrackedNpc npc = tracked.get(id);
        if (npc == null && NPCManager.getByEntityId(id) == null) return;

        if (packet.getType() == PacketType.Play.Server.ENTITY_STATUS) {
            if (Byte.valueOf((byte) 3).equals(packet.getBytes().readSafely(0))) {
                event.setCancelled(true);
                sendDestroy(event.getPlayer(), destroyPacket(id));
            }
            return; // Preserve normal hurt/status animations.
        }
        if (npc != null && npc.dead.get()) {
            event.setCancelled(true); // Late spawn/metadata must not resurrect an old corpse.
            return;
        }
        boolean zeroHealth;
        if (packet.getType() == PacketType.Play.Server.ENTITY_METADATA) {
            zeroHealth = hasZeroHealth(packet.getWatchableCollectionModifier().readSafely(0));
        } else {
            WrappedDataWatcher watcher = packet.getDataWatcherModifier().readSafely(0);
            // A decoded/cloned 1.8 spawn carries a metadata list instead of a watcher.
            zeroHealth = hasZeroHealth(watcher != null ? watcher.getWatchableObjects()
                    : packet.getWatchableCollectionModifier().readSafely(0));
        }
        // A 1.8 client can also start death animation from health metadata alone.
        if (zeroHealth) {
            event.setCancelled(true);
            sendDestroy(event.getPlayer(), destroyPacket(id));
        }
    }

    private static boolean hasZeroHealth(List<WrappedWatchableObject> values) {
        if (values == null) return false;
        for (WrappedWatchableObject value : values) {
            if (value != null && value.getIndex() == 6 && value.getValue() instanceof Number
                    && ((Number) value.getValue()).doubleValue() <= 0) return true;
        }
        return false;
    }

    private PacketContainer destroyPacket(int id) {
        PacketContainer packet = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntegerArrays().write(0, new int[]{id});
        return packet;
    }

    private void sendDestroy(Player viewer, PacketContainer packet) {
        // NPCView suppresses ordinary destroy packets during batch hidePlayer(). Bypass
        // that filter without changing Bukkit's persistent player visibility state.
        protocol.sendServerPacket(viewer, packet, false);
    }

    void close() {
        active = false;
        protocol.removePacketListener(this);
        HandlerList.unregisterAll(this);
        if (cleanup != null) cleanup.cancel();
        tracked.clear();
    }

    private static final class TrackedNpc {
        final java.lang.ref.WeakReference<Entity> entity;
        final AtomicBoolean dead = new AtomicBoolean();
        volatile long retireAt;

        TrackedNpc(Entity entity) { this.entity = new java.lang.ref.WeakReference<>(entity); }

        boolean expired(long now) {
            return retireAt != 0 && now - retireAt >= 0;
        }
    }
}
