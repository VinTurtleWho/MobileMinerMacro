package com.mobileminer.perception;

import net.minecraft.world.phys.Vec3;

public class PlayerSnapshot {
    public final int tick;
    public final Vec3 position;
    public final Vec3 velocity;
    public final float yaw;
    public final float pitch;
    public final float health;
    public final String heldItem;
    public final boolean hasVelocity;

    public PlayerSnapshot(int tick, Vec3 position, Vec3 velocity, float yaw, float pitch, float health, String heldItem) {
        this.tick = tick;
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
        this.heldItem = heldItem;
        this.hasVelocity = velocity.lengthSqr() > 0.0001; 
    }
}
