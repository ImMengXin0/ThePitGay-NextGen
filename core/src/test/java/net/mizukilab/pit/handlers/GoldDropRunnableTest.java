package net.mizukilab.pit.handlers;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoldDropRunnableTest {
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<GoldDropLocationSelector> locations;
    private final ThePit plugin = mock(ThePit.class);
    private final World world = mock(World.class);
    private final PitWorldConfig config = mock(PitWorldConfig.class);
    private final Location landing = new Location(world, -1500.5, 75.05, 800.5);
    private final List<Item> items = new ArrayList<>();
    private final List<AtomicBoolean> alive = new ArrayList<>();
    private final GoldDropRunnable task = new GoldDropRunnable();

    @Before public void setUp() {
        pit = mockStatic(ThePit.class);
        bukkit = PaperTestBootstrap.mockBukkit();
        locations = mockStatic(GoldDropLocationSelector.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getPitConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        locations.when(() -> GoldDropLocationSelector.find(eq(config), any())).thenReturn(landing);
        when(world.dropItem(eq(landing), any(ItemStack.class), any())).thenAnswer(call -> {
            Item item = mock(Item.class);
            AtomicBoolean valid = new AtomicBoolean(true);
            when(item.isValid()).thenAnswer(ignored -> valid.get());
            doAnswer(ignored -> { valid.set(false); return null; }).when(item).remove();
            ((Consumer<Item>) call.getArgument(2)).accept(item);
            // The World spawn callback must set reward data before ItemSpawnEvent is fired.
            verify(item).setVelocity(new Vector());
            verify(item).setPickupDelay(10);
            verify(item).setMetadata(eq("gold"), argThat((MetadataValue value) ->
                    value.getOwningPlugin() == plugin && value.asInt() >= 3 && value.asInt() <= 5));
            assertEquals(Material.GOLD_INGOT, ((ItemStack) call.getArgument(1)).getType());
            items.add(item);
            alive.add(valid);
            return item;
        });
    }

    @After public void close() {
        locations.close();
        bukkit.close();
        pit.close();
    }

    @Test public void eachCoinLivesTwentySecondsWithoutPeriodicMassDeletion() {
        for (int second = 1; second <= 20; second++) task.run();
        assertEquals(40, items.size());
        assertEquals(40, alive.stream().filter(AtomicBoolean::get).count());
        task.run();
        assertFalse(alive.get(0).get());
        assertFalse(alive.get(1).get());
        assertTrue(alive.get(2).get());
        assertEquals(40, alive.stream().filter(AtomicBoolean::get).count());
        task.run();
        assertFalse(alive.get(2).get());
        assertTrue(alive.get(4).get());
        verify(items.get(0)).removeMetadata("gold", plugin);
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test public void mapSwitchAndShutdownClearTrackedCoinsOnlyAndCleanupIsIdempotent() {
        task.run();
        when(plugin.getPitConfig()).thenReturn(mock(PitWorldConfig.class));
        task.run();
        assertTrue(alive.stream().noneMatch(AtomicBoolean::get));
        when(plugin.getPitConfig()).thenReturn(config);
        task.run();
        assertTrue(alive.get(2).get());
        task.clear();
        task.clear();
        assertTrue(alive.stream().noneMatch(AtomicBoolean::get));
        for (Item item : items) {
            verify(item, times(1)).remove();
            verify(item, times(1)).removeMetadata("gold", plugin);
        }
    }

    @Test public void pickedUpOrExternallyRemovedCoinsAreReleasedOnNextRun() {
        task.run();
        alive.get(0).set(false);
        task.run();
        task.clear();
        verify(items.get(0), never()).remove();
        verify(items.get(0), times(1)).removeMetadata("gold", plugin);
    }

    @Test public void unavailableMapSkipsSpawningAndLimitsWarnings() {
        locations.when(() -> GoldDropLocationSelector.find(eq(config), any())).thenReturn(null);
        for (int second = 1; second <= 60; second++) task.run();
        verify(plugin.getLogger(), times(1)).warning(contains("金锭未生成"));
        task.run();
        verify(plugin.getLogger(), times(2)).warning(contains("金锭未生成"));
        assertTrue(items.isEmpty());
    }
}
