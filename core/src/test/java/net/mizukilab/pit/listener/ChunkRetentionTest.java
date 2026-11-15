package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.config.ConfigManager;
import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.*;
import org.junit.Test;

import java.util.*;

import static org.mockito.Mockito.*;

public class ChunkRetentionTest {
    @Test public void startingAndExploringPitWorldNeverRetainsEveryVisitedChunk() throws Exception {
        try (var bukkit = PaperTestBootstrap.mockBukkit(); var pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            ConfigManager config = mock(ConfigManager.class);
            when(plugin.getConfigManager()).thenReturn(config);
            PitWorldConfig map = mock(PitWorldConfig.class);
            when(map.getWorldName()).thenReturn("arena");
            when(config.getPitConfigs()).thenReturn(Map.of(1, map));
            World world = mock(World.class);
            when(world.getName()).thenReturn("arena");
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            Chunk initial = mock(Chunk.class);
            when(world.getLoadedChunks()).thenReturn(new Chunk[]{initial});
            ProtectListener listener = new ProtectListener();
            dispatch(listener, new WorldLoadEvent(world));
            verify(initial, never()).addPluginChunkTicket(any());
            // Each new permanent ticket can load neighbors, recursively firing
            // ChunkLoadEvent. No visited chunk may become a permanent anchor.
            for (int x = -32; x < 32; x++) {
                Chunk chunk = mock(Chunk.class);
                when(chunk.getWorld()).thenReturn(world);
                when(chunk.getX()).thenReturn(x);
                dispatch(listener, new ChunkLoadEvent(chunk, false));
                dispatch(listener, new ChunkUnloadEvent(chunk, true));
                verify(chunk, never()).addPluginChunkTicket(any());
                verify(chunk, never()).setForceLoaded(anyBoolean());
            }
        }
    }

    private static void dispatch(Listener listener, Event event) throws Exception {
        for (var method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(event)) {
                method.setAccessible(true);
                method.invoke(listener, event);
            }
        }
    }
}
