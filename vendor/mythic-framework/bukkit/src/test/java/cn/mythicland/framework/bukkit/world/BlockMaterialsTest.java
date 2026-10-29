package cn.mythicland.framework.bukkit.world;

import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class BlockMaterialsTest {
    @Test
    void preservesSolidityForEveryMaterialWithoutCreatingBlockWrappers() {
        World world = mock(World.class);
        for (Material material : Material.values()) {
            when(world.getBlockTypeIdAt(-16, 64, 32)).thenReturn(material.getId());
            assertEquals(material.isSolid(), BlockMaterials.isSolid(world, -16, 64, 32));
        }
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
