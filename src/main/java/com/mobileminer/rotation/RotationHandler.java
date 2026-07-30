package com.mobileminer.rotation;

import net.minecraft.client.Minecraft;

public class RotationHandler {
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean rotating = false;

    // Physical momentum variables (This is what beats the AI)
    private float yawVelocity = 0.0f;
    private float pitchVelocity = 0.0f;

    public void startRotation(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.rotating = true;
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
    }

    public void updateTarget(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.rotating = true;
    }

    public boolean updateRotation(Minecraft client) {
        if (!rotating || client.player == null) return true;

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();

        float yawDiff = targetYaw - currentYaw;
        float pitchDiff = targetPitch - currentPitch;

        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        // DEADZONE: Stop exactly when the crosshair is centered
        if (Math.abs(yawDiff) < 1.5f && Math.abs(pitchDiff) < 1.5f && Math.abs(yawVelocity) < 1.0f) {
            rotating = false;
            return true;
        }

        // SPRING-MASS PHYSICS: Creates natural acceleration and deceleration (momentum)
        float spring = 0.12f + (float)(Math.random() * 0.03f);
        float damping = 0.65f + (float)(Math.random() * 0.10f);

        yawVelocity += yawDiff * spring;
        yawVelocity *= damping;
        
        pitchVelocity += pitchDiff * spring;
        pitchVelocity *= damping;

        // GCD (Mouse Pixel) Calculation
        double sensitivity = client.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6 + 0.2);
        float gcd = f * f * f * 8.0f;
        float stepMult = gcd * 0.15f; 

        float maxTurn = 22.0f; 
        float stepYaw = Math.max(-maxTurn, Math.min(maxTurn, yawVelocity));
        float stepPitch = Math.max(-maxTurn, Math.min(maxTurn, pitchVelocity));

        // Quantize to strict physical mouse pixels
        int mouseDeltaX = Math.round(stepYaw / stepMult);
        int mouseDeltaY = Math.round(stepPitch / stepMult);

        if (mouseDeltaX == 0 && Math.abs(yawDiff) > stepMult * 2) mouseDeltaX = (int) Math.signum(yawDiff);
        if (mouseDeltaY == 0 && Math.abs(pitchDiff) > stepMult * 2) mouseDeltaY = (int) Math.signum(pitchDiff);

        client.player.setYRot(currentYaw + ((float) mouseDeltaX * stepMult));
        client.player.setXRot(Math.max(-90.0f, Math.min(90.0f, currentPitch + ((float) mouseDeltaY * stepMult))));

        return false;
    }

    public boolean isRotating() { return rotating; }
}
