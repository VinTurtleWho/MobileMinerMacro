package com.mobileminer.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mobileminer.perception.PlayerObserver;
import com.mobileminer.perception.PlayerSnapshot;

public class BotController {
    private final BotContext context;

    public BotController() {
        this.context = new BotContext();
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // 1. Perception
        PlayerSnapshot snapshot = PlayerObserver.getSnapshot(client);
        
        // Debug Output: Print to Action Bar every 10 ticks (half a second)
        if (snapshot != null && client.player.tickCount % 10 == 0) {
            String debugTxt = "§a[Bot] " + context.getTask() + "-" + context.getPhase() + " | " + snapshot.toString();
            client.player.displayClientMessage(Component.literal(debugTxt), true);
        }
    }
}
