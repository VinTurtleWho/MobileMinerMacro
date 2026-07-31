package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class CombatMacro {
    private static CombatMacro instance;
    private boolean isCombatActive = false;
    
    private final List<String> targetMobs = new ArrayList<>();
    private Entity currentMob = null;
    private List<BlockPos> currentPath = null;
    private boolean calculatingPath = false;

    // Combat & Movement Timing Variables
    private long lastClickTime = 0;
    private long mobTargetTime = 0;
    private final Random random = new Random();

    private CombatMacro() {}

    public static CombatMacro getInstance() {
        if (instance == null) instance = new CombatMacro();
        return instance;
    }

    public void addTargetMob(String mobName) {
        targetMobs.add(mobName.toLowerCase());
    }

    public void clearTargetMobs() {
        targetMobs.clear();
        currentMob = null;
        currentPath = null;
    }

    public void toggle(Minecraft client) {
        if (!isCombatActive) {
            isCombatActive = true;
            sendMessage(client, "§c[MobileMiner] Combat Mode Activated. Scanning for targets...");
        } else {
            isCombatActive = false;
            stopMovement(client);
            currentMob = null;
            currentPath = null;
            sendMessage(client, "§7[MobileMiner] Combat Mode Deactivated.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !isCombatActive) return;

        // 1. Find or validate target mob
        if (currentMob == null || !isTargetValid(currentMob)) {
            currentMob = scanForClosestMob(client, 30); // 30 block search range
            currentPath = null;
            mobTargetTime = System.currentTimeMillis();
            if (currentMob != null) {
                sendMessage(client, "§e[Combat] Locked onto target entity!");
            }
        }

        if (currentMob == null) {
            stopMovement(client);
            return;
        }

        double distance = client.player.distanceTo(currentMob);

        // 2. COMBAT BRAIN (Within 3 blocks: Stop and Attack)
        if (distance <= 3.0) {
            stopMovement(client);
            lookAtEntity(client, currentMob);
            executeAdaptiveAttack(client);
            return;
        }

        // 3. PATHFINDING BRAIN (Beyond 3 blocks: Execute A* Omni-Movement)
        BlockPos mobPos = currentMob.blockPosition();
        
        if (currentPath == null && !calculatingPath) {
            calculatingPath = true;
            Pathfinder.calculatePathAsync(client, client.player.blockPosition(), mobPos)
                .thenAccept(path -> {
                    currentPath = path;
                    calculatingPath = false;
                });
        }

        // Follow the path using omni-directional movement
        if (currentPath != null && !currentPath.isEmpty()) {
            BlockPos nextWaypoint = currentPath.get(0);
            
            // If we reached the current waypoint, drop it and move to the next
            if (client.player.blockPosition().closerThan(nextWaypoint, 1.2)) {
                currentPath.remove(0);
                return;
            }

            navigateToWaypoint(client, nextWaypoint);
        } else {
            // Fallback: Direct line-of-sight chase if pathfinding is still computing
            directChase(client, currentMob);
        }
    }

    // OMNI-DIRECTIONAL MOVEMENT EXECUTOR
    private void navigateToWaypoint(Minecraft client, BlockPos waypoint) {
        double targetX = waypoint.getX() + 0.5;
        double targetZ = waypoint.getZ() + 0.5;

        double dx = targetX - client.player.getX();
        double dz = targetZ - client.player.getZ();

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        smoothLookTowards(client, targetYaw, client.player.getXRot());

        // Hold WASD and Sprint
        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);

        // Jump if waypoint is higher than our feet
        if (waypoint.getY() > client.player.getY() && client.player.horizontalCollision) {
            client.options.keyJump.setDown(true);
        } else {
            client.options.keyJump.setDown(false);
        }
    }

    private void directChase(Minecraft client, Entity mob) {
        Vec3 pos = mob.position();
        double dx = pos.x - client.player.getX();
        double dz = pos.z - client.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;

        smoothLookTowards(client, targetYaw, client.player.getXRot());
        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);
        if (client.player.horizontalCollision) {
            client.options.keyJump.setDown(true);
        }
    }

    // ADAPTIVE CPS COMBAT ENGINE
    private void executeAdaptiveAttack(Minecraft client) {
        long timeAlive = System.currentTimeMillis() - mobTargetTime;
        long currentTime = System.currentTimeMillis();

        // Check if it's a "Boss" (alive for more than 1 second in our crosshair)
        boolean isBoss = timeAlive > 1000;
        
        long clickDelay;
        if (!isBoss) {
            // Phase 1: The Clean 1-Tap TriggerBot
            clickDelay = 400; 
        } else {
            // Phase 2: Sweat Mode - Unpredictable 5-8 Burst CPS with humanized micro-pauses
            clickDelay = 120 + random.nextInt(100); 
        }

        if (currentTime - lastClickTime >= clickDelay) {
            client.options.keyAttack.setDown(true);
            // Instantly un-click on the next frame to register a proper hit swing
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(25); } catch (Exception e) {}
                if (client.options != null) client.options.keyAttack.setDown(false);
            });
            lastClickTime = currentTime;
        }
    }

    private void smoothLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        client.player.setYRot(currentYaw + (yawDiff * 0.3f)); // Smooth turning speed
        client.player.setXRot(targetPitch);
    }

    private void lookAtEntity(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        Vec3 target = entity.getEyePosition();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        smoothLookTowards(client, yaw, pitch);
    }

    private void stopMovement(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false);
    }

    private Entity scanForClosestMob(Minecraft client, double range) {
        Entity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (entity instanceof LivingEntity && isTargetValid(entity)) {
                double dist = client.player.distanceTo(entity);
                if (dist < range && dist < minDistance) {
                    minDistance = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    private boolean isTargetValid(Entity entity) {
        if (!entity.isAlive()) return false;
        String name = entity.getName().getString().toLowerCase();
        for (String target : targetMobs) {
            if (name.contains(target)) return true;
        }
        return false;
    }

    private void sendMessage(Minecraft client, String text) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }
}
