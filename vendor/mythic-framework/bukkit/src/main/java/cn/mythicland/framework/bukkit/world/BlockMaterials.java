package cn.mythicland.framework.bukkit.world;

import org.bukkit.Material;
import org.bukkit.World;

public final class BlockMaterials {
    private BlockMaterials() {
    }

    public static boolean isSolid(World world, int x, int y, int z) {
        return Material.getMaterial(world.getBlockTypeIdAt(x, y, z)).isSolid();
    }
}
