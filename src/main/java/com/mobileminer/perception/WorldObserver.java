package com.mobileminer.perception;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WorldObserver {
    private WorldSnapshot lastSnapshot = new WorldSnapshot(null, 0);

    public WorldSnapshot getSnapshot(Minecraft client, String targetId, int radius) {
        if (client.player == null || client.level == null) {
            return lastSnapshot;
        }

        // Scan every 10 ticks instead of every tick.
        if (client.player.tickCount % 10 != 0) {
            return lastSnapshot;
        }

        BlockPos playerPos = client.player.blockPosition();
        Vec3 eyePos = client.player.getEyePosition();

        BlockObservation closest = null;
        double minDistance = Double.MAX_VALUE;

        String normalizedTargetId = targetId.toLowerCase();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockState state = client.level.getBlockState(pos);

                    String blockId = BuiltInRegistries.BLOCK
                            .getKey(state.getBlock())
                            .getPath();

                    if (!blockId.equals(normalizedTargetId)) {
                        continue;
                    }

                    double distance = Math.sqrt(
                            pos.distToCenterSqr(
                                    eyePos.x,
                                    eyePos.y,
                                    eyePos.z
                            )
                    );

                    // Already have a closer candidate.
                    if (distance >= minDistance) {
                        continue;
                    }

                    // Check actual line of sight to this candidate.
                    Vec3 targetCenter = Vec3.atCenterOf(pos);

                    BlockHitResult hit = client.level.clip(
                            new ClipContext(
                                    eyePos,
                                    targetCenter,
                                    ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE,
                                    client.player
                            )
                    );

                    boolean isVisible =
                            hit.getType() == HitResult.Type.BLOCK
                            && hit.getBlockPos().equals(pos);

                    // Perception only selects visible targets.
                    if (!isVisible) {
                        continue;
                    }

                    minDistance = distance;

                    closest = new BlockObservation(
                            pos,
                            blockId,
                            distance,
                            true
                    );
                }
            }
        }

        lastSnapshot = new WorldSnapshot(
                closest,
                client.player.tickCount
        );

        return lastSnapshot;
    }
}
