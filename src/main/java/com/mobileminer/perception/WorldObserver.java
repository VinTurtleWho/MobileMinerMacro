package com.mobileminer.perception;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WorldObserver {
    private WorldSnapshot lastSnapshot = new WorldSnapshot(null, 0);

    public WorldSnapshot getSnapshot(Minecraft client, String targetId, int radius) {
        if (client.player == null || client.level == null) return lastSnapshot;
        
        if (client.player.tickCount % 10 != 0) return lastSnapshot;

        BlockPos playerPos = client.player.blockPosition();
        Vec3 eyePos = client.player.getEyePosition();
        BlockObservation closest = null;
        double minDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = playerPos.offset(x, y, z);
                    BlockState state = client.level.getBlockState(p);
                    // Updated to modern Mojmap mappings
                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    
                    if (blockId.equals(targetId.toLowerCase())) {
                        double dist = Math.sqrt(p.distToCenterSqr(eyePos.x, eyePos.y, eyePos.z));
                        if (dist < minDistance) {
                            minDistance = dist;
                            closest = new BlockObservation(p, blockId, dist, false); 
                        }
                    }
                }
            }
        }
        lastSnapshot = new WorldSnapshot(closest, client.player.tickCount);
        return lastSnapshot;
    }
}
