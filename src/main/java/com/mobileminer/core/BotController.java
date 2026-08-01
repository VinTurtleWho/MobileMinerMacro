package com.mobileminer.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mobileminer.perception.*;
import com.mobileminer.planning.TaskPlanner;

public class BotController {
    private final BotContext context;
    private final WorldObserver worldObserver;
    private final PlayerObserver playerObserver; 
    private final TaskPlanner taskPlanner;

    public BotController() {
        this.context = new BotContext();
        this.worldObserver = new WorldObserver();
        this.playerObserver = new PlayerObserver(); 
        this.taskPlanner = new TaskPlanner();
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // 1. Perception Layer
        PlayerSnapshot pSnap = playerObserver.getSnapshot(client);
        WorldSnapshot wSnap = worldObserver.getSnapshot(client, "diamond_ore", 5);
        
        // 2. Planning Layer (Zero action inputs executed)
        taskPlanner.evaluate(context, pSnap, wSnap);

        // 3. Debug Output (Print state decisions every 10 ticks)
        if (pSnap != null && pSnap.tick % 10 == 0) {
            String stateTxt = String.format("§e[%s-%s]", context.getTask(), context.getPhase());
            String targetState = (wSnap.closestTarget != null) 
                ? String.format("§bTarget: %.1fm", wSnap.closestTarget.distance) 
                : "§8No Target";
            
            String debugTxt = String.format("%s | %s", stateTxt, targetState);
            client.player.sendOverlayMessage(Component.literal(debugTxt));
        }
    }
}
