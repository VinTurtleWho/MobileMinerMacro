package com.mobileminer;

import com.mobileminer.input.InputController;
import com.mobileminer.rotation.RotationHandler;
import com.mobileminer.state.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.lang.reflect.Field;

public class MiningMacro {
    private static MiningMacro instance;
    private MacroState state = MacroState.IDLE;
    
    private final RotationHandler rotationHandler = new RotationHandler();
    private final InputController inputController = new InputController();

    private float defaultYaw = 0.0f;
    private float defaultPitch = 0.0f;
    private int toolSlot = 0;

    private MiningMacro() {}

    public static MiningMacro getInstance() {
        if (instance == null) instance = new MiningMacro();
        return instance;
    }

    public void toggle(Minecraft client) {
        if (state == MacroState.IDLE) {
            state = MacroState.ALIGNING;
            rotationHandler.updateTarget(defaultYaw, defaultPitch);
            sendMessage(client, "§a[MobileMiner] Starting...");
        } else {
            state = MacroState.IDLE;
            inputController.releaseAll(client);
            sendMessage(client, "§c[MobileMiner] Stopped.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null) return;
        rotationHandler.tick(client);

        switch (state) {
            case IDLE:
                break;
            case ALIGNING:
                if (rotationHandler.isFinished()) {
                    equipSlot(client, toolSlot);
                    state = MacroState.MINING;
                    sendMessage(client, "§e[MobileMiner] Aligned. Starting mining sequence.");
                }
                break;
            case MINING:
                handleMining(client);
                break;
            default:
                break;
        }
    }

    private void handleMining(Minecraft client) {
        // TODO: We will build the 3D Block Scanner (Raytracer) here!
        // Right now it just holds left click.
        inputController.setPressed(client.options.keyAttack, true);
    }

    public void setDefaultYaw(float yaw) { this.defaultYaw = yaw; }
    public void setDefaultPitch(float pitch) { this.defaultPitch = pitch; }
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
            } catch (Exception ex) {
                // Failsafe
            }
        }
    }

    private void sendMessage(Minecraft client, String text) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
