package net.mizukilab.pit.util.worldedit;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

/** Reads the original block through the edit session so solid blocks are retained. */
public final class JustAirBlockPattern implements Pattern {
    private final Extent extent;
    private final BaseBlock block;
    public JustAirBlockPattern(Extent extent, BaseBlock block) { this.extent = extent; this.block = block; }
    public BaseBlock getBlock() { return block; }
    @Override public BaseBlock applyBlock(BlockVector3 position) {
        BaseBlock existing = extent.getFullBlock(position);
        return existing.getBlockType().getMaterial().isAir() ? block : existing;
    }
}
