package com.mobileminer.perception;

import net.minecraft.world.phys.Vec3;

public class PlayerSnapshot {
    public final Vec3 position;
    public final float yaw;
    public final float pitch;
    public final float health;
    public final String heldItem;

    public PlayerSnapshot(Vec3 position, float yaw, float pitch, float health, String heldItem) {
        this.position = position; 
        this.yaw = yaw; 
        this.pitch = pitch; 
        this.health = health; 
        this.heldItem = heldItem;
    }

    @Override
    public String toString() {
        return String.format("Pos:[%.1f, %.1f, %.1f] Yaw:%.1f Pitch:%.1f HP:%.1f Item:%s",
                position.x, position.y, position.z, yaw, pitch, health, heldItem);
    }
}
