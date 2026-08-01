package com.mobileminer.perception;

import net.minecraft.core.BlockPos;

public class BlockObservation {
    public final BlockPos pos;
    public final String blockId;
    public final double distance;
    public final boolean isVisible; 

    public BlockObservation(BlockPos pos, String blockId, double distance, boolean isVisible) {
        this.pos = pos; this.blockId = blockId; this.distance = distance; this.isVisible = isVisible;
    }

    @Override
    public String toString() {
        return String.format("%s at [%d, %d, %d] (%.1fm)", blockId, pos.getX(), pos.getY(), pos.getZ(), distance);
    }
}
