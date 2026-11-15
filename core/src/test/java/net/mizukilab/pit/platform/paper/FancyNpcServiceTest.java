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
public class FancyNpcServiceTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<FancyNpcsPlugin> fancyApi;
    private MockedStatic<?> colors;
    private FancyNpcsPlugin fancy;
    private FancyNpcService service;
    private FancyTextService texts;
    private FancyTextService.TextHandle text;
    private CraftPlayer player;
    private ServerGamePacketListenerImpl connection;
    private World world;
    private Runnable refresh;
    private final List<RenderingNpc> npcs = new ArrayList<>();

    @BeforeClass public static void verifyRuntimeArtifact() throws Exception {
        var actual = java.nio.file.Path.of(Npc_1_21_11.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        System.out.println("Testing FancyNpcs adapter from " + actual);
        String expected = System.getProperty("fancyNpcsTestJar");
        if (expected != null) assertEquals(java.nio.file.Path.of(expected).toRealPath(), actual);
    }

    @Before public void setup() {
        bukkit = PaperTestBootstrap.mockBukkit();
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        PluginManager plugins = mock(PluginManager.class);
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
        ThePit owner = mock(ThePit.class);
        when(owner.isEnabled()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
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
        if (colors != null) colors.close();
        if (fancyApi != null) fancyApi.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void playerRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.PLAYER); }
    @Test public void villagerRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.VILLAGER); }
    @Test public void silverfishRendersWithoutOptionalAttributeProviders() { assertRenders(EntityType.SILVERFISH); }

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
    private static final class RenderingNpc extends Npc_1_21_11 {
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
        @Override protected void refreshEntityData(Player viewer) { }
        @Override public void move(Player viewer, boolean force) { }
        // Overrides the scheduler hook added in 2.10; 2.9 sends directly.
        protected void runOnPlayerScheduler(Player viewer, Runnable action) { action.run(); }
    }
}
