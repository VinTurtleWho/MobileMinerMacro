package com.mobileminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

public class MobileMiner implements ModInitializer {
    private boolean oKeyPressed = false;
    private long windowPointer = -1;
    
    // THE SWITCHBOARD: Defaults to mining
    public static String currentMode = "mining"; 

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (windowPointer == -1) {
                windowPointer = getSafeWindowPointer(client);
            }

            if (windowPointer != -1) {
                boolean isOKeyDown = GLFW.glfwGetKey(windowPointer, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
                if (isOKeyDown && !oKeyPressed) {
                    toggleCurrentMode(client);
                }
                oKeyPressed = isOKeyDown;
            }

            // Route the tick to the correct brain
            if (currentMode.equals("mining")) {
                MiningMacro.getInstance().onTick(client);
            } else if (currentMode.equals("combat")) {
                // CombatMacro.getInstance().onTick(client); // We will uncomment this when we build Layer 2!
            }
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.toLowerCase().startsWith("!macro")) {
                handleCommand(Minecraft.getInstance(), message);
                return false;
            }
            return true;
        });
    }

    private void toggleCurrentMode(Minecraft client) {
        if (currentMode.equals("mining")) {
            MiningMacro.getInstance().toggle(client);
        } else if (currentMode.equals("combat")) {
            // CombatMacro.getInstance().toggle(client);
            sendMessage(client, "§e[MobileMiner] Combat Mode toggle recognized! (Coming soon)");
        }
    }

    private long getSafeWindowPointer(Minecraft client) {
        try {
            Object window = client.getWindow();
            for (Field field : window.getClass().getDeclaredFields()) {
                if (field.getType() == long.class) {
                    field.setAccessible(true);
                    return field.getLong(window);
                }
            }
        } catch (Exception e) {}
        return -1;
    }

    private void handleCommand(Minecraft client, String message) {
        if (client.player == null) return;
        String[] parts = message.split(" ");
        
        if (parts.length == 1 || (parts.length == 2 && parts[1].equalsIgnoreCase("toggle"))) {
            toggleCurrentMode(client);
            return;
        }

        if (parts.length >= 2) {
            String cmd = parts[1].toLowerCase();
            
            // THE NEW MODE COMMAND
            if (cmd.equals("mode") && parts.length >= 3) {
                String newMode = parts[2].toLowerCase();
                if (newMode.equals("mining") || newMode.equals("combat")) {
                    currentMode = newMode;
                    sendMessage(client, "§b[MobileMiner] Mode switched to: §l" + currentMode.toUpperCase());
                } else {
                    sendMessage(client, "§c[MobileMiner] Unknown mode. Use 'mining' or 'combat'.");
                }
            }
            else if (cmd.equals("addblock") && parts.length >= 3) {
                MiningMacro.getInstance().addTargetBlock(parts[2].toLowerCase());
                sendMessage(client, "§a[MobileMiner] Added block: " + parts[2]);
            } 
            else if (cmd.equals("addmob") && parts.length >= 3) {
                // CombatMacro.getInstance().addTargetMob(parts[2].toLowerCase());
                sendMessage(client, "§c[MobileMiner] Added mob: " + parts[2] + " (Stored for Layer 2)");
            }
            else if (cmd.equals("clear")) {
                MiningMacro.getInstance().clearTargetBlocks();
                sendMessage(client, "§e[MobileMiner] Cleared all memory.");
            } 
        }
    }

    private void sendMessage(Minecraft client, String text) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
