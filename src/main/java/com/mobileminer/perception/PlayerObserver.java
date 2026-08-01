package com.mobileminer.perception;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerObserver {
    private Vec3 lastPos = null;

    public PlayerSnapshot getSnapshot(Minecraft client) {
        Player player = client.player;
        if (player == null) return null;
        
        Vec3 currentPos = player.position();
        boolean actuallyMoved = false;
        
        // ChatGPT's Fix: Only calculate distance if we have a previous position
        if (lastPos != null) {
            double distMoved = currentPos.distanceToSqr(lastPos);
            actuallyMoved = distMoved > 0.0001; 
        }
        
        // Save current position for the next tick
        lastPos = currentPos;

        String item = player.getMainHandItem().getHoverName().getString();
        return new PlayerSnapshot(player.tickCount, currentPos, player.getDeltaMovement(), player.getYRot(), player.getXRot(), player.getHealth(), item, actuallyMoved);
    }
}
