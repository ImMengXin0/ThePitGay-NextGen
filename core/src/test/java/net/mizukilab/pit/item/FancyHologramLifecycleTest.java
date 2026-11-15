package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.hologram.*;
import net.mizukilab.pit.platform.paper.FancyTextService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Visibility and lifecycle at the game/Fancy service boundary. */
public class FancyHologramLifecycleTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private FancyTextService texts;
    private BukkitScheduler scheduler;
    private ThePit plugin;
    private World world;
    private Player alice, bob;
    private final List<FancyTextService.TextHandle> displays = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    @Before public void setup() {
        bukkit = net.mizukilab.pit.PaperTestBootstrap.mockBukkit(); pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class); when(plugin.isEnabled()).thenReturn(true);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        texts = mock(FancyTextService.class); when(plugin.getFancyTexts()).thenReturn(texts);
        when(texts.create(any(), anyList())).thenAnswer(call -> {
            var display = mock(FancyTextService.TextHandle.class); displays.add(display); return display;
        });
        scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            pending.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        world = mock(World.class); alice = player(); bob = player();
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(alice, bob));
    }
    private Player player() {
        Player player = mock(Player.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); return player;
    }
    private FancyHologramAdapter hologram() { return new FancyHologramAdapter(new Location(world, 0, 64, 0), "parent"); }
    @After public void close() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); HologramAPI.shutdown(); pit.close(); bukkit.close();
    }
    @Test public void privateLinesKeepIntendedViewerWhileTheyAreOutOfRange() {
        when(alice.getLocation()).thenReturn(new Location(world, 200, 64, 0));
        var parent = hologram(); parent.spawn(List.of(alice)); var child = parent.addLineAbove("child");
        assertTrue(parent.members().isEmpty()); assertTrue(child.members().isEmpty());
        when(alice.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        parent.update(); child.update();
        assertEquals(Set.of(alice), parent.members()); assertEquals(Set.of(alice), child.members());
        for (var display : displays) verify(display, never()).showOnly(argThat(players -> players.contains(bob)));
    }
    @Test public void publicViewerExclusionsPropagateToNewAndExistingLines() {
        var parent = hologram(); var above = parent.addLineAbove("before spawn");
        parent.spawn(); parent.hide(bob); var below = parent.addLineBelow("after hide");
        assertEquals(Set.of(alice), parent.members()); assertEquals(Set.of(alice), above.members());
        assertEquals(Set.of(alice), below.members());
        parent.spawn(List.of(bob));
        assertEquals(Set.of(alice, bob), below.members());
    }
    @Test public void privateTextSwitchClosesPreviousDisplaysAndDespawnClosesEveryLine() {
        var parent = hologram(); parent.spawn(List.of(alice)); parent.addLineBelow("below");
        var shared = displays.getFirst(); parent.addViewHandler((h, player, text) -> text + player.getUniqueId());
        verify(shared).close(); parent.clearViewHandlers(); parent.deSpawn();
        for (var display : displays) verify(display).close();
        assertTrue(parent.isFullyDespawned()); assertTrue(HologramAPI.getHolograms().isEmpty());
        assertThrows(IllegalStateException.class, () -> parent.addLineAbove("late"));
    }
    @Test public void shutdownCompletesDespawnEvenWhenItsScheduledTaskWasCancelled() {
        var parent = hologram(); parent.spawn();
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertTrue(HologramAPI.removeHologram(parent.getLocation(), parent.getText()));
        assertTrue(HologramAPI.getHolograms().contains(parent)); assertFalse(parent.isFullyDespawned());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); HologramAPI.shutdown();
        assertTrue(parent.isFullyDespawned()); for (var display : displays) verify(display).close();
        pending.forEach(Runnable::run); for (var display : displays) verify(display).close();
    }
    @Test public void despawnCancelsExpiryAndCannotBeSpawnedAgain() {
        BukkitTask timeout = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(10L))).thenReturn(timeout);
        var parent = hologram(); parent.spawn(10); parent.deSpawn();
        verify(timeout).cancel(); assertFalse(parent.spawn()); assertTrue(parent.members().isEmpty());
    }
}
