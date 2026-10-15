package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import cn.charlotte.pit.perk.PerkFactory;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.config.ConfigManager;
import net.mizukilab.pit.config.PitGlobalConfig;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.data.operator.PackedOperator;
import net.mizukilab.pit.data.operator.ProfileOperator;
import net.mizukilab.pit.handlers.PlayerMoveHandler;
import net.mizukilab.pit.impl.PitInternalImpl;
import net.mizukilab.pit.map.MapSelector;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.TitleUtil;
import net.mizukilab.pit.util.configuration.serializer.LocationSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import spg.lgdev.iSpigot;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs initial spawn -> join -> profile-ready callback, plus the actual movement and respawn paths. */
public class PlayerJoinSpawnTest extends BukkitDataTestSupport {
    private abstract static class RespawnPlayer extends CraftPlayer {
        private RespawnPlayer() { super(null, null); }
        @Override public abstract double getMaxHealth();
    }

    private ThePit plugin;
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PitInternalImpl> startup;
    private MockedStatic<PlayerUtil> playerUtil;
    private ConfigManager configs;
    private PitWorldConfig worldConfig;
    private ProfileOperator profiles;
    private PackedOperator operator;
    private PlayerProfile profile;
    private PluginManager plugins;
    private final PlayerMarco listener = new PlayerMarco();
    private final World pitWorld = mock(World.class), logoutWorld = mock(World.class);
    private final AtomicReference<Location> position = new AtomicReference<>();
    private final AtomicBoolean loaded = new AtomicBoolean();
    private final Queue<Consumer<PlayerProfile>> pendingLoads = new ArrayDeque<>();
    private final Queue<Runnable> mainThreadTasks = new ArrayDeque<>();
    private final List<PlayerTeleportEvent> teleports = new ArrayList<>();
    private final UUID uuid = UUID.randomUUID();
    private iSpigot movement, previousMovement;

    @Before
    public void setUpJoin() {
        pit = mockStatic(ThePit.class);
        bukkit = mockStatic(Bukkit.class);
        startup = mockStatic(PitInternalImpl.class);
        // Only the NMS reset is isolated; the teleport event and movement guard run normally.
        playerUtil = mockStatic(PlayerUtil.class);
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        startup.when(PitInternalImpl::getLoaded).thenReturn(true);
        configs = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(configs);
        PitGlobalConfig global = mock(PitGlobalConfig.class);
        when(configs.getGlobal()).thenReturn(global);
        when(global.getStartDate()).thenReturn(-1L);
        worldConfig = mock(PitWorldConfig.class);
        when(configs.getSelectedWorldConfig()).thenReturn(worldConfig);
        MapSelector selector = new MapSelector(plugin);
        when(plugin.getMapSelector()).thenReturn(selector);
        profiles = mock(ProfileOperator.class);
        when(plugin.getProfileOperator()).thenReturn(profiles);
        operator = mock(PackedOperator.class);
        profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenAnswer(call -> loaded.get());
        when(profile.getProfileFormatVersion()).thenReturn(1);
        when(operator.isLoaded()).thenAnswer(call -> loaded.get());
        when(operator.profile()).thenReturn(profile);
        when(profiles.getIOperator(uuid)).thenReturn(operator);
        when(profiles.getOrLoadOperator(any(Player.class))).thenReturn(operator);
        doAnswer(call -> { pendingLoads.add(call.getArgument(0)); return null; })
                .when(operator).pendingUntilLoaded(any());
        plugins = mock(PluginManager.class);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(plugins);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(pitWorld);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        doAnswer(call -> { mainThreadTasks.add(call.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        previousMovement = iSpigot.INSTANCE;
        movement = new iSpigot();
        movement.addMovementHandler(new PlayerMoveHandler());
    }

    @After
    public void closeJoin() {
        iSpigot.INSTANCE = previousMovement;
        if (playerUtil != null) playerUtil.close();
        if (startup != null) startup.close();
        if (bukkit != null) bukkit.close();
        if (pit != null) pit.close();
    }

    private Player reconnectAt(Location savedLocation) {
        return reconnectAt(mock(Player.class), savedLocation);
    }

    private <T extends Player> T reconnectAt(T player, Location savedLocation) {
        loaded.set(false);
        position.set(savedLocation.clone());
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("spawn_tester");
        when(player.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(call -> position.get().clone());
        when(player.getWorld()).thenAnswer(call -> position.get().getWorld());
        doAnswer(call -> {
            PlayerTeleportEvent event = new PlayerTeleportEvent(player, position.get().clone(),
                    ((Location) call.getArgument(0)).clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            teleports.add(event);
            movement.onMove(event);
            if (event.isCancelled()) return false;
            position.set(event.getTo().clone());
            return true;
        }).when(player).teleport(any(Location.class));
        return player;
    }

    private void finishLoading() {
        loaded.set(true);
        pendingLoads.remove().accept(profile);
        while (!mainThreadTasks.isEmpty()) mainThreadTasks.remove().run();
    }

    private PlayerSpawnLocationEvent initialSpawn(Player player) {
        PlayerSpawnLocationEvent event = new PlayerSpawnLocationEvent(player, position.get().clone());
        listener.onPlayerSpawnLocation(event);
        // The server applies the event location directly, before firing PlayerJoinEvent.
        position.set(event.getSpawnLocation().clone());
        return event;
    }

    private List<Location> configuredSpawns() {
        LocationSerializer serializer = new LocationSerializer();
        return List.of("world|9.5|114.0|0.5|90.0|0.0", "world|0.5|114.0|-8.5|0.0|0.0",
                        "world|-8.5|114.0|0.5|-90.0|0.0", "world|0.5|114.0|9.5|180.0|0.0")
                .stream().map(serializer::fromString).toList();
    }

    @Test
    public void initialSpawnIsConfiguredBeforeJoinWithoutAnyTeleportOrProfileLookup() {
        List<Location> spawns = configuredSpawns();
        when(worldConfig.getSpawnLocations()).thenReturn(spawns);
        Player player = reconnectAt(new Location(logoutWorld, 500, 32, 600));
        when(player.isOnline()).thenReturn(false);
        initialSpawn(player);
        assertTrue("The first server position must already be a configured spawn", spawns.contains(position.get()));
        Location firstPosition = position.get().clone();
        verifyNoInteractions(profiles, operator);
        assertTrue(mainThreadTasks.isEmpty());
        verify(player, never()).teleport(any(Location.class));
        when(player.isOnline()).thenReturn(true);
        PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
        listener.onPlayerJoin(event);
        assertEquals(firstPosition, position.get());
        finishLoading();
        assertEquals(firstPosition, position.get());
        assertTrue(teleports.isEmpty());
        verify(player, never()).teleport(any(Location.class));
        assertNull(event.getJoinMessage());
        var order = inOrder(player, profiles, operator, plugins);
        order.verify(profiles).getOrLoadOperator(player);
        order.verify(operator).pendingUntilLoaded(any());
        order.verify(plugins).callEvent(any(PitProfileLoadedEvent.class));
        verify(profile).setLogin(true);
    }

    @Test
    public void loadingStillBlocksWalkingAtTheInitialSpawnWithoutQueuingATeleport() {
        Location spawn = new Location(pitWorld, 12.25, 114, -9.75, 137.5F, -14.25F);
        when(worldConfig.getSpawnLocations()).thenReturn(List.of(spawn));
        Player player = reconnectAt(new Location(logoutWorld, 500, 32, 600));
        PlayerSpawnLocationEvent spawnEvent = initialSpawn(player);
        assertEquals(spawn, position.get());
        assertNotSame("An event listener must not be able to mutate the configured spawn", spawn, spawnEvent.getSpawnLocation());
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));
        PlayerMoveEvent walk = new PlayerMoveEvent(player, position.get().clone(), position.get().clone().add(1, 0, 0));
        movement.onMove(walk);
        assertTrue("Loading movement protection must remain active", walk.isCancelled());
        verify(player, never()).teleport(any(Location.class));
        loaded.set(true);
        pendingLoads.remove().accept(profile);
        verify(player, never()).teleport(any(Location.class));
        mainThreadTasks.remove().run();
        assertEquals(spawn, position.get());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void everyReconnectUsesTheMapSelectedAtInitialSpawn() {
        Location oldSpawn = new Location(pitWorld, 1, 114, 9);
        when(worldConfig.getSpawnLocations()).thenReturn(List.of(oldSpawn));
        Player first = reconnectAt(new Location(logoutWorld, 20, 20, 20));
        initialSpawn(first);
        listener.onPlayerJoin(new PlayerJoinEvent(first, null));
        finishLoading();
        assertEquals(oldSpawn, position.get());
        Player second = reconnectAt(new Location(pitWorld, 300, 40, 400));
        PitWorldConfig nextMap = mock(PitWorldConfig.class);
        List<Location> nextSpawns = configuredSpawns();
        when(nextMap.getSpawnLocations()).thenReturn(nextSpawns);
        when(configs.getSelectedWorldConfig()).thenReturn(nextMap);
        initialSpawn(second);
        assertTrue(nextSpawns.contains(position.get()));
        listener.onPlayerJoin(new PlayerJoinEvent(second, null));
        finishLoading();
        assertTrue(nextSpawns.contains(position.get()));
        assertTrue(teleports.isEmpty());
        verify(first, never()).teleport(any(Location.class));
        verify(second, never()).teleport(any(Location.class));
    }

    @Test
    public void disconnectBeforeTheQueuedTaskPreventsLateTeleport() {
        List<Location> spawns = configuredSpawns();
        when(worldConfig.getSpawnLocations()).thenReturn(spawns);
        Player player = reconnectAt(new Location(logoutWorld, 25, 70, 25));
        initialSpawn(player);
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));
        loaded.set(true);
        pendingLoads.remove().accept(profile);
        when(player.isOnline()).thenReturn(false);
        mainThreadTasks.remove().run();
        verify(player, never()).teleport(any(Location.class));
        verify(profile, never()).setLogin(true);
    }

    @Test
    public void missingSpawnKeepsServerInitialLocationAndStillCompletesLogin() {
        when(worldConfig.getSpawnLocations()).thenReturn(List.of());
        Location saved = new Location(logoutWorld, 25, 70, 25);
        Player player = reconnectAt(saved);
        initialSpawn(player);
        assertEquals(saved, position.get());
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));
        finishLoading();
        verify(player, never()).teleport(any(Location.class));
        verify(profile).setLogin(true);
        assertEquals(saved, position.get());
    }

    @Test
    public void startupRejectionDoesNotTeleportOrBeginLoadingPlayerData() {
        startup.when(PitInternalImpl::getLoaded).thenReturn(false);
        Player player = reconnectAt(new Location(logoutWorld, 25, 70, 25));
        Location saved = position.get().clone();
        initialSpawn(player);
        assertEquals(saved, position.get());
        verify(configs, never()).getSelectedWorldConfig();
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));
        verify(player).kickPlayer("Didn't load completely");
        verify(player, never()).teleport(any(Location.class));
        verifyNoInteractions(profiles, operator);
        playerUtil.verifyNoInteractions();
    }

    @Test
    public void initialSpawnWithAnUnloadedWorldDoesNotGiveTheServerAnInvalidLocation() {
        when(worldConfig.getSpawnLocations()).thenReturn(List.of(new Location(null, 9.5, 114, 0.5)));
        Location saved = new Location(logoutWorld, 25, 70, 25);
        Player player = reconnectAt(saved);
        initialSpawn(player);
        assertEquals(saved, position.get());
        verify(plugin.getLogger()).warning(contains("世界未加载"));
        verifyNoInteractions(profiles, operator);
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void profileCompletionDoesNotPullThePlayerBackToSpawn() {
        List<Location> spawns = configuredSpawns();
        when(worldConfig.getSpawnLocations()).thenReturn(spawns);
        Player player = reconnectAt(new Location(logoutWorld, 500, 32, 600));
        initialSpawn(player);
        listener.onPlayerJoin(new PlayerJoinEvent(player, null));
        // Another feature moves the player after initial placement but before data finishes loading.
        Location next = new Location(pitWorld, 20, 114, 20);
        position.set(next.clone());
        finishLoading();
        assertEquals(next, position.get());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    public void deathRespawnUsesTheConfiguredPoolThroughTheSameMovementGuard() throws Exception {
        List<Location> spawns = configuredSpawns();
        when(worldConfig.getSpawnLocations()).thenReturn(spawns);
        when(plugin.getPitConfig()).thenReturn(worldConfig);
        PerkFactory perks = mock(PerkFactory.class);
        when(plugin.getPerkFactory()).thenReturn(perks);
        RespawnPlayer player = reconnectAt(mock(RespawnPlayer.class), new Location(logoutWorld, 500, 32, 600));
        loaded.set(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getMaxHealth()).thenReturn(24D);
        // Isolate vanilla attribute bootstrap and allocate a handle without starting a server.
        try (var attributes = mockConstruction(AttributeModifier.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<TitleUtil> titles = mockStatic(TitleUtil.class)) {
            Class<?> allocatorType = Class.forName("org.objenesis.ObjenesisStd"); // Mockito runtime dependency
            Object allocator = allocatorType.getConstructor().newInstance();
            EntityPlayer handle = (EntityPlayer) allocatorType.getMethod("newInstance", Class.class)
                    .invoke(allocator, EntityPlayer.class);
            when(player.getHandle()).thenReturn(handle);
            var respawn = CombatListener.class.getDeclaredMethod("doRespawn", Player.class, PlayerProfile.class);
            respawn.setAccessible(true);
            respawn.invoke(mock(CombatListener.class), player, profile);
            assertEquals(40, handle.invulnerableTicks);
        }
        assertTrue(spawns.contains(position.get()));
        assertEquals(1, teleports.size());
        assertFalse(teleports.get(0).isCancelled());
        verify(player).setBedSpawnLocation(position.get(), true);
        verify(player).setHealth(24D);
        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(plugins).callEvent(any(PitPlayerSpawnEvent.class));
    }
}
