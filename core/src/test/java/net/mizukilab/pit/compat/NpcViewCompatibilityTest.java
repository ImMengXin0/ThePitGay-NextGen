package net.mizukilab.pit.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.*;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real 1.8 packets and ProtocolLib accessors; server/network services are mocked. */
public class NpcViewCompatibilityTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<CitizensAPI> citizens;
    private MockedStatic<ProtocolLibrary> protocolLibrary;
    private ProtocolManager protocol;
    private Plugin plugin;
    private PluginManager plugins;
    private BukkitScheduler scheduler;
    private BukkitTask cleanup;
    private NpcDeathAnimationListener listener;
    private Player bot, viewer;
    private NPC npc;
    private World world;
    private final List<PacketContainer> sent = new ArrayList<>();

    @Before public void setup() throws Exception {
        MinecraftVersion.setCurrentVersion(new MinecraftVersion(1, 8, 8));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(org.bukkit.Server.class, RETURNS_DEEP_STUBS));
        var packages = MinecraftReflection.class.getDeclaredMethod("setMinecraftPackage", String.class, String.class);
        packages.setAccessible(true);
        packages.invoke(null, "net.minecraft.server.v1_8_R3", "org.bukkit.craftbukkit.v1_8_R3");
        citizens = mockStatic(CitizensAPI.class);
        protocolLibrary = mockStatic(ProtocolLibrary.class);
        protocol = mock(ProtocolManager.class);
        protocolLibrary.when(ProtocolLibrary::getProtocolManager).thenReturn(protocol);
        plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("npcview-test"));
        plugins = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        cleanup = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(100L))).thenReturn(cleanup);
        world = mock(World.class);
        bot = player(41); viewer = player(12);
        when(world.getPlayers()).thenReturn(List.of(viewer, bot));
        npc = mock(NPC.class);
        when(npc.isSpawned()).thenReturn(true); when(npc.getEntity()).thenReturn(bot);
        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenAnswer(call -> List.of(npc).iterator());
        citizens.when(CitizensAPI::getNPCRegistries).thenReturn(List.of(registry));
        when(protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY)).thenAnswer(call ->
                wrap(PacketType.Play.Server.ENTITY_DESTROY, new PacketPlayOutEntityDestroy()));
        doAnswer(call -> { sent.add(call.getArgument(1)); return null; })
                .when(protocol).sendServerPacket(eq(viewer), any(PacketContainer.class), eq(false));
        listener = new NpcDeathAnimationListener(plugin);
        listener.start();
    }

    @After public void teardown() {
        if (listener != null) listener.close();
        if (protocolLibrary != null) protocolLibrary.close();
        if (citizens != null) citizens.close();
        if (bukkit != null) bukkit.close();
    }

    private Player player(int id) {
        Player player = mock(Player.class);
        when(player.getEntityId()).thenReturn(id);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private PacketContainer wrap(PacketType type, Object nms) {
        return new PacketContainer(type, nms, new StructureModifier<>(nms.getClass()).withTarget(nms));
    }

    private PacketContainer status(int id, int value) {
        PacketContainer packet = wrap(PacketType.Play.Server.ENTITY_STATUS, new PacketPlayOutEntityStatus());
        packet.getIntegers().write(0, id); packet.getBytes().write(0, (byte) value);
        return packet;
    }

    private DataWatcher health(float health) {
        DataWatcher watcher = new DataWatcher(null);
        watcher.a(6, health);
        return watcher;
    }

    private PacketContainer metadata(int id, float health) {
        return wrap(PacketType.Play.Server.ENTITY_METADATA, new PacketPlayOutEntityMetadata(id, health(health), true));
    }

    private PacketContainer spawn(int id, float health, boolean livingMob) {
        Object nms = livingMob ? new PacketPlayOutSpawnEntityLiving() : new PacketPlayOutNamedEntitySpawn();
        PacketContainer packet = wrap(livingMob ? PacketType.Play.Server.SPAWN_ENTITY_LIVING
                : PacketType.Play.Server.NAMED_ENTITY_SPAWN, nms);
        packet.getIntegers().write(0, id);
        packet.getModifier().withType(DataWatcher.class).write(0, health(health));
        return packet;
    }

    private PacketEvent send(PacketContainer packet) {
        PacketEvent event = PacketEvent.fromServer(this, packet, viewer);
        listener.onPacketSending(event);
        return event;
    }

    private PlayerDeathEvent death() {
        PlayerDeathEvent event = new PlayerDeathEvent(bot, new ArrayList<>(), 17, "unchanged");
        listener.onDeath(event);
        return event;
    }

    private void respawn(int id) {
        Player newBot = player(id);
        when(npc.getEntity()).thenReturn(newBot);
        listener.onSpawn(new NPCSpawnEvent(npc, null, SpawnReason.RESPAWN));
    }

    @Test public void deathStatusIsReplacedWithImmediateDestroy() {
        assertTrue(send(status(41, 3)).isCancelled());
        assertEquals(1, sent.size());
        assertArrayEquals(new int[]{41}, sent.get(0).getIntegerArrays().read(0));
        verify(viewer, never()).hidePlayer(any(Player.class));
    }

    @Test public void zeroHealthMetadataIsRemovedEvenWithoutDeathStatus() {
        assertTrue(send(metadata(41, 0)).isCancelled());
        assertTrue(send(metadata(41, -1)).isCancelled());
        assertEquals(2, sent.size());
    }

    @Test public void livingNpcsKeepHurtAnimationHealthyMetadataAndSpawn() {
        assertFalse(send(status(41, 2)).isCancelled());
        assertFalse(send(metadata(41, 8)).isCancelled());
        assertFalse(send(spawn(41, 20, false)).isCancelled());
        assertTrue(sent.isEmpty());
    }

    @Test public void realPlayersAndUnregisteredEntitiesAreUntouched() {
        for (int id : new int[]{12, 999}) {
            assertFalse(send(status(id, 3)).isCancelled());
            assertFalse(send(metadata(id, 0)).isCancelled());
        }
        listener.onDeath(new PlayerDeathEvent(viewer, new ArrayList<>(), 0, null));
        assertTrue(sent.isEmpty());
    }

    @Test public void deathEventClearsCorpseOnceWithoutAlteringDeathSettlement() {
        PlayerDeathEvent event = death();
        listener.onDespawn(new NPCDespawnEvent(npc, DespawnReason.DEATH));
        assertEquals(1, sent.size());
        assertEquals(17, event.getDroppedExp());
        assertEquals("unchanged", event.getDeathMessage());
        verify(npc, never()).despawn(any(DespawnReason.class));
        verify(npc, never()).spawn(any());
        verify(bot, never()).remove();
    }

    @Test public void oldCorpsePacketsStayBlockedAfterRespawnWithNewEntityId() {
        death(); respawn(42);
        assertTrue(send(status(41, 3)).isCancelled());
        assertTrue(send(metadata(41, 20)).isCancelled());
        assertTrue(send(spawn(41, 20, false)).isCancelled());
        assertFalse(send(metadata(42, 20)).isCancelled());
        assertFalse(send(status(42, 2)).isCancelled());
        listener.onDeath(new PlayerDeathEvent((Player) npc.getEntity(), new ArrayList<>(), 0, null));
        assertTrue(send(spawn(42, 20, false)).isCancelled());
    }

    @Test public void alreadyDeadSpawnPacketsAreNotRendered() {
        assertTrue(send(spawn(41, 0, false)).isCancelled());
        assertTrue(send(spawn(41, 0, true)).isCancelled());
    }

    @Test public void serializedDeadSpawnIsAlsoBlocked() throws Exception {
        // ProtocolLib cloning/other packet listeners can deserialize spawn packets,
        // leaving the metadata list populated instead of the live DataWatcher field.
        for (boolean livingMob : new boolean[]{false, true}) {
            PacketContainer original = spawn(41, 0, livingMob);
            if (!livingMob) original.getUUIDs().write(0, UUID.randomUUID());
            PacketDataSerializer buffer = new PacketDataSerializer(io.netty.buffer.Unpooled.buffer());
            try {
                ((Packet<?>) original.getHandle()).b(buffer);
                Packet<?> decoded = livingMob ? new PacketPlayOutSpawnEntityLiving() : new PacketPlayOutNamedEntitySpawn();
                decoded.a(buffer);
                assertTrue(send(wrap(original.getType(), decoded)).isCancelled());
            } finally {
                buffer.release();
            }
        }
    }

    @Test public void unrelatedZeroMetadataDoesNotHideNpc() {
        DataWatcher watcher = new DataWatcher(null);
        watcher.a(7, 0);
        assertFalse(send(wrap(PacketType.Play.Server.ENTITY_METADATA,
                new PacketPlayOutEntityMetadata(41, watcher, true))).isCancelled());
        assertTrue(sent.isEmpty());
    }

    @Test public void packetCallbacksDoNotLookUpWorldEntitiesOrNpcRegistry() {
        clearInvocations(bot, viewer, world);
        citizens.clearInvocations();
        send(status(41, 3)); send(metadata(41, 0)); send(metadata(999, 0));
        citizens.verifyNoInteractions();
        verifyNoInteractions(bot, world);
        verify(viewer, never()).getWorld();
    }

    @Test public void cancelledViewPacketsAreNotSentAgain() {
        PacketEvent event = PacketEvent.fromServer(this, status(41, 3), viewer);
        event.setCancelled(true); listener.onPacketSending(event);
        assertTrue(sent.isEmpty());
    }

    @Test public void cancelledNormalDespawnDoesNotRemoveLiveNpc() {
        NPCDespawnEvent event = new NPCDespawnEvent(npc, DespawnReason.CHUNK_UNLOAD);
        event.setCancelled(true); listener.onDespawn(event);
        assertFalse(send(metadata(41, 10)).isCancelled());
        assertTrue(sent.isEmpty());
    }

    @Test public void deathDespawnStillCleansUpWhenCitizensIgnoresCancellation() {
        NPCDespawnEvent event = new NPCDespawnEvent(npc, DespawnReason.DEATH);
        event.setCancelled(true); listener.onDespawn(event);
        assertEquals(1, sent.size());
        assertTrue(send(metadata(41, 0)).isCancelled());
    }

    @Test public void retiringIdsArePurgedWithoutRemovingRespawnedNpcs() throws Exception {
        death(); respawn(42);
        Field field = NpcDeathAnimationListener.class.getDeclaredField("tracked");
        field.setAccessible(true);
        Map<?, ?> tracked = (Map<?, ?>) field.get(listener);
        Object old = tracked.get(41);
        Field retireAt = old.getClass().getDeclaredField("retireAt");
        retireAt.setAccessible(true); retireAt.setLong(old, System.nanoTime() - 1);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(100L), eq(100L));
        task.getValue().run();
        assertFalse(tracked.containsKey(41)); assertTrue(tracked.containsKey(42));
    }

    @Test public void actualNpcViewBatchSuppressionCannotCancelDeathCleanup() throws Exception {
        try (URLClassLoader jar = new URLClassLoader(new URL[]{Path.of(System.getProperty("npcViewJar"))
                .toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> main = jar.loadClass("cn.apnf.npcview.Main");
            Class<?> type = jar.loadClass("cn.apnf.npcview.PacketBatcher");
            Object batcher = type.getConstructor(main).newInstance(mock(main));
            ArgumentCaptor<PacketListener> filter = ArgumentCaptor.forClass(PacketListener.class);
            verify(protocol, times(2)).addPacketListener(filter.capture());
            PacketListener npcViewFilter = filter.getAllValues().get(1);
            type.getMethod("beginBatchHide", Player.class, int[].class).invoke(batcher, viewer, new int[]{41});
            PacketContainer ordinaryDestroy = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            ordinaryDestroy.getIntegerArrays().write(0, new int[]{41});
            PacketEvent ordinary = PacketEvent.fromServer(this, ordinaryDestroy, viewer);
            npcViewFilter.onPacketSending(ordinary);
            assertTrue("Actual NPCView filter suppresses ordinary destroy during its batch", ordinary.isCancelled());
            death();
            verify(protocol).sendServerPacket(eq(viewer), any(PacketContainer.class), eq(false));
            verify(protocol, never()).sendServerPacket(eq(viewer), any(PacketContainer.class), eq(true));
        }
    }

    @Test public void optionalBridgeHandlesLateEnableDisableAndEnableAgain() {
        listener.close(); listener = null;
        clearInvocations(protocol);
        NpcViewCompatibility compat = new NpcViewCompatibility(plugin);
        compat.start();
        verify(protocol, never()).addPacketListener(any());
        when(plugins.isPluginEnabled("NPCView")).thenReturn(true);
        when(plugins.isPluginEnabled("Citizens")).thenReturn(true);
        Plugin npcView = mock(Plugin.class); when(npcView.getName()).thenReturn("NPCView");
        compat.onEnable(new PluginEnableEvent(npcView));
        compat.onEnable(new PluginEnableEvent(npcView));
        verify(protocol).addPacketListener(any());
        compat.onDisable(new PluginDisableEvent(npcView));
        verify(protocol).removePacketListener(any());
        compat.onEnable(new PluginEnableEvent(npcView));
        verify(protocol, times(2)).addPacketListener(any());
        compat.close();
        verify(protocol, times(2)).removePacketListener(any());
    }
}
