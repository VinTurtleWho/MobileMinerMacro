package com.mobileminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MobileMiner implements ModInitializer {
    private boolean oKeyPressed = false;
    private long windowPointer = -1;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Fetch the window pointer safely using reflection
            if (windowPointer == -1) {
                windowPointer = getWindowPointer(client);
            }

            // Check the 'O' key natively via GLFW
            if (windowPointer != -1) {
                boolean isOKeyDown = GLFW.glfwGetKey(windowPointer, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
                if (isOKeyDown && !oKeyPressed) {
                    MiningMacro.getInstance().toggle(client);
                }
                oKeyPressed = isOKeyDown;
            }

            MiningMacro.getInstance().onTick(client);
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("!macro ")) {
                handleCommand(message);
                return false;
            }
            return true;
        });
    }

    private long getWindowPointer(Minecraft client) {
        Object window = client.getWindow();
        try {
            // Try standard Mojmap (getWindow)
            Method m = window.getClass().getMethod("getWindow");
            return (long) m.invoke(window);
        } catch (Exception e1) {
            try {
                // Try Fabric Yarn (getHandle)
                Method m = window.getClass().getMethod("getHandle");
                return (long) m.invoke(window);
            } catch (Exception e2) {
                try {
                    // Try raw field access as a last resort
                    Field f = window.getClass().getDeclaredField("window");
                    f.setAccessible(true);
                    return f.getLong(window);
                } catch (Exception e3) {
                    return -1; // Failsafe
                }
            }
        }
    }

    private void handleCommand(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 2) return;
        
        try {
            switch (parts[1].toLowerCase()) {
                case "addblock":
                    if (parts.length >= 3) MiningMacro.getInstance().addTargetBlock(parts[2].toLowerCase());
                    break;
                case "clearblocks":
                    MiningMacro.getInstance().clearTargetBlocks();
                    break;
                case "tool":
                    MiningMacro.getInstance().setToolSlot(Integer.parseInt(parts[2]) - 1);
                    break;
            }
        } catch (Exception e) {}
    }
}
