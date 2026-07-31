package com.mobileminer;

import com.mobileminer.input.InputController;
import com.mobileminer.rotation.RotationHandler;
import com.mobileminer.state.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MiningMacro {
    private static MiningMacro instance;
    private MacroState state = MacroState.IDLE;
    
    private final RotationHandler rotationHandler = new RotationHandler();
    private final InputController inputController = new InputController();

    private int toolSlot = 0;
    private final List<String> targetBlocks = new ArrayList<>();
    private BlockPos currentTarget = null;
    private int debugTimer = 0;

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
        if (state == MacroState.IDLE) {
            equipSlot(client, toolSlot);
            state = MacroState.MINING;
            sendMessage(client, "§a[MobileMiner] Layer 1 Activated. Scanning...");
        } else {
            state = MacroState.IDLE;
            client.options.keyAttack.setDown(false); 
            currentTarget = null;
            sendMessage(client, "§c[MobileMiner] Stopped.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null) return;
        rotationHandler.updateRotation(client);

        if (state == MacroState.MINING) {
            handleMining(client);
        }
    }

    private void handleMining(Minecraft client) {
        if (currentTarget == null || !isValidTarget(client, currentTarget)) {
            client.options.keyAttack.setDown(false); // Stop swinging when block breaks
            currentTarget = scanForClosestBlock(client, 4);
            
            if (currentTarget != null) {
                sendMessage(client, "§d[Debug] Locked onto block at: " + currentTarget.getX() + ", " + currentTarget.getY() + ", " + currentTarget.getZ());
            }
        }

        if (currentTarget != null) {
            double targetX = currentTarget.getX() + 0.5;
            double targetY = currentTarget.getY() + 0.5;
            double targetZ = currentTarget.getZ() + 0.5;

            double dx = targetX - client.player.getX();
            double dy = targetY - client.player.getEyeY();
            double dz = targetZ - client.player.getZ();
            
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));

            rotationHandler.updateTarget(targetYaw, targetPitch);

            // JIGGLE MATH: Check if crosshair is within 10 degrees of target
            float currentYaw = client.player.getYRot();
            float currentPitch = client.player.getXRot();
            
            float yawDiff = Math.abs(currentYaw - targetYaw) % 360.0f;
            if (yawDiff > 180.0f) yawDiff = 360.0f - yawDiff;
            float pitchDiff = Math.abs(currentPitch - targetPitch);

            // If we are looking close enough, SWING!
            if (yawDiff < 10.0f && pitchDiff < 10.0f) {
                client.options.keyAttack.setDown(true); // Native left click hold
                if (debugTimer++ % 20 == 0) {
                    sendMessage(client, "§e[Debug] Crosshair aligned. Swinging pickaxe!");
                }
            } else {
                client.options.keyAttack.setDown(false); // Let go of click while turning
            }
        }
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
