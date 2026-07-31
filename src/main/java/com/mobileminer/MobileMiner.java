package com.mobileminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.lang.reflect.Method;

public class MobileMiner implements ModInitializer {
    private boolean oKeyPressed = false;
    private long windowPointer = -1;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Bypass the compiler error to get the raw window memory address
            if (windowPointer == -1) {
                try {
                    Method m = client.getWindow().getClass().getMethod("getWindow");
                    windowPointer = (long) m.invoke(client.getWindow());
                } catch (Exception e) {
                    try {
                        Method m = client.getWindow().getClass().getMethod("getHandle");
                        windowPointer = (long) m.invoke(client.getWindow());
                    } catch (Exception ex) {}
                }
            }

            // The Hardcoded 'O' Key Toggle!
            if (windowPointer != -1) {
                boolean isOKeyDown = GLFW.glfwGetKey(windowPointer, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
                if (isOKeyDown && !oKeyPressed) {
                    MiningMacro.getInstance().toggle(client);
                }
                oKeyPressed = isOKeyDown;
            }

            MiningMacro.getInstance().onTick(client);
        });

        // Responsive Chat Intercept
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.toLowerCase().startsWith("!macro")) {
                handleCommand(Minecraft.getInstance(), message);
                return false; // Hides it from the server
            }
            return true;
        });
    }

    private void handleCommand(Minecraft client, String message) {
        if (client.player == null) return;
        String[] parts = message.split(" ");
        
        // !macro or !macro toggle
        if (parts.length == 1 || (parts.length == 2 && parts[1].equalsIgnoreCase("toggle"))) {
            MiningMacro.getInstance().toggle(client);
            return;
        }

        // Processing commands with visual feedback
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
            else {
                client.player.sendSystemMessage(Component.literal("§c[MobileMiner] Unknown command."));
            }
        }
    }
}
