package com.mobileminer.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mobileminer.perception.*;

public class BotController {
    private final BotContext context;
    private final WorldObserver worldObserver;
    private final PlayerObserver playerObserver; 

    public BotController() {
        this.context = new BotContext();
        this.worldObserver = new WorldObserver();
        this.playerObserver = new PlayerObserver(); 
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // 1. Perception Layer
        PlayerSnapshot pSnap = playerObserver.getSnapshot(client);
        WorldSnapshot wSnap = worldObserver.getSnapshot(client, "diamond_ore", 5);
        
        // 2. Debug Verification (Print every 10 ticks)
        if (pSnap != null && pSnap.tick % 10 == 0) {
            String moveState = pSnap.actuallyMoved ? "§aMoved" : "§cStationary";
            String targetState = (wSnap.closestTarget != null) ? "§bFound: " + wSnap.closestTarget.toString() : "§8No Target";
            
            String debugTxt = String.format("%s | %s | HP:%.1f", moveState, targetState, pSnap.health);
            client.player.sendOverlayMessage(Component.literal(debugTxt));
        }
    }
}
