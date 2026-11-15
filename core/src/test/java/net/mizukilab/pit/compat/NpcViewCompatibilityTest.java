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
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
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

/** Real 1.21.11 packets and ProtocolLib accessors; server/network services are mocked. */
public class NpcViewCompatibilityTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ProtocolLibrary> protocolLibrary;
    private ProtocolManager protocol;
    private Plugin plugin;
    private PluginManager plugins;
    private BukkitScheduler scheduler;
    private BukkitTask cleanup;
    private NpcDeathAnimationListener listener;
    private Player bot, viewer;
    private Player currentBot;
    private World world;
    private final List<PacketContainer> sent = new ArrayList<>();

    @Before public void setup() throws Exception {
        net.mizukilab.pit.PaperTestBootstrap.initialize();
        MinecraftVersion.setCurrentVersion(new MinecraftVersion(1, 21, 11));
        bukkit = net.mizukilab.pit.PaperTestBootstrap.mockBukkit();
        MinecraftReflection.getMinecraftPackage();
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
        currentBot = bot;
        when(bot.hasMetadata("NPC")).thenReturn(true);
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        when(world.getEntities()).thenReturn(List.of(bot, viewer));
        when(protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY)).thenAnswer(call ->
                wrap(PacketType.Play.Server.ENTITY_DESTROY, new ClientboundRemoveEntitiesPacket(0)));
        doAnswer(call -> { sent.add(call.getArgument(1)); return null; })
                .when(protocol).sendServerPacket(eq(viewer), any(PacketContainer.class), eq(false));
        listener = new NpcDeathAnimationListener(plugin);
        listener.start();
    }

    @After public void teardown() {
        if (listener != null) listener.close();
        if (protocolLibrary != null) protocolLibrary.close();
        if (bukkit != null) bukkit.close();
    }

    private Player player(int id) {
        Player player = mock(Player.class);
        when(player.getEntityId()).thenReturn(id);
        when(player.isValid()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private PacketContainer wrap(PacketType type, Object nms) {
        return new PacketContainer(type, nms, new StructureModifier<>(nms.getClass()).withTarget(nms));
    }

    private PacketContainer status(int id, int value) {
        Entity entity = mock(Entity.class); when(entity.getId()).thenReturn(id);
        return wrap(PacketType.Play.Server.ENTITY_STATUS, new ClientboundEntityEventPacket(entity, (byte) value));
    }
    private PacketContainer metadata(int id, float health) {
        return wrap(PacketType.Play.Server.ENTITY_METADATA, new ClientboundSetEntityDataPacket(id,
            List.of(new SynchedEntityData.DataValue<>(LivingEntity.DATA_HEALTH_ID.id(),
                net.minecraft.network.syncher.EntityDataSerializers.FLOAT, health))));
    }
    private PacketContainer spawn(int id, float health, boolean livingMob) {
        return wrap(PacketType.Play.Server.SPAWN_ENTITY, new ClientboundAddEntityPacket(id, UUID.randomUUID(),
            0,64,0,0,0,livingMob ? EntityType.ZOMBIE : EntityType.PLAYER,0,Vec3.ZERO,0));
    }
    private PacketEvent send(PacketContainer packet) {
        PacketEvent event = PacketEvent.fromServer(this, packet, viewer);
        listener.onPacketSending(event);
        return event;
    }

    private PlayerDeathEvent death() {
        PlayerDeathEvent event = new PlayerDeathEvent(bot, mock(org.bukkit.damage.DamageSource.class), new ArrayList<>(), 17, "unchanged");
        listener.onDeath(event);
        return event;
    }

    private void respawn(int id) {
        Player newBot = player(id);
        currentBot = newBot;
        when(newBot.hasMetadata("NPC")).thenReturn(true);
        listener.onDamage(new org.bukkit.event.entity.EntityDamageEvent(newBot,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM, 1));
    }

    @Test public void deathStatusIsReplacedWithImmediateDestroy() {
        assertTrue(send(status(41, 3)).isCancelled());
        assertEquals(1, sent.size());
        assertArrayEquals(new int[]{41}, ((ClientboundRemoveEntitiesPacket) sent.get(0).getHandle()).getEntityIds().toIntArray());
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
        listener.onDeath(new PlayerDeathEvent(viewer, mock(org.bukkit.damage.DamageSource.class), new ArrayList<>(), 0, null));
        assertTrue(sent.isEmpty());
    }

    @Test public void deathEventClearsCorpseOnceWithoutAlteringDeathSettlement() {
        PlayerDeathEvent event = death();
        listener.onDeath(event);
        assertEquals(1, sent.size());
        assertEquals(17, event.getDroppedExp());
        assertEquals("unchanged", event.getDeathMessage());
        verify(bot, never()).remove();
    }

    @Test public void oldCorpsePacketsStayBlockedAfterRespawnWithNewEntityId() {
        death(); respawn(42);
        assertTrue(send(status(41, 3)).isCancelled());
        assertTrue(send(metadata(41, 20)).isCancelled());
        assertTrue(send(spawn(41, 20, false)).isCancelled());
        assertFalse(send(metadata(42, 20)).isCancelled());
        assertFalse(send(status(42, 2)).isCancelled());
        listener.onDeath(new PlayerDeathEvent(currentBot, mock(org.bukkit.damage.DamageSource.class), new ArrayList<>(), 0, null));
        assertTrue(send(spawn(42, 20, false)).isCancelled());
    }

    @Test public void spawningDeadNpcsStaysBlockedAfterDeathEvent() {
        death();
        assertTrue(send(spawn(41, 0, false)).isCancelled());
        assertTrue(send(spawn(41, 0, true)).isCancelled());
    }
    @Test public void unrelatedZeroMetadataDoesNotHideNpc() {
        var packet = new ClientboundSetEntityDataPacket(41, List.of(new SynchedEntityData.DataValue<>(7,
                net.minecraft.network.syncher.EntityDataSerializers.INT, 0)));
        assertFalse(send(wrap(PacketType.Play.Server.ENTITY_METADATA, packet)).isCancelled());
        assertTrue(sent.isEmpty());
    }
    @Test public void packetCallbacksDoNotLookUpWorldEntitiesOrNpcRegistry() {
        clearInvocations(bot, viewer, world);
        send(status(41, 3)); send(metadata(41, 0)); send(metadata(999, 0));
        verifyNoInteractions(bot, world);
        verify(viewer, never()).getWorld();
    }

    @Test public void cancelledViewPacketsAreNotSentAgain() {
        PacketEvent event = PacketEvent.fromServer(this, status(41, 3), viewer);
        event.setCancelled(true); listener.onPacketSending(event);
        assertTrue(sent.isEmpty());
    }

    @Test public void quittingBotRetiresIdWithoutChangingHealthyPackets() {
        listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(bot, (String) null));
        assertFalse(send(metadata(41, 10)).isCancelled());
        assertTrue(sent.isEmpty());
    }

    @Test public void repeatedDeathEventCleansUpOnlyOnce() {
        death(); death();
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
        net.mizukilab.pit.OptionalTestFixtures.require("npcViewJar");
        try (URLClassLoader jar = new URLClassLoader(new URL[]{Path.of(System.getProperty("npcViewJar"))
                .toUri().toURL(), Path.of("../libs/citizens-main-2.0.34-SNAPSHOT.jar").toUri().toURL()},
                getClass().getClassLoader())) {
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
