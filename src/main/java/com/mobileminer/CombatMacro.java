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
    private int clickReleaseTimer = 0; // NEW: Tick-based click release
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
            sendMessage(client, "§c[MobileMiner] Combat Mode Activated. Hunting...");
        } else {
            isCombatActive = false;
            stopMovement(client);
            currentMob = null;
            currentPath = null;
            clickReleaseTimer = 0;
            sendMessage(client, "§7[MobileMiner] Combat Mode Deactivated.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !isCombatActive) return;

        // 1. Manage Tick-Based Clicks (Guarantees the game registers the swing)
        if (clickReleaseTimer > 0) {
            clickReleaseTimer--;
            if (clickReleaseTimer == 0) {
                client.options.keyAttack.setDown(false);
            }
        }

        // 2. Find or validate target mob
        if (currentMob == null || !isTargetValid(currentMob)) {
            // Drop current target to avoid corpse-staring
            if (currentMob != null) stopMovement(client); 
            
            currentMob = scanForClosestMob(client, 30); // 30 block search range
            currentPath = null;
            mobTargetTime = System.currentTimeMillis();
            if (currentMob != null) {
                sendMessage(client, "§e[Combat] Locked onto new target!");
            }
        }

        if (currentMob == null) {
            stopMovement(client);
            return;
        }

        double distance = client.player.distanceTo(currentMob);

        // 3. COMBAT BRAIN (Within 3 blocks: Stop and Attack)
        if (distance <= 3.0) {
            stopMovement(client);
            lookAtTorso(client, currentMob);
            executeAdaptiveAttack(client);
            return;
        }

        // 4. PATHFINDING BRAIN (Beyond 3 blocks: Execute A* Omni-Movement)
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
            
            if (client.player.blockPosition().closerThan(nextWaypoint, 1.2)) {
                currentPath.remove(0);
                return;
            }
            navigateToWaypoint(client, nextWaypoint);
        } else {
            directChase(client, currentMob);
        }
    }

    private void navigateToWaypoint(Minecraft client, BlockPos waypoint) {
        double targetX = waypoint.getX() + 0.5;
        double targetZ = waypoint.getZ() + 0.5;

        double dx = targetX - client.player.getX();
        double dz = targetZ - client.player.getZ();

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        smoothLookTowards(client, targetYaw, client.player.getXRot());

        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);

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

    private void executeAdaptiveAttack(Minecraft client) {
        long timeAlive = System.currentTimeMillis() - mobTargetTime;
        long currentTime = System.currentTimeMillis();

        boolean isBoss = timeAlive > 1000;
        
        long clickDelay;
        if (!isBoss) {
            clickDelay = 400; // 1-Tap pacing
        } else {
            clickDelay = 120 + random.nextInt(80); // Boss Sweaty 5-8 CPS pacing
        }

        if (currentTime - lastClickTime >= clickDelay) {
            client.options.keyAttack.setDown(true);
            clickReleaseTimer = 2; // Hold click for exactly 2 game ticks to guarantee a swing
            lastClickTime = currentTime;
        }
    }

    private void smoothLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        client.player.setYRot(currentYaw + (yawDiff * 0.3f));
        client.player.setXRot(targetPitch);
    }

    // NEW: Aim at the chest/torso instead of the nametag
    private void lookAtTorso(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        
        // Calculate center of mass (50% up the entity's height)
        double torsoY = entity.getY() + (entity.getBbHeight() * 0.5);
        Vec3 target = new Vec3(entity.getX(), torsoY, entity.getZ());
        
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
        if (clickReleaseTimer == 0) client.options.keyAttack.setDown(false);
    }

    private Entity scanForClosestMob(Minecraft client, double range) {
        Entity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (isTargetValid(entity)) {
                double dist = client.player.distanceTo(entity);
                if (dist < range && dist < minDistance) {
                    minDistance = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    // NEW: Checks if the entity is actually dead/removed to skip death animations
    private boolean isTargetValid(Entity entity) {
        if (entity.isRemoved()) return false;
        if (!(entity instanceof LivingEntity)) return false;
        
        LivingEntity living = (LivingEntity) entity;
        if (living.getHealth() <= 0 || living.isDeadOrDying()) return false;
        
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
