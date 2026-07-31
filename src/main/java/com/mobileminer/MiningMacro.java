package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
            sendMessage(client, "§a[MobileMiner] Layer 1 Activated. Sweaty Mode ON.");
        } else {
            isMining = false;
            client.options.keyAttack.setDown(false); // Finally let go of the mouse
            currentTarget = null;
            delayEndTime = 0;
            sendMessage(client, "§c[MobileMiner] Stopped.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null) return;

        if (isMining) {
            handleMining(client);
        }
    }

    private void handleMining(Minecraft client) {
        // 1. Check if the block just broke or we need a new target
        if (currentTarget == null || !isValidTarget(client, currentTarget)) {
            
            // Start the Sweaty Ping Delay (50ms - 130ms)
            if (delayEndTime == 0) {
                delayEndTime = System.currentTimeMillis() + 50 + random.nextInt(81);
            }
            
            // If we are still delayed, wait! (But DO NOT release left click)
            if (System.currentTimeMillis() < delayEndTime) {
                return; 
            }
            
            // Delay is over, find the next block
            delayEndTime = 0; 
            currentTarget = scanForClosestBlock(client, 4);
            
            if (currentTarget == null) {
                // Vein is wiped clean. Release the Death Grip.
                client.options.keyAttack.setDown(false);
                return;
            }
        }

        // 2. We have a target block!
        if (currentTarget != null) {
            // THE DEATH GRIP: Clamp down on left click natively
            client.options.keyAttack.setDown(true);

            // THE LAZY EDGE CHECK: Use Minecraft's native raytrace to see if our crosshair is touching the hitbox
            boolean isLookingAtTarget = false;
            if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) client.hitResult;
                if (blockHit.getBlockPos().equals(currentTarget)) {
                    isLookingAtTarget = true;
                }
            }

            // If we are NOT touching the block yet, drag towards it smoothly.
            // If we ARE touching it, the camera stops moving completely (Lazy Edge Stop).
            if (!isLookingAtTarget) {
                lazyDragToTarget(client, currentTarget);
            }
        }
    }

    private void lazyDragToTarget(Minecraft client, BlockPos target) {
        double targetX = target.getX() + 0.5;
        double targetY = target.getY() + 0.5;
        double targetZ = target.getZ() + 0.5;

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

        // Smooth Linear Drag (No bouncy spring physics)
        float speed = 0.25f; // Drag speed multiplier - smooth and lazy
        float stepYaw = yawDiff * speed;
        float stepPitch = pitchDiff * speed;

        // Strict GCD Quantization to bypass Watchdog
        double sensitivity = client.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6 + 0.2);
        float gcd = f * f * f * 8.0f;
        float stepMult = gcd * 0.15f;

        int mouseDeltaX = Math.round(stepYaw / stepMult);
        int mouseDeltaY = Math.round(stepPitch / stepMult);

        // Failsafe: Force a 1-pixel move if we are stalling out before hitting the edge
        if (mouseDeltaX == 0 && Math.abs(yawDiff) > stepMult) mouseDeltaX = (int) Math.signum(yawDiff);
        if (mouseDeltaY == 0 && Math.abs(pitchDiff) > stepMult) mouseDeltaY = (int) Math.signum(pitchDiff);

        client.player.setYRot(currentYaw + ((float) mouseDeltaX * stepMult));
        client.player.setXRot(currentPitch + ((float) mouseDeltaY * stepMult));
    }

    private boolean isValidTarget(Minecraft client, BlockPos pos) {
        String blockName = client.level.getBlockState(pos).getBlock().getDescriptionId().toLowerCase();
        for (String target : targetBlocks) {
            if (blockName.contains(target)) return true;
        }
        return false;
    }

    private BlockPos scanForClosestBlock(Minecraft client, int radius) {
        BlockPos playerPos = client.player.blockPosition();
        BlockPos closestPos = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (isValidTarget(client, pos)) {
                        double dist = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestPos = pos;
                        }
                    }
                }
            }
        }
        return closestPos;
    }

    public void setToolSlot(int slot) { this.toolSlot = slot; }

    private void equipSlot(Minecraft client, int slot) {
        if (slot < 0 || slot > 8) return;
        Inventory inv = client.player.getInventory();
        try {
            Field selectedField = Inventory.class.getDeclaredField("selected");
            selectedField.setAccessible(true);
            selectedField.setInt(inv, slot);
        } catch (Exception e) {
            try {
                Field obfuscatedField = Inventory.class.getDeclaredField("field_7545");
                obfuscatedField.setAccessible(true);
                obfuscatedField.setInt(inv, slot);
            } catch (Exception ex) {}
        }
    }

    private void sendMessage(Minecraft client, String text) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
