package net.mizukilab.pit.handlers;

import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoldDropLocationSelectorTest {
    private MockedStatic<Bukkit> bukkit;
    private final World world = mock(World.class);
    private final WorldBorder border = mock(WorldBorder.class);
    private final PitWorldConfig config = mock(PitWorldConfig.class);
    private final Map<Integer, Block> terrain = new HashMap<>();
    private final Random random = new Random(9713);

    @Before public void setUp() {
        bukkit = PaperTestBootstrap.mockBukkit();
        bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);
        when(config.getWorldName()).thenReturn("arena");
        when(config.getGoldDropCenterX()).thenReturn(Double.NaN);
        when(config.getGoldDropCenterZ()).thenReturn(Double.NaN);
        when(config.getGoldDropRadius()).thenReturn(3);
        when(config.getGoldDropMinY()).thenReturn(-64);
        when(config.getArenaHighestY()).thenReturn(102);
        // Reversed safe-zone corners, offset far away from the default world's origin.
        when(config.getPitLocA()).thenReturn(new Location(world, 1002, 125, -1998));
        when(config.getPitLocB()).thenReturn(new Location(world, 998, 110, -2002));
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt()))
                .thenAnswer(call -> terrain.getOrDefault(call.getArgument(1), air));
        surface(70, Material.STONE, 1);
    }

    @After public void close() { bukkit.close(); }

    private void surface(int y, Material type, double height) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.isLiquid()).thenReturn(type == Material.WATER || type == Material.LAVA);
        when(block.getBoundingBox()).thenReturn(new BoundingBox(0, y, 0, 1, y + height, 1));
        terrain.put(y, block);
    }

    @Test public void usesSelectedWorldAndMapCenterBelowLobbyRoofs() {
        surface(114, Material.STONE, 1);
        for (int i = 0; i < 100; i++) {
            Location location = GoldDropLocationSelector.find(config, random);
            assertNotNull(location);
            assertSame(world, location.getWorld());
            assertTrue(location.getX() >= 997 && location.getX() <= 1003);
            assertTrue(location.getZ() >= -2003 && location.getZ() <= -1997);
            assertEquals(71.05, location.getY(), 0.0001);
            assertEquals(0.5, location.getX() - location.getBlockX(), 0.0001);
            assertEquals(0.5, location.getZ() - location.getBlockZ(), 0.0001);
        }
        bukkit.verify(Bukkit::getWorlds, never());
        verify(world, never()).getHighestBlockAt(anyInt(), anyInt());
    }

    @Test public void supportsExplicitOffsetsAndPartialBlockSurfaces() {
        when(config.getGoldDropCenterX()).thenReturn(-4500D);
        when(config.getGoldDropCenterZ()).thenReturn(2500D);
        surface(70, Material.STONE_SLAB, 0.5);
        Location location = GoldDropLocationSelector.find(config, random);
        assertNotNull(location);
        assertTrue(location.getX() >= -4503 && location.getX() <= -4497);
        assertTrue(location.getZ() >= 2497 && location.getZ() <= 2503);
        assertEquals(70.55, location.getY(), 0.0001);
    }

    @Test public void rejectsSpawnSafeZoneWorldBorderAndMissingWorld() {
        when(config.getPitLocA()).thenReturn(new Location(world, 1010, 100, -1990));
        when(config.getPitLocB()).thenReturn(new Location(world, 990, 60, -2010));
        assertNull(GoldDropLocationSelector.find(config, random));
        when(config.getGoldDropCenterX()).thenReturn(0D);
        when(config.getGoldDropCenterZ()).thenReturn(0D);
        when(border.isInside(any(Location.class))).thenReturn(false);
        assertNull(GoldDropLocationSelector.find(config, random));
        bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(null);
        assertNull(GoldDropLocationSelector.find(config, random));
    }

    @Test public void rejectsHazardsWaterAndVoidWithoutLookingForAnUndergroundFallback() {
        for (Material type : new Material[]{Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE,
                Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.WATER, Material.LAVA}) {
            surface(75, type, 1);
            assertNull(type.name(), GoldDropLocationSelector.find(config, random));
        }
        terrain.clear();
        assertNull(GoldDropLocationSelector.find(config, random));
    }

    @Test public void respectsMinimumYAndNegativeModernWorldHeights() {
        when(config.getGoldDropMinY()).thenReturn(80);
        assertNull(GoldDropLocationSelector.find(config, random));
        when(config.getGoldDropMinY()).thenReturn(-64);
        terrain.clear();
        surface(-40, Material.STONE, 1);
        Location location = GoldDropLocationSelector.find(config, random);
        assertNotNull(location);
        assertEquals(-38.95, location.getY(), 0.0001);
    }

    @Test public void skipsUnloadedChunksWithoutReadingBlocksOrForcingWorldGeneration() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        clearInvocations(world);
        assertNull(GoldDropLocationSelector.find(config, random));
        verify(world, times(32)).isChunkLoaded(anyInt(), anyInt());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test public void rejectsInvalidRangesAndInsufficientClearance() {
        when(config.getGoldDropRadius()).thenReturn(0);
        assertNull(GoldDropLocationSelector.find(config, random));
        when(config.getGoldDropRadius()).thenReturn(3);
        when(config.getArenaHighestY()).thenReturn(-100);
        assertNull(GoldDropLocationSelector.find(config, random));
        when(config.getArenaHighestY()).thenReturn(102);
        surface(71, Material.SWEET_BERRY_BUSH, 1);
        assertNull(GoldDropLocationSelector.find(config, random));
    }
}
