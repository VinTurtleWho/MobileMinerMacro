package com.mobileminer.perception;

public class WorldSnapshot {
    public final BlockObservation closestTarget;
    public final int tick;

    public WorldSnapshot(BlockObservation closestTarget, int tick) {
        this.closestTarget = closestTarget;
        this.tick = tick;
    }
}
