package cn.mythicland.framework.bukkit.edit;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BlockEditSessionsTest {
    @Test
    void lowerRightCornerFollowsHorizontalFacingAtNegativeBlockCoordinates() {
        World world = mock(World.class);
        Location block = new Location(world, -2, 64, 3);
        float[] directions = {0, 90, 180, 270, -90, 360};
        double[][] corners = {{-2, 3}, {-1, 3}, {-1, 4}, {-2, 4}, {-2, 4}, {-2, 3}};
        for (int i = 0; i < directions.length; i++) {
            Location corner = BlockEditSessions.lowerRightCorner(block, directions[i]);
            assertSame(world, corner.getWorld());
            assertEquals(corners[i][0], corner.getX());
            assertEquals(64, corner.getY());
            assertEquals(corners[i][1], corner.getZ());
        }
        assertEquals(-2, block.getX());
        assertEquals(3, block.getZ());
    }

    @Test
    void interactionAndBreakShareOneSelectionAndUndoClearsOwnedMarkers() {
        TemporaryItemMarkers markers = mock(TemporaryItemMarkers.class);
        Item marker = mock(Item.class);
        when(markers.add(any(), eq(Material.BED))).thenReturn(marker);
        BlockEditSessions sessions = new BlockEditSessions(markers);
        BlockEditSessions.Points<Location> points = sessions.points();
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        World world = mock(World.class);
        Location location = new Location(world, -2, 64, 3);
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(location);
        AtomicInteger selected = new AtomicInteger();
        assertTrue(sessions.claim(id, this));
        sessions.route(id, this, sender -> true, (sender, point) -> {
            selected.incrementAndGet();
            points.put(point, point, point, Material.BED);
        }, sender -> points.undo(), true, points::close);
        PlayerInteractEvent selection = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block, BlockFace.UP);
        selection.setCancelled(true);
        sessions.onInteract(selection);
        assertTrue(selection.isCancelled());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        BlockBreakEvent breaking = new BlockBreakEvent(block, player);
        assertTrue(sessions.handle(breaking));
        assertTrue(sessions.handle(breaking));
        assertTrue(breaking.isCancelled());
        assertEquals(1, selected.get());
        assertEquals(location, points.get(location));
        assertEquals(location, points.lastKey());
        sessions.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, null));
        assertTrue(points.isEmpty());
        assertNull(points.get(location));
        assertNull(points.lastKey());
        assertNull(points.undo());
        verify(markers).remove(marker);
        sessions.release(id, this);
        sessions.onInteract(selection);
        assertEquals(1, selected.get());
        assertTrue(sessions.claim(id, new Object()));
    }
}
