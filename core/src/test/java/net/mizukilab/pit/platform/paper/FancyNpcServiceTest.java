package net.mizukilab.pit.platform.paper;

import cn.charlotte.pit.ThePit;
import de.oliver.fancynpcs.api.*;
import de.oliver.fancynpcs.v1_21_11.Npc_1_21_11;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.mizukilab.pit.PaperTestBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real Fancy update() and attribute handling, with mocked entities and packet transport. */
public class FancyNpcServiceTest extends net.mizukilab.pit.BukkitDataTestSupport {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<FancyNpcsPlugin> fancyApi;
    private MockedStatic<?> colors;
    private FancyNpcsPlugin fancy;
    private ThePit owner;
    private PluginManager plugins;
    private FancyNpcService service;
    private FancyTextService texts;
    private FancyTextService.TextHandle text;
    private CraftPlayer player;
    private ServerGamePacketListenerImpl connection;
    private World world;
    private Runnable refresh;
    private final List<RenderingNpc> npcs = new ArrayList<>();
    private final List<org.bukkit.event.Listener> listeners = new ArrayList<>();
    @Rule public org.junit.rules.TemporaryFolder files = new org.junit.rules.TemporaryFolder();

    @BeforeClass public static void verifyRuntimeArtifact() throws Exception {
        var actual = java.nio.file.Path.of(Npc_1_21_11.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        System.out.println("Testing FancyNpcs adapter from " + actual);
        String expected = System.getProperty("fancyNpcsTestJar");
        if (expected != null) assertEquals(java.nio.file.Path.of(expected).toRealPath(), actual);
    }

    @Before public void setup() {
        bukkit = PaperTestBootstrap.mockBukkit();
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        plugins = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getPluginManager()).thenReturn(plugins);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        fancyApi = mockStatic(FancyNpcsPlugin.class);
        fancy = mock(FancyNpcsPlugin.class);
        fancyApi.when(FancyNpcsPlugin::get).thenReturn(fancy);
        when(fancy.getNpcManager()).thenReturn(mock(NpcManager.class));
        when(fancy.getAttributeManager()).thenReturn(mock(AttributeManager.class));
        when(fancy.getNpcAdapter()).thenReturn(data -> {
            RenderingNpc npc = new RenderingNpc(data);
            npcs.add(npc);
            return npc;
        });
        mockColorTranslation();
        owner = mock(ThePit.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getServer()).thenReturn(server);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(owner), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskTimer(eq(owner), any(Runnable.class), eq(5L), eq(5L))).thenAnswer(call -> {
            refresh = call.getArgument(1);
            return mock(BukkitTask.class);
        });
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        player = mock(CraftPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 1));
        when(player.getEyeLocation()).thenReturn(new Location(world, 0, 65, 1));
        when(player.isOnline()).thenReturn(true);
        ServerPlayer viewer = mock(ServerPlayer.class);
        connection = mock(ServerGamePacketListenerImpl.class);
        viewer.connection = connection;
        when(player.getHandle()).thenReturn(viewer);
        when(viewer.getBukkitEntity()).thenReturn(player);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        texts = mock(FancyTextService.class);
        text = mock(FancyTextService.TextHandle.class);
        when(texts.create(any(), anyList())).thenReturn(text);
        installEventBus();
        service = new FancyNpcService(owner, texts);
    }

    private void mockColorTranslation() {
        // Fancy 2.10 changed its embedded color library; both versions are test targets.
        try {
            Class<?> legacy = Class.forName("de.oliver.fancynpcs.libs.chatcolorhandler.ModernChatColorHandler");
            colors = mockStatic(legacy, call -> call.getMethod().getName().equals("translate")
                    ? net.kyori.adventure.text.Component.empty() : RETURNS_DEFAULTS.answer(call));
        } catch (ClassNotFoundException oldLibraryAbsent) {
            try {
                Class<?> modern = Class.forName("de.oliver.fancynpcs.libs.chatcolorhandler.paper.PaperColor");
                Object color = mock(modern, call -> call.getMethod().getName().equals("translate")
                        ? net.kyori.adventure.text.Component.empty() : RETURNS_DEFAULTS.answer(call));
                colors = mockStatic(modern, call -> call.getMethod().getName().equals("handler")
                        ? color : RETURNS_DEFAULTS.answer(call));
            } catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
        }
    }

    @After public void cleanup() {
        if (service != null) service.close();
        listeners.forEach(org.bukkit.event.HandlerList::unregisterAll);
        net.mizukilab.pit.util.menu.Menu.currentlyOpenedMenus.remove(player.getUniqueId());
        if (colors != null) colors.close();
        if (fancyApi != null) fancyApi.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void playerRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.PLAYER); }
    @Test public void villagerRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.VILLAGER); }
    @Test public void silverfishRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.SILVERFISH); }

    @Test public void repeatedNativeClicksNeverSendFancyCooldownMessages() {
        AtomicInteger clicks = new AtomicInteger();
        var handle = service.create("click", new Location(world, 0, 64, 0), EntityType.PLAYER,
                ignored -> clicks.incrementAndGet());
        handle.show(player);
        try (var actions = mockStatic(de.oliver.fancynpcs.api.actions.executor.ActionExecutor.class)) {
            var npc = npcs.getFirst();
            npc.interact(player);
            npc.interact(player);
        }
        assertEquals(2, clicks.get());
        verify(player, never()).sendMessage(anyString());
    }

    @Test public void positionalClickReachesTheActualPerkMenuThroughRegisteredListeners() throws Exception {
        assertPerkMenuOpens(true, false, false);
    }

    @Test public void ordinaryFancyClickReachesTheActualPerkMenuThroughRegisteredListeners() throws Exception {
        assertPerkMenuOpens(false, false, false);
    }

    @Test public void cancelledFancyClickCannotReachTheActualPerkMenu() throws Exception {
        assertPerkMenuOpens(false, true, false);
    }

    @Test public void delayedFancyLoadRestoresOrdinaryClickToTheActualPerkMenu() throws Exception {
        assertPerkMenuOpens(false, false, true);
    }

    @Test public void delayedFancyLoadRestoresPositionalClickToTheActualPerkMenu() throws Exception {
        assertPerkMenuOpens(true, false, true);
    }

    private void installEventBus() {
        doAnswer(call -> {
            org.bukkit.event.Listener listener = call.getArgument(0);
            listeners.add(listener);
            for (var method : listener.getClass().getMethods()) {
                var annotation = method.getAnnotation(org.bukkit.event.EventHandler.class);
                if (annotation == null) continue;
                Class<?> eventType = method.getParameterTypes()[0];
                var handlers = (org.bukkit.event.HandlerList) eventType.getMethod("getHandlerList").invoke(null);
                handlers.register(new org.bukkit.plugin.RegisteredListener(listener, (target, event) -> {
                    if (eventType.isInstance(event)) {
                        try { method.invoke(target, event); }
                        catch (ReflectiveOperationException failure) { throw new org.bukkit.event.EventException(failure); }
                    }
                }, annotation.priority(), owner, annotation.ignoreCancelled()));
            }
            return null;
        }).when(plugins).registerEvents(any(), eq(owner));
        doAnswer(call -> {
            org.bukkit.event.Event event = call.getArgument(0);
            for (var listener : event.getHandlers().getRegisteredListeners()) listener.callEvent(event);
            return null;
        }).when(plugins).callEvent(any());
    }

    private de.oliver.fancynpcs.NpcManagerImpl realManager(de.oliver.fancynpcs.FancyNpcs nativePlugin) throws Exception {
        var manager = new de.oliver.fancynpcs.NpcManagerImpl(owner, fancy.getNpcAdapter());
        var configFile = manager.getClass().getDeclaredField("npcConfigFile");
        configFile.setAccessible(true);
        configFile.set(manager, files.newFile());
        when(fancy.getNpcManager()).thenReturn(manager);
        when(nativePlugin.getNpcManagerImpl()).thenReturn(manager);
        return manager;
    }

    private void assertPerkMenuOpens(boolean positional, boolean cancelled, boolean delayedLoad) throws Exception {
        try (var pit = mockStatic(ThePit.class);
             var profiles = mockStatic(cn.charlotte.pit.data.PlayerProfile.class);
             var nativeApi = mockStatic(de.oliver.fancynpcs.FancyNpcs.class);
             var actions = mockStatic(de.oliver.fancynpcs.api.actions.executor.ActionExecutor.class)) {
            var nativePlugin = mock(de.oliver.fancynpcs.FancyNpcs.class);
            nativeApi.when(de.oliver.fancynpcs.FancyNpcs::getInstance).thenReturn(nativePlugin);
            var nativeManager = realManager(nativePlugin);
            plugins.registerEvents(new de.oliver.fancynpcs.listeners.PlayerUseUnknownEntityListener(), owner);
            if (cancelled) plugins.registerEvents(new CancellingListener(), owner);
            pit.when(ThePit::getInstance).thenReturn(owner);
            when(owner.getFancyNpcs()).thenReturn(service);
            var config = mock(net.mizukilab.pit.config.PitWorldConfig.class);
            when(owner.getPitConfig()).thenReturn(config);
            when(config.getPerkNpcLocation()).thenReturn(new Location(world, 0, 64, 0));
            var perks = new cn.charlotte.pit.perk.PerkFactory();
            when(owner.getPerkFactory()).thenReturn(perks);
            for (String name : List.of("XPBoost", "CoinBoost", "MeleeBoost", "ArrowBoost", "DmgReduceBoost", "BuilderBattleBoost", "ElGatoBoost")) {
                var perk = mock(cn.charlotte.pit.perk.AbstractPerk.class);
                when(perk.getInternalPerkName()).thenReturn(name);
                when(perk.getDescription(player)).thenReturn(List.of());
                perks.getPerkMap().put(name, perk);
            }
            var profile = mock(cn.charlotte.pit.data.PlayerProfile.class);
            when(profile.getLevel()).thenReturn(120);
            when(profile.getUnlockedPerkMap()).thenReturn(new java.util.HashMap<>());
            when(profile.getChosePerk()).thenReturn(new java.util.HashMap<>());
            profiles.when(() -> cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
            bukkit.when(() -> Bukkit.createInventory(any(org.bukkit.inventory.InventoryHolder.class), anyInt(), anyString()))
                    .thenAnswer(call -> new org.bukkit.craftbukkit.inventory.CraftInventoryCustom(call.getArgument(0), (int) call.getArgument(1), (String) call.getArgument(2)));
            when(player.openInventory(any(org.bukkit.inventory.Inventory.class))).thenReturn(mock(org.bukkit.inventory.InventoryView.class));
            var factory = new net.mizukilab.pit.npc.NpcFactory();
            when(owner.getNpcFactory()).thenReturn(factory);
            plugins.registerEvents(factory, owner);
            try {
                factory.init(List.of(MenuPerkNpc.class));
                assertEquals(1, factory.getPitNpc().size());
                var npc = npcs.getLast();
                assertSame(npc, nativeManager.getNpc(npc.getEntityId()));
                if (delayedLoad) {
                    // Reproduce Fancy's real startup task: it clears all registrations, including ours.
                    nativeManager.loadNpcs();
                    assertSame(npc, nativeManager.getNpc(npc.getEntityId()));
                    assertTrue(service.isRegistered(npc.getEntityId()));
                    assertEquals(1, nativeManager.getAllNpcs().size());
                }
                plugins.callEvent(new com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent(player,
                        npc.getEntityId(), false, org.bukkit.inventory.EquipmentSlot.HAND,
                        positional ? new org.bukkit.util.Vector(0, 1, 0) : null));
                if (cancelled) {
                    assertNull(net.mizukilab.pit.util.menu.Menu.find(player));
                    verify(player, never()).openInventory(any(org.bukkit.inventory.Inventory.class));
                } else {
                    assertTrue("Click must reach the real perk menu", net.mizukilab.pit.util.menu.Menu.find(player)
                            instanceof net.mizukilab.pit.menu.perk.normal.choose.PerkChooseMenu);
                    verify(player).openInventory(any(org.bukkit.inventory.Inventory.class));
                }
            } finally { factory.shutdown(); }
        }
    }

    @Test public void reloadPreservesOwnedIdsAndViewerPolicyWithoutDuplicatingOrResurrectingNpcs() throws Exception {
        try (var nativeApi = mockStatic(de.oliver.fancynpcs.FancyNpcs.class)) {
            var nativePlugin = mock(de.oliver.fancynpcs.FancyNpcs.class);
            nativeApi.when(de.oliver.fancynpcs.FancyNpcs::getInstance).thenReturn(nativePlugin);
            var manager = realManager(nativePlugin);
            var visible = service.create("visible", new Location(world, 0, 64, 0), EntityType.PLAYER, ignored -> {});
            var hidden = service.create("hidden", new Location(world, 0, 64, 0), EntityType.VILLAGER, ignored -> {});
            var deleted = service.create("deleted", new Location(world, 0, 64, 0), EntityType.SILVERFISH, ignored -> {});
            visible.setText(player, List.of("personal"));
            visible.show(player);
            deleted.close();
            var npc = npcs.getFirst();
            int entityId = visible.entityId();
            manager.loadNpcs();
            manager.loadNpcs();
            assertEquals(2, manager.getAllNpcs().size());
            assertSame(npc, manager.getNpc(entityId));
            assertEquals(1, npc.spawnAttempts);
            manager.reloadNpcs();
            assertEquals(2, manager.getAllNpcs().size());
            assertSame(npc, manager.getNpc(entityId));
            assertTrue(visible.isShown(player));
            assertFalse(hidden.isShown(player));
            assertEquals(2, npc.spawnAttempts);
            verify(texts).create(any(), eq(List.of("personal")));
            service.close();
            manager.loadNpcs();
            assertTrue(manager.getAllNpcs().isEmpty());
            assertFalse(visible.isShown(player));
        }
    }

    @Test public void asynchronousLoadRestoresOnMainThreadAndSkipsAClosedService() throws Exception {
        var pending = new ArrayList<Runnable>();
        when(Bukkit.getScheduler().runTask(eq(owner), any(Runnable.class))).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        service.create("async", new Location(world, 0, 64, 0), EntityType.PLAYER, ignored -> {});
        clearInvocations(fancy.getNpcManager());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        plugins.callEvent(new de.oliver.fancynpcs.api.events.NpcsLoadedEvent());
        verify(fancy.getNpcManager(), never()).registerNpc(any());
        assertEquals(1, pending.size());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        pending.removeFirst().run();
        verify(fancy.getNpcManager()).registerNpc(npcs.getFirst());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        plugins.callEvent(new de.oliver.fancynpcs.api.events.NpcsLoadedEvent());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        service.close();
        clearInvocations(fancy.getNpcManager());
        pending.removeFirst().run();
        verify(fancy.getNpcManager(), never()).registerNpc(any());
    }

    public static final class MenuPerkNpc extends net.mizukilab.pit.npc.type.PerkNPC {
        @Override public void initSkin(cn.mythicland.npclib.api.NPC npc) { }
    }

    public static final class CancellingListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
        public void cancel(de.oliver.fancynpcs.api.events.NpcInteractEvent event) { event.setCancelled(true); }
    }

    private void assertRenders(EntityType type) {
        FancyNpcService.NpcHandle handle = service.create("test", new Location(world, 0, 64, 0), type, ignored -> {});
        handle.setText(player, List.of("menu"));
        handle.show(player);
        assertTrue(handle.isShown(player));
        verify(connection, atLeastOnce()).send(any());
        verify(text).showOnly(List.of(player));
    }

    @Test public void ordinaryAttributesStillApplyAndClearThroughNativeNpcData() {
        AtomicInteger applied = new AtomicInteger();
        FancyNpcService.NpcHandle handle = service.create("test", new Location(world, 0, 64, 0), EntityType.VILLAGER, ignored -> {});
        NpcData data = npcs.getFirst().getData();
        NpcAttribute attribute = new NpcAttribute("profession", List.of("none"), List.of(EntityType.VILLAGER),
                (npc, value) -> { assertEquals("none", value); applied.incrementAndGet(); });
        data.addAttribute(attribute, "none");
        handle.show(player);
        assertEquals(1, applied.get());
        assertEquals("none", data.getAttributes().get(attribute));
        data.clearAttributes();
        handle.hide(player);
        handle.show(player);
        assertEquals(1, applied.get());
        assertTrue(data.getAttributes().isEmpty());
    }

    @Test public void failedSpawnIsCleanedUpAndNextRefreshRetriesBeforeShowingText() {
        FancyNpcService.NpcHandle handle = service.create("test", new Location(world, 0, 64, 0), EntityType.PLAYER, ignored -> {});
        RenderingNpc npc = npcs.getFirst();
        npc.failNextSpawn = true;
        handle.setText(player, List.of("menu"));
        assertThrows(IllegalStateException.class, () -> handle.show(player));
        assertFalse(handle.isShown(player));
        assertFalse(npc.isShownFor(player));
        verify(text, never()).showOnly(any());

        refresh.run();

        assertTrue(handle.isShown(player));
        assertEquals(2, npc.spawnAttempts);
        verify(text).showOnly(List.of(player));
    }

    /** Retains Fancy's actual update(); isolates spawn packets, metadata transport and movement. */
    private final class RenderingNpc extends Npc_1_21_11 {
        private boolean failNextSpawn;
        private int spawnAttempts;
        RenderingNpc(NpcData data) { super(data); }
        @Override public void create() {
            LivingEntity entity;
            if (getData().getType() == EntityType.PLAYER) {
                ServerPlayer human = mock(ServerPlayer.class);
                when(human.getGameProfile()).thenReturn(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TestNpc"));
                when(human.getUUID()).thenReturn(UUID.randomUUID());
                when(human.getEntityData()).thenReturn(mock(SynchedEntityData.class));
                try {
                    var gameMode = ServerPlayer.class.getDeclaredField("gameMode");
                    gameMode.setAccessible(true);
                    gameMode.set(human, mock(ServerPlayerGameMode.class));
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                entity = human;
            } else {
                entity = getData().getType() == EntityType.VILLAGER
                        ? mock(net.minecraft.world.entity.npc.villager.Villager.class)
                        : mock(net.minecraft.world.entity.monster.Silverfish.class);
            }
            when(entity.getStringUUID()).thenReturn(UUID.randomUUID().toString());
            when(entity.getId()).thenReturn(npcs.size() + 1);
            when(entity.getItemBySlot(any())).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
            try {
                var field = Npc_1_21_11.class.getDeclaredField("npc");
                field.setAccessible(true);
                field.set(this, entity);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        @Override public void spawn(Player viewer) {
            spawnAttempts++;
            getIsVisibleForPlayer().put(viewer.getUniqueId(), true);
            if (failNextSpawn) {
                failNextSpawn = false;
                throw new IllegalStateException("update failed after marking NPC visible");
            }
            update(viewer, false);
        }
        @Override public void remove(Player viewer) { getIsVisibleForPlayer().remove(viewer.getUniqueId()); }
        @Override public void removeForAll() {
            for (Player viewer : Bukkit.getOnlinePlayers()) remove(viewer);
        }
        @Override protected void refreshEntityData(Player viewer) { }
        @Override public void move(Player viewer, boolean force) { }
        // Overrides the scheduler hook added in 2.10; 2.9 sends directly.
        protected void runOnPlayerScheduler(Player viewer, Runnable action) { action.run(); }
    }
}
