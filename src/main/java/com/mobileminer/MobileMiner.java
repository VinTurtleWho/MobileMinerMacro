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

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Mapping-Proof Memory Scanner: Grabs the only 'long' variable inside the Window class
            if (windowPointer == -1) {
                windowPointer = getSafeWindowPointer(client);
            }

            // The Hardcoded 'O' Key 
            if (windowPointer != -1) {
                boolean isOKeyDown = GLFW.glfwGetKey(windowPointer, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
                if (isOKeyDown && !oKeyPressed) {
                    MiningMacro.getInstance().toggle(client);
                }
                oKeyPressed = isOKeyDown;
            }

            MiningMacro.getInstance().onTick(client);
        });

        // Chat Intercept
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.toLowerCase().startsWith("!macro")) {
                handleCommand(Minecraft.getInstance(), message);
                return false;
            }
            return true;
        });
    }

    private long getSafeWindowPointer(Minecraft client) {
        try {
            Object window = client.getWindow();
            // Scans all variables in the Window class. The only 'long' is the GLFW pointer.
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
            MiningMacro.getInstance().toggle(client);
            return;
        }

        if (parts.length >= 2) {
            String cmd = parts[1].toLowerCase();
            if (cmd.equals("addblock") && parts.length >= 3) {
                MiningMacro.getInstance().addTargetBlock(parts[2].toLowerCase());
                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Added block: " + parts[2]));
            } 
            else if (cmd.equals("clearblocks")) {
                MiningMacro.getInstance().clearTargetBlocks();
                client.player.sendSystemMessage(Component.literal("§e[MobileMiner] Cleared all target blocks."));
            } 
            else if (cmd.equals("tool") && parts.length >= 3) {
                MiningMacro.getInstance().setToolSlot(Integer.parseInt(parts[2]) - 1);
                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Tool slot set to " + parts[2]));
            }
        }
    }
}
