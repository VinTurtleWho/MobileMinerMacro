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
    
    // Layer 1: Dynamic Block Memory
    private final List<String> targetBlocks = new ArrayList<>();
    private BlockPos currentTarget = null;

    private MiningMacro() {}

    public static MiningMacro getInstance() {
        if (instance == null) instance = new MiningMacro();
        return instance;
    }

    public void addTargetBlock(String blockName) {
        targetBlocks.add(blockName);
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
            inputController.releaseAll(client);
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
        // 1. If we have no target, or the current target is broken, scan for a new one.
        if (currentTarget == null || !isValidTarget(client, currentTarget)) {
            inputController.setPressed(client.options.keyAttack, false);
            currentTarget = scanForClosestBlock(client, 4); // 4 block radius
        }

        // 2. If we found a valid block, aim and mine it
        if (currentTarget != null) {
            double targetX = currentTarget.getX() + 0.5;
            double targetY = currentTarget.getY() + 0.5;
            double targetZ = currentTarget.getZ() + 0.5;

            aimAt(client, targetX, targetY, targetZ);

            // Jiggle Tolerance: Start swinging if we are looking closely at it
            if (!rotationHandler.isRotating()) {
                inputController.setPressed(client.options.keyAttack, true);
            }
        }
    }

    private boolean isValidTarget(Minecraft client, BlockPos pos) {
        String blockName = client.level.getBlockState(pos).getBlock().toString().toLowerCase();
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

    private void aimAt(Minecraft client, double x, double y, double z) {
        double dx = x - client.player.getX();
        double dy = y - client.player.getEyeY();
        double dz = z - client.player.getZ();
        
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizDist));
        
        rotationHandler.updateTarget(yaw, pitch);
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
