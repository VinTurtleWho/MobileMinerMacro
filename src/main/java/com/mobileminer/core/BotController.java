package com.mobileminer.core;

import net.minecraft.client.Minecraft;

public class BotController {
    private final BotContext context;

    public BotController() {
        this.context = new BotContext();
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // Future loop:
        // 1. Perception
        // 2. Planning
        // 3. Control
        // 4. Verification
    }
}
