package com.mobileminer.control;

import net.minecraft.world.phys.Vec3;

public class AimCalculator {

    // Wrap degrees into [-180, +180] range to prevent 358-degree spin bugs
    public static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    public static DesiredRotation calculate(Vec3 eyePos, Vec3 targetPos) {
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Minecraft Yaw math: 0 = South (+Z), 90 = West (-X), 180 = North (-Z), -90 = East (+X)
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        return new DesiredRotation(wrapDegrees(yaw), wrapDegrees(pitch));
    }

    public static float getAngleDifference(float current, float target) {
        return wrapDegrees(target - current);
    }
}
