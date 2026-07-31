package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MiningMacro {
    private static MiningMacro instance;
    private boolean isMining = false;
    
    private int toolSlot = 0;
    private final List<String> targetBlocks = new ArrayList<>();
    private BlockPos currentTarget = null;
    
    // Humanization Variables
    private long delayEndTime = 0;
    private final Random random = new Random();

    private MiningMacro() {}

    public static MiningMacro getInstance() {
        if (instance == null) instance = new MiningMacro();
        return instance;
    }

    public void addTargetBlock(String blockName) {
        targetBlocks.add(blockName.toLowerCase());
    }

    public void clearTargetBlocks() {
        targetBlocks.clear();
        currentTarget = null;
    }

    public void toggle(Minecraft client) {
        if (!isMining) {
            equipSlot(client, toolSlot);
            isMining = true;
            delayEndTime = 0;
            sendMessage(client, "§a[MobileMiner] Layer 1 Activated. Humanized Mode ON.");
        } else {
            isMining = false;
            client.options.keyAttack.setDown(false);
            currentTarget = null;
            delayEndTime = 0;
            sendMessage(client, "§c[MobileMiner] Stopped.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null) return;
        if (isMining) handleMining(client);
    }

    private void handleMining(Minecraft client) {
        // 1. Check if the block broke or if we need a new target
        if (currentTarget == null || !isValidTarget(client, currentTarget)) {
            
            // Start the Ping Delay (50ms - 130ms)
            if (delayEndTime == 0) {
                delayEndTime = System.currentTimeMillis() + 50 + random.nextInt(81);
            }
            
            // Still delayed? Wait, but keep holding click!
            if (System.currentTimeMillis() < delayEndTime) return;
            
            // Delay over, scan for the next best block
            delayEndTime = 0; 
            currentTarget = scanForBestBlock(client, 4);
            
            if (currentTarget == null) {
                // Completely out of ores. Let go of the mouse.
                client.options.keyAttack.setDown(false);
                return;
            }
        }

        // 2. We have a target!
        if (currentTarget != null) {
            // Clamp down on left click natively
            client.options.keyAttack.setDown(true);

            // Calculate exact math to the center of the block
            double targetX = currentTarget.getX() + 0.5;
            double targetY = currentTarget.getY() + 0.5;
            double targetZ = currentTarget.getZ() + 0.5;

            double dx = targetX - client.player.getX();
            double dy = targetY - client.player.getEyeY();
            double dz = targetZ - client.player.getZ();
            
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));

            float currentYaw = client.player.getYRot();
            float currentPitch = client.player.getXRot();
            
            float yawDiff = targetYaw - currentYaw;
            while (yawDiff < -180.0f) yawDiff += 360.0f;
            while (yawDiff > 180.0f) yawDiff -= 360.0f;
            
            float pitchDiff = targetPitch - currentPitch;

            // Check if we are touching the block
            boolean isLookingAtTarget = false;
            if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) client.hitResult;
                if (blockHit.getBlockPos().equals(currentTarget)) {
                    isLookingAtTarget = true;
                }
            }

            // THE "GOLDEN RATIO" AIM FIX
            // Only stop moving the camera if we are hitting the block AND we are safely inside the "meat" of it (within 8 degrees of center)
            if (isLookingAtTarget && Math.abs(yawDiff) < 8.0f && Math.abs(pitchDiff) < 8.0f) {
                return; // Stop dragging. Perfect aim achieved.
            }

            // Smooth linear drag to the Golden Ratio
            float speed = 0.22f; 
            float stepYaw = yawDiff * speed;
            float stepPitch = pitchDiff * speed;

            // Watchdog GCD Math
            double sensitivity = client.options.sensitivity().get();
            float f = (float) (sensitivity * 0.6 + 0.2);
            float gcd = f * f * f * 8.0f;
            float stepMult = gcd * 0.15f;

            int mouseDeltaX = Math.round(stepYaw / stepMult);
            int mouseDeltaY = Math.round(stepPitch / stepMult);

            if (mouseDeltaX == 0 && Math.abs(yawDiff) > stepMult) mouseDeltaX = (int) Math.signum(yawDiff);
            if (mouseDeltaY == 0 && Math.abs(pitchDiff) > stepMult) mouseDeltaY = (int) Math.signum(pitchDiff);

            client.player.setYRot(currentYaw + ((float) mouseDeltaX * stepMult));
            client.player.setXRot(currentPitch + ((float) mouseDeltaY * stepMult));
        }
    }

    private boolean isValidTarget(Minecraft client, BlockPos pos) {
        String blockName = client.level.getBlockState(pos).getBlock().getDescriptionId().toLowerCase();
        for (String target : targetBlocks) {
            if (blockName.contains(target)) return true;
        }
        return false;
    }

    // THE LINE OF SIGHT CHECK
    private boolean hasLineOfSight(Minecraft client, BlockPos pos) {
        Vec3 eyePos = new Vec3(client.player.getX(), client.player.getEyeY(), client.player.getZ());
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        ClipContext context = new ClipContext(eyePos, blockCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player);
        BlockHitResult result = client.level.clip(context);
        return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos);
    }

    private BlockPos scanForBestBlock(Minecraft client, int radius) {
        BlockPos playerPos = client.player.blockPosition();
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    
                    if (isValidTarget(client, pos) && hasLineOfSight(client, pos)) {
                        double dist = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        
                        // THE FOOT-FETISH FIX: Add a massive penalty to blocks below the waist
                        if (pos.getY() < client.player.getY()) {
                            dist += 20.0; // Pushes lower blocks to the bottom of the priority list
                        }

                        if (dist < bestScore) {
                            bestScore = dist;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    public void setToolSlot(int slot) { this.toolSlot = slot; }

    private void equipSlot(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) return;
        Inventory inv = client.player.getInventory();
        try {
            Field selectedField = Inventory.class.getDeclaredField("selected");
            selectedField.setAccessible(true);
            selectedField.setInt(inv, slot);
        } catch (Exception e) {}
    }

    private void sendMessage(Minecraft client, String text) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
