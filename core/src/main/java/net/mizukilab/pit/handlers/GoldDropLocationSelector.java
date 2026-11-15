package net.mizukilab.pit.handlers;

import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class GoldDropLocationSelector {
    private static final int MAX_ATTEMPTS = 32;
    private static final Set<Material> UNSAFE_GROUND = EnumSet.of(
            Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE);

    private GoldDropLocationSelector() { }

    /** Returns null when no loaded, safe landing column is available; never loads chunks. */
    public static Location find(PitWorldConfig config, RandomGenerator random) {
        if (config == null || config.getWorldName() == null) return null;
        World world = Bukkit.getWorld(config.getWorldName());
        if (world == null) return null;
        Location a = config.getPitLocA(), b = config.getPitLocB();
        double centerX = config.getGoldDropCenterX(), centerZ = config.getGoldDropCenterZ();
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            if (a == null || b == null || a.getWorld() != world || b.getWorld() != world) return null;
            centerX = (a.getX() + b.getX()) / 2;
            centerZ = (a.getZ() + b.getZ()) / 2;
        }
        int radius = config.getGoldDropRadius();
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
                || radius <= 0 || radius > 30000000
                || Math.abs(centerX) + radius > 30000000 || Math.abs(centerZ) + radius > 30000000) return null;
        int minX = (int) Math.ceil(centerX - radius - 0.5), maxX = (int) Math.floor(centerX + radius - 0.5);
        int minZ = (int) Math.ceil(centerZ - radius - 0.5), maxZ = (int) Math.floor(centerZ + radius - 0.5);
        if (config.getArenaHighestY() <= world.getMinHeight() + 1) return null;
        int minY = Math.max(world.getMinHeight(), config.getGoldDropMinY());
        int maxY = Math.min(world.getMaxHeight() - 3, config.getArenaHighestY() - 2);
        if (maxY < minY) return null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = random.nextInt(minX, maxX + 1), z = random.nextInt(minZ, maxZ + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = maxY; y >= minY; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.isLiquid() || ground.getType() == Material.POWDER_SNOW) break;
                if (!ground.getType().isSolid()) continue;
                // Do not look below a hazardous surface or through a solid roof.
                if (UNSAFE_GROUND.contains(ground.getType())) break;
                if (!world.getBlockAt(x, y + 1, z).getType().isAir()
                        || !world.getBlockAt(x, y + 2, z).getType().isAir()) break;
                double surfaceY = ground.getBoundingBox().getMaxY();
                if (surfaceY <= y || surfaceY > y + 1) continue;
                Location location = new Location(world, x + 0.5, surfaceY + 0.05, z + 0.5);
                if (world.getWorldBorder().isInside(location) && !insideSpawn(location, a, b)) return location;
                break;
            }
        }
        return null;
    }

    private static boolean insideSpawn(Location location, Location a, Location b) {
        if (a == null || b == null || a.getWorld() != location.getWorld() || b.getWorld() != location.getWorld()) return false;
        return location.getX() + 0.125 >= Math.min(a.getX(), b.getX())
                && location.getX() - 0.125 <= Math.max(a.getX(), b.getX())
                && location.getY() + 0.25 >= Math.min(a.getY(), b.getY())
                && location.getY() <= Math.max(a.getY(), b.getY())
                && location.getZ() + 0.125 >= Math.min(a.getZ(), b.getZ())
                && location.getZ() - 0.125 <= Math.max(a.getZ(), b.getZ());
    }
}
