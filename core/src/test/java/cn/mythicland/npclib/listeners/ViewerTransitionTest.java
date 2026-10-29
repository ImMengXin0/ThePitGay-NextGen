package cn.mythicland.npclib.listeners;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.internal.NPCBase;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ViewerTransitionTest {
    @Test public void teleportUsesDestinationOnTheNextTickAndRespawnDoesNotStartAPollingTask() {
        NPCLib lib = mock(NPCLib.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(lib.getPlugin()).thenReturn(plugin);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        List<Runnable> callbacks = new ArrayList<>();
        List<BukkitTask> tasks = new ArrayList<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1)); BukkitTask task = mock(BukkitTask.class); tasks.add(task); return task;
        });
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        World source = mock(World.class), destination = mock(World.class);
        NPCBase npc = mock(NPCBase.class);
        when(npc.isCreated()).thenReturn(true);
        when(npc.getShown()).thenReturn(Set.of(id));
        when(npc.isLocationChunkLoaded()).thenReturn(true);
        when(npc.inRangeOf(player)).thenReturn(true);
        when(npc.inViewOf(player)).thenReturn(true);
        when(lib.getNpcs()).thenReturn(List.of(npc));
        PlayerListener listener = new PlayerListener(lib);
        listener.onPlayerTeleport(new PlayerTeleportEvent(player, new Location(source, 0, 64, 0),
                new Location(destination, 10, 70, 10)));
        verify(npc, never()).show(any(), anyBoolean());
        callbacks.get(0).run();
        verify(npc).show(player, true);
        listener.onPlayerRespawn(new PlayerRespawnEvent(player, new Location(destination, 10, 70, 10), false));
        listener.onPlayerRespawn(new PlayerRespawnEvent(player, new Location(destination, 11, 70, 10), false));
        verify(tasks.get(1)).cancel();
        assertEquals(3, callbacks.size());
        listener.shutdown();
        verify(tasks.get(2)).cancel();
        verify(scheduler, never()).runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong());
    }

    @Test public void chunkCoordinatesIncludeWorldAndDoNotLoadChunks() {
        World first = mock(World.class), other = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(first);
        when(chunk.getX()).thenReturn(-1); when(chunk.getZ()).thenReturn(2);
        assertTrue(ChunkListener.isSameChunk(new Location(first, -0.5, 80, 32.5), chunk));
        assertFalse(ChunkListener.isSameChunk(new Location(other, -0.5, 80, 32.5), chunk));
        assertFalse(ChunkListener.isSameChunk(new Location(first, 0, 80, 32.5), chunk));
        verify(first, never()).getChunkAt(anyInt(), anyInt());
        verify(other, never()).getChunkAt(anyInt(), anyInt());
    }
}
