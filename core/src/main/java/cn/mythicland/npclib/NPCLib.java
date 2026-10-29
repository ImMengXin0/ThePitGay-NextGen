/* Copyright (c) 2018 Jitse Boonstra. MythicNPCLib packet-NPC integration. */
package cn.mythicland.npclib;

import cn.mythicland.npclib.api.NPC;
import cn.mythicland.npclib.api.PacketNpc;
import cn.mythicland.npclib.api.PacketNpcOptions;
import cn.mythicland.npclib.api.utilities.Logger;
import cn.mythicland.npclib.internal.NPCBase;
import cn.mythicland.npclib.listeners.*;
import cn.mythicland.npclib.nms.v1_8_R3.NPC_v1_8_R3;
import cn.mythicland.npclib.bot.*;
import cn.mythicland.npclib.bot.v1_8_R3.BotEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/** MythicNPCLib embedded by the core; combat/path tasks start only when requested. */
public final class NPCLib implements Listener {
    private final JavaPlugin plugin;
    private final Logger logger = new Logger("MythicNPCLib");
    private final List<Listener> listeners = new ArrayList<>();
    private final Set<NPCBase> ownedNpcs = new LinkedHashSet<>();
    private List<NPCBase> npcSnapshot = List.of();
    private final Set<PacketNpc> automaticNpcs = new LinkedHashSet<>();
    private final PacketListener packetListener;
    private volatile boolean shutdown;
    private double autoHideDistance = 50.0;
    private BotManager botManager;
    private final Set<ExternalRobot> externalRobots = new LinkedHashSet<>();

    public NPCLib(JavaPlugin plugin) { this(plugin, new NPCLibOptions()); }

    public NPCLib(JavaPlugin plugin, NPCLibOptions options) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        var movement = Objects.requireNonNull(options, "options").moveHandling;
        registerListener(this);
        registerListener(new PlayerListener(this));
        registerListener(new ChunkListener(this));
        registerListener(movement.usePme ? new PlayerMoveEventListener(this)
                : new PeriodicMoveListener(this, movement.updateInterval));
        packetListener = new PacketListener();
        packetListener.start(this);
    }

    public JavaPlugin getPlugin() { return plugin; }
    public Logger getLogger() { return logger; }
    public double getAutoHideDistance() { return autoHideDistance; }
    public boolean isShutdown() { return shutdown; }
    public void forgetViewer(Player player) { packetListener.forget(player); }

    public void setAutoHideDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) throw new IllegalArgumentException("Invalid hide distance");
        autoHideDistance = distance;
    }

    /** Also owns NPCs constructed through the legacy NMS constructor. */
    public void registerNPC(NPCBase npc) {
        if (shutdown) throw new IllegalStateException("NPC library is shut down");
        if (ownedNpcs.add(npc)) npcSnapshot = List.copyOf(ownedNpcs);
    }

    public List<NPCBase> getNpcs() { return npcSnapshot; }

    // Binary-compatible entry points used by existing Addon / RepairExTend builds.
    public NPC createNPC() { return createNPC(null); }
    public NPC createNPC(List<String> text) { return new NPC_v1_8_R3(this, text); }

    public PacketNpc createPacketNpc(Location location) { return createPacketNpc(location, options -> { }); }

    public PacketNpc createPacketNpc(Location location, Consumer<PacketNpcOptions> configure) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        PacketNpcOptions options = new PacketNpcOptions();
        Objects.requireNonNull(configure, "configure").accept(options);
        NPCBase npc = options.profession() == null ? new NPC_v1_8_R3(this, options.text())
                : NPC_v1_8_R3.villager(this, options.text(), options.profession());
        try {
            npc.setLocation(location);
            npc.setSkin(options.skin());
            npc.create();
            if (options.autoShow()) {
                automaticNpcs.add(npc);
                for (Player player : plugin.getServer().getOnlinePlayers()) npc.show(player);
            }
            return npc;
        } catch (RuntimeException failure) {
            npc.destroy();
            throw failure;
        }
    }

    public boolean hasAutomaticNpcs() { return !automaticNpcs.isEmpty(); }

    public void showAutomaticNpcs(Player player) {
        if (shutdown) return;
        for (PacketNpc npc : List.copyOf(automaticNpcs)) {
            if (!((NPCBase) npc).getShown().contains(player.getUniqueId())) npc.show(player);
        }
    }

    public void forgetNPC(NPC npc) {
        if (ownedNpcs.remove(npc)) npcSnapshot = List.copyOf(ownedNpcs);
        automaticNpcs.remove(npc);
    }

    public PatrolRobot createPatrolRobot(Location location) { return createPatrolRobot(location, options -> { }); }

    public PatrolRobot createPatrolRobot(Location location, Consumer<PatrolRobotOptions> configure) {
        ensureActive();
        PatrolRobotOptions options = new PatrolRobotOptions(location);
        Objects.requireNonNull(configure, "configure").accept(options);
        if (options.externalControl() != null) {
            ExternalPatrolRobot robot = new ExternalPatrolRobot(BotEntity.create(location, options.externalControl()));
            externalRobots.add(robot);
            return robot;
        }
        return bots().createPatrolRobot(location, options);
    }

    public FighterRobot createFighterRobot(Location location) { return createFighterRobot(location, options -> { }); }

    public FighterRobot createFighterRobot(Location location, Consumer<FighterRobotOptions> configure) {
        ensureActive();
        FighterRobotOptions options = new FighterRobotOptions();
        Objects.requireNonNull(configure, "configure").accept(options);
        if (options.externalControl() != null) {
            ExternalFighterRobot robot = new ExternalFighterRobot(BotEntity.create(location, options.externalControl()));
            externalRobots.add(robot);
            return robot;
        }
        return bots().createFighterRobot(location, options);
    }

    private BotManager bots() {
        ensureActive();
        if (botManager == null) botManager = new BotManager(plugin);
        return botManager;
    }

    private void ensureActive() {
        if (shutdown) throw new IllegalStateException("NPC library is shut down");
    }

    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) shutdown();
    }

    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        packetListener.stop();
        if (botManager != null) { botManager.close(); botManager = null; }
        for (ExternalRobot robot : List.copyOf(externalRobots)) robot.close();
        for (NPCBase npc : getNpcs()) npc.destroy();
        ownedNpcs.clear();
        automaticNpcs.clear();
        for (Listener listener : listeners) {
            if (listener instanceof PeriodicMoveListener periodic) periodic.shutdown();
            if (listener instanceof PlayerListener players) players.shutdown();
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }

    private void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    private abstract class ExternalRobot implements AutoCloseable {
        private final BotEntity entity;
        private boolean closed;
        private ExternalRobot(BotEntity entity) { this.entity = entity; }
        public BotEntity entity() { return entity; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            externalRobots.remove(this);
            entity.destroy();
        }
    }

    private final class ExternalPatrolRobot extends ExternalRobot implements PatrolRobot {
        private ExternalPatrolRobot(BotEntity entity) { super(entity); }
    }
    private final class ExternalFighterRobot extends ExternalRobot implements FighterRobot {
        private ExternalFighterRobot(BotEntity entity) { super(entity); }
    }
}
