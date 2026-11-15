package cn.mythicland.framework.bukkit.world;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
final class BlockMaterialsTest {
    @Test void detectsSolidityAtNegativeCoordinates() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(-16, 64, 32)).thenReturn(block);
        for (Material material : new Material[]{Material.STONE, Material.AIR, Material.WATER, Material.GLASS}) {
            when(block.isSolid()).thenReturn(material == Material.STONE || material == Material.GLASS);
            assertEquals(material == Material.STONE || material == Material.GLASS, BlockMaterials.isSolid(world, -16, 64, 32));
        }
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
