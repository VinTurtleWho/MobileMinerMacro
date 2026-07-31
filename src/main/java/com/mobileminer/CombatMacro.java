package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CombatMacro {
    private static CombatMacro instance;
    private boolean isCombatActive = false;
    
    private final List<String> targetMobs = new ArrayList<>();
    private Entity currentMob = null;
    private List<BlockPos> currentPath = null;
    private boolean calculatingPath = false;

    // Burst CPS Variables
    private long currentMobFirstHitTime = 0;
    private long nextClickTime = 0;
    private long burstCooldownEndTime = 0;
    private int burstClicksRemaining = 0;
    private final Random random = new Random();

    // Anti-Stuck Variables
    private Vec3 lastPlayerPos = Vec3.ZERO;
    private int stuckTicks = 0;

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
            sendMessage(client, "§c[MobileMiner] Combat Mode Activated. Sweaty Mode ON.");
        } else {
            isCombatActive = false;
            stopMovement(client);
            currentMob = null;
            currentPath = null;
            currentMobFirstHitTime = 0;
            burstClicksRemaining = 0;
            sendMessage(client, "§7[MobileMiner] Combat Mode Deactivated.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !isCombatActive) return;

        // 1. Anti-Stuck Memory Check
        double moveDist = client.player.position().distanceToSqr(lastPlayerPos);
        if (client.options.keyUp.isDown() && moveDist < 0.005) {
            stuckTicks++;
            if (stuckTicks > 10) { 
                client.options.keyJump.setDown(true); 
                currentPath = null; 
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
            if (currentPath != null && !currentPath.isEmpty()) {
                if (currentPath.get(0).getY() <= client.player.getY() && !client.player.horizontalCollision) {
                    client.options.keyJump.setDown(false);
                }
            } else {
                client.options.keyJump.setDown(false);
            }
        }
        lastPlayerPos = client.player.position();

        // 2. Find or validate target
        if (currentMob == null || !isTargetValid(currentMob, client)) {
            if (currentMob != null) stopMovement(client); 
            
            currentMob = scanForClosestMob(client, 30);
            currentPath = null;
            currentMobFirstHitTime = 0; // Reset boss timer for new mob
        }

        if (currentMob == null) {
            stopMovement(client);
            return;
        }

        // Get the real flesh entity to bypass holograms
        Entity actualTarget = getRealFleshEntity(client, currentMob);
        if (actualTarget == null) {
            currentMob = null; // Hologram is empty, drop it
            return; 
        }

        double distance = client.player.distanceTo(actualTarget);

        // 3. COMBAT BRAIN
        if (distance <= 3.2) {
            stopMovement(client);
            lookAtTorso(client, actualTarget);
            executeAdaptiveAttack(client);
            return;
        }

        // 4. PATHFINDING BRAIN
        BlockPos mobPos = actualTarget.blockPosition();
        
        if (currentPath == null && !calculatingPath) {
            calculatingPath = true;
            Pathfinder.calculatePathAsync(client, client.player.blockPosition(), mobPos)
                .thenAccept(path -> {
                    currentPath = path;
                    calculatingPath = false;
                });
        }

        // Fix 1: Kill Blitz Chase. Just stare at the mob and wait for the math to finish to prevent spinning.
        if (calculatingPath) {
            lookAtTorso(client, actualTarget);
            stopMovement(client);
            return;
        }

        // Fix 2: Look-Ahead Smoothing
        if (currentPath != null && !currentPath.isEmpty()) {
            BlockPos nextWaypoint = currentPath.get(0);
            
            // Horizontal distance calculation (X and Z only)
            double dx = nextWaypoint.getX() + 0.5 - client.player.getX();
            double dz = nextWaypoint.getZ() + 0.5 - client.player.getZ();
            double distSq = dx * dx + dz * dz;

            // If we are within 1.5 blocks horizontally, we drop the waypoint early to curve the corner!
            if (distSq < 2.25) { 
                currentPath.remove(0);
                if (!currentPath.isEmpty()) {
                    // Update our aim immediately to the next block in line
                    nextWaypoint = currentPath.get(0);
                    dx = nextWaypoint.getX() + 0.5 - client.player.getX();
                    dz = nextWaypoint.getZ() + 0.5 - client.player.getZ();
                } else {
                    return;
                }
            }
            
            navigateToWaypoint(client, nextWaypoint, dx, dz);
        } else {
            // Failsafe direct chase
            directChase(client, actualTarget);
        }
    }

    private void navigateToWaypoint(Minecraft client, BlockPos waypoint, double dx, double dz) {
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        humanizedLookTowards(client, targetYaw, client.player.getXRot());

        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);

        if ((waypoint.getY() > client.player.getY() || client.player.horizontalCollision) && stuckTicks == 0) {
            client.options.keyJump.setDown(true);
        }
    }

    private void directChase(Minecraft client, Entity mob) {
        Vec3 pos = mob.position();
        double dx = pos.x - client.player.getX();
        double dz = pos.z - client.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;

        humanizedLookTowards(client, targetYaw, client.player.getXRot());
        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);
        
        if (client.player.horizontalCollision && stuckTicks == 0) {
            client.options.keyJump.setDown(true);
        }
    }

    private void executeAdaptiveAttack(Minecraft client) {
        long currentTime = System.currentTimeMillis();
        
        if (currentMobFirstHitTime == 0) {
            currentMobFirstHitTime = currentTime;
        }

        long timeSinceFirstHit = currentTime - currentMobFirstHitTime;
        boolean isBoss = timeSinceFirstHit > 1000; 
        
        if (!isBoss) {
            if (currentTime >= nextClickTime) {
                injectHardwareClick(client);
                nextClickTime = currentTime + 400 + random.nextInt(100); 
            }
        } else {
            if (burstClicksRemaining > 0) {
                if (currentTime >= nextClickTime) {
                    injectHardwareClick(client);
                    burstClicksRemaining--;
                    nextClickTime = currentTime + 50 + random.nextInt(36); 
                }
            } else if (currentTime >= burstCooldownEndTime) {
                burstClicksRemaining = 3 + random.nextInt(4); 
                burstCooldownEndTime = currentTime + 150 + random.nextInt(100);
                nextClickTime = currentTime;
            }
        }
    }

    // PURE REFLECTION HARDWARE CLICKER
    private void injectHardwareClick(Minecraft client) {
        try {
            Field clickCountField = KeyMapping.class.getDeclaredField("clickCount");
            clickCountField.setAccessible(true);
            clickCountField.setInt(client.options.keyAttack, clickCountField.getInt(client.options.keyAttack) + 1);
        } catch (Exception e) {
            try {
                Field timesPressedField = KeyMapping.class.getDeclaredField("timesPressed");
                timesPressedField.setAccessible(true);
                timesPressedField.setInt(client.options.keyAttack, timesPressedField.getInt(client.options.keyAttack) + 1);
            } catch (Exception ex) {
                try {
                    Field obfField = KeyMapping.class.getDeclaredField("field_1653");
                    obfField.setAccessible(true);
                    obfField.setInt(client.options.keyAttack, obfField.getInt(client.options.keyAttack) + 1);
                } catch (Exception exx) {}
            }
        }
    }

    private void humanizedLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        float currentPitch = client.player.getXRot();
        float pitchDiff = targetPitch - currentPitch;

        double sensitivity = client.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6 + 0.2);
        float gcd = f * f * f * 8.0f;
        float stepMult = gcd * 0.15f;

        float speed = 0.35f;
        int mouseDeltaX = Math.round((yawDiff * speed) / stepMult);
        int mouseDeltaY = Math.round((pitchDiff * speed) / stepMult);

        if (mouseDeltaX == 0 && Math.abs(yawDiff) > stepMult) mouseDeltaX = (int) Math.signum(yawDiff);
        if (mouseDeltaY == 0 && Math.abs(pitchDiff) > stepMult) mouseDeltaY = (int) Math.signum(pitchDiff);

        client.player.setYRot(currentYaw + ((float) mouseDeltaX * stepMult));
        client.player.setXRot(currentPitch + ((float) mouseDeltaY * stepMult));
    }

    // Fix 3: No Flesh, No Target. Validates if a living mob actually exists under the nametag.
    private Entity getRealFleshEntity(Minecraft client, Entity scannedEntity) {
        if (scannedEntity.getClass().getSimpleName().contains("ArmorStand")) {
            for (Entity e : client.level.getEntities(scannedEntity, scannedEntity.getBoundingBox().inflate(1.5))) {
                if (e instanceof LivingEntity && !e.getClass().getSimpleName().contains("ArmorStand")) {
                    LivingEntity living = (LivingEntity) e;
                    if (living.getHealth() > 0 && !living.isDeadOrDying() && living.deathTime == 0) {
                        return e; // Found a living mob beneath the tag!
                    }
                }
            }
            return null; // Empty hologram (the mob is dead), ignore it completely!
        }
        return scannedEntity;
    }

    private void lookAtTorso(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        double torsoY = entity.getY() + (entity.getBbHeight() * 0.5); 
        Vec3 target = new Vec3(entity.getX(), torsoY, entity.getZ());
        
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        humanizedLookTowards(client, yaw, pitch);
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
            if (isTargetValid(entity, client)) {
                double dist = client.player.distanceTo(entity);
                if (dist < range && dist < minDistance) {
                    minDistance = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    private boolean isTargetValid(Entity entity, Minecraft client) {
        if (entity.isRemoved()) return false;
        
        // Ensure there is actual living flesh behind this entity (filters out dead holograms instantly)
        Entity flesh = getRealFleshEntity(client, entity);
        if (flesh == null) return false;
        
        if (flesh instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) flesh;
            if (living.getHealth() <= 0 || living.isDeadOrDying() || living.deathTime > 0) return false;
        }
        
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
