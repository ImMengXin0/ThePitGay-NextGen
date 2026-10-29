package cn.mythicland.framework.bukkit.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GridPathfinderTest {
    interface BlockWorld extends World {
        int getBlockTypeIdAt(int x, int y, int z);
    }

    @Test
    void carpetRoutesUseTheGroundBelowAndRemainAtTheCarpetHeight() {
        BlockWorld world = mock(BlockWorld.class);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockTypeIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int y = call.getArgument(1);
            return y < 64 ? Material.STONE.getId() : y == 64 ? Material.CARPET.getId() : Material.AIR.getId();
        });
        GridPathfinder finder = new GridPathfinder();
        Location start = new Location(world, .5, 64 + 1.0 / 16, .5);
        Location goal = new Location(world, 2.5, 64 + 1.0 / 16, .5);
        assertTrue(finder.isWalkable(goal));
        assertTrue(finder.canWalkDirectly(start, goal));
        var path = finder.find(start, goal, 128, .6, (fx, fy, fz, tx, ty, tz) -> true);
        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(point -> point.getBlockY() == 64));
        assertFalse(finder.isWalkable(goal.clone().add(0, 1, 0)));
        when(world.getBlockTypeIdAt(2, 63, 0)).thenReturn(Material.AIR.getId());
        assertFalse(finder.isWalkable(goal));
        assertFalse(finder.canWalkDirectly(start, goal));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void reusableSearchFindsRoutesAndSeesChangedBlocks() {
        BlockWorld world = mock(BlockWorld.class, withSettings().stubOnly());
        when(world.getMaxHeight()).thenReturn(256);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockTypeIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                call.<Integer>getArgument(1) < 64 ? Material.STONE.getId() : Material.AIR.getId());
        GridPathfinder finder = new GridPathfinder();
        Location start = new Location(world, -.5, 64, -.5), goal = new Location(world, -5.5, 64, -5.5);
        assertEquals(5, finder.find(start, goal, 128, .6, (fx, fy, fz, tx, ty, tz) -> true).size());
        assertTrue(finder.find(start, goal, 128, .6, (fx, fy, fz, tx, ty, tz) -> false).isEmpty());
        assertFalse(finder.find(start, goal, 128, .6, (fx, fy, fz, tx, ty, tz) -> true).isEmpty());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertTrue(finder.find(start, goal, 128, .6, (fx, fy, fz, tx, ty, tz) -> true).isEmpty());
    }
}
