package com.mobileminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class MobileMiner implements ClientModInitializer {
    private boolean oKeyPressed = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Toggle macro with 'O' key
            boolean isOKeyDown = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
            if (isOKeyDown && !oKeyPressed) {
                MiningMacro.getInstance().toggle(client);
            }
            oKeyPressed = isOKeyDown;

            MiningMacro.getInstance().onTick(client);
        });

        // Command Parser
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("!macro ")) {
                handleCommand(message);
                return false; // Don't send to server
            }
            return true;
        });
    }

    private void handleCommand(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 2) return;
        
        try {
            switch (parts[1].toLowerCase()) {
                case "yaw":
                    MiningMacro.getInstance().setDefaultYaw(Float.parseFloat(parts[2]));
                    break;
                case "pitch":
                    MiningMacro.getInstance().setDefaultPitch(Float.parseFloat(parts[2]));
                    break;
                case "tool":
                    MiningMacro.getInstance().setToolSlot(Integer.parseInt(parts[2]) - 1);
                    break;
            }
        } catch (Exception e) {
            // Ignore bad input for now
        }
    }
}
