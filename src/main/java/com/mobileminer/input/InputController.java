package com.mobileminer.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

public class InputController {
    public static void setPressed(KeyMapping key, boolean pressed) {
        if (key != null) {
            key.setDown(pressed);
        }
    }

    public static void releaseAll(Minecraft client) {
        if (client == null || client.options == null) return;
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
    }
}
