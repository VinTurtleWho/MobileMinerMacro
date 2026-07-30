package com.mobileminer;

public class TickRecord {
    public final boolean w, a, s, d, space, sneak, attack, use;
    public final float yaw, pitch;

    public TickRecord(boolean w, boolean a, boolean s, boolean d, boolean space, boolean sneak, boolean attack, boolean use, float yaw, float pitch) {
        this.w = w; this.a = a; this.s = s; this.d = d;
        this.space = space; this.sneak = sneak;
        this.attack = attack; this.use = use;
        this.yaw = yaw; this.pitch = pitch;
    }
}
