package cn.charlotte.pit.util.hologram;

import org.bukkit.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.Test;
import java.util.List;
import static org.mockito.Mockito.*;

public class HologramChunkTest {
    @Test public void loadingAChunkOnlyRefreshesMatchingHologramsWithoutLoadingTheirChunks() {
        try (var api = mockStatic(HologramAPI.class)) {
            World world = mock(World.class), other = mock(World.class);
            Chunk chunk = mock(Chunk.class);
            when(chunk.getWorld()).thenReturn(world);
            when(chunk.getX()).thenReturn(-2);
            when(chunk.getZ()).thenReturn(3);
            Hologram nearby = hologram(new Location(world, -17, 70, 48));
            Hologram distant = hologram(new Location(world, 512, 70, 512));
            Hologram differentWorld = hologram(new Location(other, -17, 70, 48));
            api.when(HologramAPI::getHolograms).thenReturn(List.of(nearby, distant, differentWorld));
            new HologramListeners().onChunkLoad(new ChunkLoadEvent(chunk, false));
            api.verify(() -> HologramAPI.spawn(eq(nearby), any()), times(1));
            api.verify(() -> HologramAPI.spawn(eq(distant), any()), never());
            api.verify(() -> HologramAPI.spawn(eq(differentWorld), any()), never());
            verify(world, never()).getChunkAt(any(Location.class));
            verify(other, never()).getChunkAt(any(Location.class));
        }
    }
    private static Hologram hologram(Location location) {
        Hologram hologram = mock(Hologram.class);
        when(hologram.isSpawned()).thenReturn(true);
        when(hologram.getLocation()).thenReturn(location);
        return hologram;
    }
}
