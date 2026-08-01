package com.mobileminer.perception;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class PlayerObserver {
    public static PlayerSnapshot getSnapshot(Minecraft client) {
        Player player = client.player;
        if (player == null) return null;
        
        String item = player.getMainHandItem().getHoverName().getString();
        return new PlayerSnapshot(player.tickCount, player.position(), player.getDeltaMovement(), player.getYRot(), player.getXRot(), player.getHealth(), item);
    }
}
