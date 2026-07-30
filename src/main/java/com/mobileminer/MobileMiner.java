package com.mobileminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class MobileMiner implements ClientModInitializer {
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        // Register the key natively so it shows up in Esc -> Controls
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Toggle MobileMiner",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.mobileminer.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            while (toggleKey.consumeClick()) {
                MiningMacro.getInstance().toggle(client);
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
