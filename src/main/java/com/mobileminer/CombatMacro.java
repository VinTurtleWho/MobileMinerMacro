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
    private boolean pathFailed = false; 

    private long currentMobFirstHitTime = 0;
    private long nextClickTime = 0;
    private long burstCooldownEndTime = 0;
    private int burstClicksRemaining = 0;
    private final Random random = new Random();

    private long lastTargetScanTime = 0;

    private double targetOffsetX = 0.0;
    private double targetOffsetY = 0.0;
    private double targetOffsetZ = 0.0;

    private final Map<Integer, Long> deadEntityBlacklist = new HashMap<>(); 
    private final Map<Integer, Double> unreachableEntityBlacklist = new HashMap<>(); 

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
        deadEntityBlacklist.clear();
        unreachableEntityBlacklist.clear();
    }

    public void toggle(Minecraft client) {
        if (!isCombatActive) {
            isCombatActive = true;
            sendMessage(client, "§c[MobileMiner] Combat Mode Activated.");
        } else {
            isCombatActive = false;
            stopMovement(client);
            currentMob = null;
            currentPath = null;
            currentMobFirstHitTime = 0;
            burstClicksRemaining = 0;
            pathFailed = false;
            deadEntityBlacklist.clear();
            unreachableEntityBlacklist.clear();
            sendMessage(client, "§7[MobileMiner] Combat Mode Deactivated.");
        }
    }

    private void navigateWithStrafing(Minecraft client, BlockPos waypoint, double dx, double dz) {
        float moveYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        applyWASDKeys(client, moveYaw);
        client.options.keySprint.setDown(true);

        if ((waypoint.getY() > client.player.getY() || client.player.horizontalCollision) && stuckTicks == 0 && canJump(client)) {
            client.options.keyJump.setDown(true);
        }
    }

    private void directStrafeChase(Minecraft client, Entity mob) {
        double dx = mob.getX() - client.player.getX();
        double dz = mob.getZ() - client.player.getZ();
        float moveYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        
        applyWASDKeys(client, moveYaw);
        client.options.keySprint.setDown(true);
        
        if (client.player.horizontalCollision && stuckTicks == 0 && canJump(client)) {
            client.options.keyJump.setDown(true);
        }
    }

    private void applyWASDKeys(Minecraft client, float moveYaw) {
        float playerYaw = client.player.getYRot();
        float diff = moveYaw - playerYaw;
        
        while (diff < -180.0f) diff += 360.0f;
        while (diff > 180.0f) diff -= 360.0f;

        boolean w = false, s = false, a = false, d = false;

        if (diff > -60 && diff < 60) w = true; 
        if (diff < -120 || diff > 120) s = true; 
        if (diff > 30 && diff < 150) d = true; 
        if (diff < -30 && diff > -150) a = true; 

        client.options.keyUp.setDown(w);
        client.options.keyDown.setDown(s);
        client.options.keyLeft.setDown(a);
        client.options.keyRight.setDown(d);
    }

    private boolean canJump(Minecraft client) {
        BlockPos headPos = new BlockPos((int)client.player.getX(), (int)client.player.getY() + 2, (int)client.player.getZ());
        return client.level.getBlockState(headPos).isAir(); 
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
                nextClickTime = currentTime + 350 + random.nextInt(80); 
            }
        } else {
            if (burstClicksRemaining > 0) {
                if (currentTime >= nextClickTime) {
                    injectHardwareClick(client);
                    burstClicksRemaining--;
                    nextClickTime = currentTime + 50 + random.nextInt(30); 
                }
            } else if (currentTime >= burstCooldownEndTime) {
                burstClicksRemaining = 3 + random.nextInt(4); 
                burstCooldownEndTime = currentTime + 140 + random.nextInt(80);
                nextClickTime = currentTime;
            }
        }
    }

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
            } catch (Exception ex) {}
        }
    }

    private void lookAtCenterMass(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        
        double torsoY = entity.getY() + (entity.getBbHeight() * 0.55) + targetOffsetY; 
        Vec3 target = new Vec3(entity.getX() + targetOffsetX, torsoY, entity.getZ() + targetOffsetZ);
        
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float targetPitch;
        
        // GIMBAL LOCK PREVENTION & PITCH CLAMP
        if (dist < 0.3) {
            targetPitch = client.player.getXRot(); // Mob is inside player, freeze pitch
        } else {
            targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
            // Force camera to never look further down than 35 degrees during combat
            targetPitch = Math.max(-60.0f, Math.min(35.0f, targetPitch));
        }

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        float pitchDiff = targetPitch - currentPitch;

        if (Math.abs(yawDiff) < 2.5f && Math.abs(pitchDiff) < 2.5f) {
            return; 
        }

        humanizedLookTowards(client, targetYaw, targetPitch);
    }

    private void humanizedLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        float pitchDiff = targetPitch - currentPitch;

        float yawAbs = Math.abs(yawDiff);
        float pitchAbs = Math.abs(pitchDiff);

        float maxYawSpeed;
        if (yawAbs > 100) maxYawSpeed = 75.0f;       
        else if (yawAbs > 45) maxYawSpeed = 50.0f;   
        else if (yawAbs > 15) maxYawSpeed = 30.0f;   
        else maxYawSpeed = Math.max(4.0f, yawAbs * 0.5f); 

        float maxPitchSpeed;
        if (pitchAbs > 45) maxPitchSpeed = 45.0f;
        else if (pitchAbs > 15) maxPitchSpeed = 25.0f;
        else maxPitchSpeed = Math.max(3.0f, pitchAbs * 0.5f);

        yawDiff = Math.max(-maxYawSpeed, Math.min(maxYawSpeed, yawDiff));
        pitchDiff = Math.max(-maxPitchSpeed, Math.min(maxPitchSpeed, pitchDiff));

        double sensitivity = client.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6 + 0.2);
        float gcd = f * f * f * 8.0f;
        float stepMult = gcd * 0.15f;

        float speed = 0.45f;
        int mouseDeltaX = Math.round((yawDiff * speed) / stepMult);
        int mouseDeltaY = Math.round((pitchDiff * speed) / stepMult);

        if (mouseDeltaX == 0 && Math.abs(yawDiff) > stepMult) mouseDeltaX = (int) Math.signum(yawDiff);
        if (mouseDeltaY == 0 && Math.abs(pitchDiff) > stepMult) mouseDeltaY = (int) Math.signum(pitchDiff);

        client.player.setYRot(currentYaw + ((float) mouseDeltaX * stepMult));
        client.player.setXRot(currentPitch + ((float) mouseDeltaY * stepMult));
    }

    private Entity getRealFleshEntity(Minecraft client, Entity scannedEntity) {
        if (scannedEntity.getClass().getSimpleName().contains("ArmorStand")) {
            for (Entity e : client.level.getEntities(scannedEntity, scannedEntity.getBoundingBox().inflate(1.5))) {
                if (e instanceof LivingEntity && !e.getClass().getSimpleName().contains("ArmorStand")) {
                    LivingEntity living = (LivingEntity) e;
                    if (living.getHealth() > 0 && !living.isDeadOrDying() && living.deathTime == 0) {
                        return e; 
                    }
                }
            }
            return null; 
        }
        return scannedEntity;
    }

    private void stopMovement(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false);
    }

    private Entity scanForBestClusterMob(Minecraft client, double range) {
        List<Entity> validTargets = new ArrayList<>();
        
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (isTargetValid(entity, client)) {
                if (client.player.distanceTo(entity) < range) {
                    validTargets.add(entity);
                }
            }
        }

        Entity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity target : validTargets) {
            double distance = client.player.distanceTo(target);
            int friendsNearby = 0;
            
            for (Entity friend : validTargets) {
                if (friend != target && target.distanceTo(friend) <= 5.0) {
                    friendsNearby++;
                }
            }
            
            double score = distance - (friendsNearby * 4.0);
            
            if (score < bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }
        
        return bestTarget;
    }

    private boolean hasClearLineOfSight(Minecraft client, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        // CORNER CLIPPING BYPASS: If mob is within 2.5 blocks, assume clear sight to prevent wall-grazing drops
        if (dist < 2.5) return true; 
        
        int steps = (int) Math.ceil(dist * 2); 
        double dx = (end.x - start.x) / steps;
        double dy = (end.y - start.y) / steps;
        double dz = (end.z - start.z) / steps;

        for (int i = 1; i < steps; i++) {
            BlockPos pos = new BlockPos((int)(start.x + dx * i), (int)(start.y + dy * i), (int)(start.z + dz * i));
            if (client.level.getBlockState(pos).isSolidRender()) { 
                return false; 
            }
        }
        return true; 
    }

    private boolean isTargetValid(Entity entity, Minecraft client) {
        if (entity.isRemoved()) return false;
        
        int id = entity.getId();
        
        if (deadEntityBlacklist.containsKey(id)) {
            if (System.currentTimeMillis() - deadEntityBlacklist.get(id) > 3000) {
                deadEntityBlacklist.remove(id);
            } else {
                return false; 
            }
        }

        if (unreachableEntityBlacklist.containsKey(id)) {
            if (hasClearLineOfSight(client, client.player.getEyePosition(), entity.getEyePosition())) {
                unreachableEntityBlacklist.remove(id); 
            } else {
                return false; 
            }
        }
        
        Entity flesh = getRealFleshEntity(client, entity);
        if (flesh == null) return false;
        
        if (flesh instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) flesh;
            if (living.getHealth() <= 0 || living.isDeadOrDying() || living.deathTime > 0) {
                deadEntityBlacklist.put(id, System.currentTimeMillis());
                return false;
            }
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
