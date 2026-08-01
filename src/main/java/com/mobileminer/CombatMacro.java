package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.*;

public class CombatMacro {
    private static CombatMacro instance;
    private boolean isCombatActive = false;
    private final List<String> targetMobs = new ArrayList<>();
    private Entity currentMob = null, lastMob = null;
    private List<BlockPos> currentPath = null;
    private boolean calculatingPath = false, pathFailed = false; 

    private long currentMobFirstHitTime = 0, nextClickTime = 0, burstCooldownEndTime = 0;
    private int burstClicksRemaining = 0;
    private boolean announcedBossMode = false;
    private final Random random = new Random();
    private long lastTargetScanTime = 0;

    private final Map<Integer, Long> deadEntityBlacklist = new HashMap<>(); 
    private final Map<Integer, Double> unreachableEntityBlacklist = new HashMap<>(); 
    private Vec3 lastPlayerPos = Vec3.ZERO;
    private int stuckTicks = 0;

    // God Rotation Variables
    private long flickStart = 0, flickTime = 250;
    private float startYaw, startPitch, ctrlYaw, ctrlPitch, endYaw, endPitch, noiseTime = 0;

    private CombatMacro() {}
    public static CombatMacro getInstance() {
        if (instance == null) instance = new CombatMacro();
        return instance;
    }

    public void addTargetMob(String mobName) { targetMobs.add(mobName.toLowerCase()); }
    public void clearTargetMobs() {
        targetMobs.clear(); currentMob = null; currentPath = null;
        deadEntityBlacklist.clear(); unreachableEntityBlacklist.clear();
    }

    public void toggle(Minecraft client) {
        isCombatActive = !isCombatActive;
        if (!isCombatActive) stopMovement(client);
        currentMob = null; currentPath = null; currentMobFirstHitTime = 0;
        sendMessage(client, isCombatActive ? "§c[MobileMiner] Combat Mode Activated." : "§7[MobileMiner] Combat Deactivated.");
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !isCombatActive) return;
        if (client.player.tickCount % 4 == 0) Pathfinder.cacheWorldChunk(client, 12);
        long currentTime = System.currentTimeMillis();

        if (pathFailed) {
            if (currentMob != null) unreachableEntityBlacklist.put(currentMob.getId(), (double)client.player.distanceTo(currentMob));
            currentMob = null; currentPath = null; pathFailed = false; stopMovement(client);
        }

        if (currentMob == null || !isTargetValid(currentMob, client) || (currentTime - lastTargetScanTime > 400)) {
            Entity bestMob = scanForBestClusterMob(client, 25);
            if (bestMob != null && bestMob != currentMob) {
                currentMob = bestMob; currentPath = null; currentMobFirstHitTime = 0; announcedBossMode = false;
            } else if (bestMob == null) {
                currentMob = null; announcedBossMode = false;
            }
            lastTargetScanTime = currentTime;
        }

        if (currentMob == null) { stopMovement(client); return; }
        Entity actualTarget = getRealFleshEntity(client, currentMob);
        if (actualTarget == null) return;

        double distance = client.player.distanceTo(actualTarget);
        boolean canSeeTarget = hasClearLineOfSight(client, client.player.getEyePosition(), actualTarget.getEyePosition());

        if (canSeeTarget || currentPath == null || currentPath.isEmpty()) {
            executeGodRotation(client, actualTarget);
        } else {
            BlockPos nextWP = currentPath.get(0);
            float pathYaw = (float) Math.toDegrees(Math.atan2(nextWP.getZ() + 0.5 - client.player.getZ(), nextWP.getX() + 0.5 - client.player.getX())) - 90.0f;
            float pathPitch = (float) -Math.toDegrees(Math.atan2(nextWP.getY() + 1.62 - client.player.getEyePosition().y, Math.sqrt(Math.pow(nextWP.getX() + 0.5 - client.player.getX(), 2) + Math.pow(nextWP.getZ() + 0.5 - client.player.getZ(), 2))));
            smoothTravelLookTowards(client, pathYaw, Math.max(-15.0f, Math.min(15.0f, pathPitch)));
        }

        if (distance <= 3.3 && canSeeTarget) { stopMovement(client); executeAdaptiveAttack(client); return; }

        if (currentPath == null && !calculatingPath) {
            calculatingPath = true;
            Pathfinder.calculatePathAsync(client, client.player.blockPosition(), actualTarget.blockPosition()).thenAccept(path -> {
                if (path == null || path.isEmpty()) pathFailed = true; else currentPath = path;
                calculatingPath = false;
            });
        }
        if (calculatingPath) { stopMovement(client); return; }
        if (currentPath != null && !currentPath.isEmpty()) {
            BlockPos nextWP = currentPath.get(0);
            double dx = nextWP.getX() + 0.5 - client.player.getX(), dz = nextWP.getZ() + 0.5 - client.player.getZ();
            if (dx * dx + dz * dz < 1.8) { currentPath.remove(0); if (!currentPath.isEmpty()) nextWP = currentPath.get(0); else return; }
            navigateWithStrafing(client, nextWP, nextWP.getX() + 0.5 - client.player.getX(), nextWP.getZ() + 0.5 - client.player.getZ());
        } else directStrafeChase(client, actualTarget);
    }

    private void executeGodRotation(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        double torsoY = entity.getY() + (entity.getBbHeight() * 0.6); 
        double dx = entity.getX() - eyes.x;
        double dy = torsoY - eyes.y;
        double dz = entity.getZ() - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        
        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float yawDiff = wrapAngle(idealYaw - currentYaw);
        
        if (currentMob != lastMob || Math.abs(yawDiff) > 25.0f) {
            flickStart = System.currentTimeMillis();
            flickTime = 120 + random.nextInt(100); 
            startYaw = currentYaw; startPitch = currentPitch;
            
            float overshoot = (random.nextBoolean()) ? 1.0f + (random.nextFloat() * 0.03f) : 1.0f;
            endYaw = currentYaw + (yawDiff * overshoot);
            endPitch = idealPitch;
            
            ctrlYaw = currentYaw + (yawDiff * 0.5f) + (random.nextFloat() * 15 - 7.5f);
            ctrlPitch = currentPitch + ((idealPitch - currentPitch) * 0.5f) + (random.nextFloat() * 15 - 7.5f);
            lastMob = currentMob;
        }
        
        long elapsed = System.currentTimeMillis() - flickStart;
        float t = Math.min(1.0f, (float)elapsed / flickTime);
        float easedT = 1.0f - (float)Math.pow(1.0f - t, 3); // Ease-out cubic
        
        float nextYaw, nextPitch;
        if (t < 1.0f) {
            float u = 1.0f - easedT;
            nextYaw = (u*u * startYaw) + (2*u*easedT * ctrlYaw) + (easedT*easedT * endYaw);
            nextPitch = (u*u * startPitch) + (2*u*easedT * ctrlPitch) + (easedT*easedT * endPitch);
        } else {
            noiseTime += 0.15f;
            nextYaw = idealYaw + (float)(Math.cos(noiseTime * 0.7) * 0.6);
            nextPitch = idealPitch + (float)(Math.sin(noiseTime) * 0.4);
        }
        applyGCDRotation(client, nextYaw, nextPitch);
    }

    private void applyGCDRotation(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float f = (float) (client.options.sensitivity().get() * 0.6 + 0.2);
        float step = f * f * f * 8.0f * 0.15f;
        
        int mX = Math.round((targetYaw - currentYaw) / step);
        int mY = Math.round((targetPitch - currentPitch) / step);
        
        client.player.setYRot(currentYaw + (mX * step));
        client.player.setXRot(currentPitch + (mY * step));
    }

    private float wrapAngle(float angle) {
        while (angle <= -180.0f) angle += 360.0f;
        while (angle > 180.0f) angle -= 360.0f;
        return angle;
    }

    private void smoothTravelLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float yawDiff = wrapAngle(targetYaw - client.player.getYRot());
        float pitchDiff = targetPitch - client.player.getXRot();
        float maxYawSpeed = Math.min(12.0f, Math.max(3.0f, Math.abs(yawDiff) * 0.35f));
        float maxPitchSpeed = Math.min(8.0f, Math.max(2.0f, Math.abs(pitchDiff) * 0.35f));
        applyGCDRotation(client, client.player.getYRot() + Math.max(-maxYawSpeed, Math.min(maxYawSpeed, yawDiff)), client.player.getXRot() + Math.max(-maxPitchSpeed, Math.min(maxPitchSpeed, pitchDiff)));
    }

    private void executeAdaptiveAttack(Minecraft client) {
        long currentTime = System.currentTimeMillis();
        if (currentMobFirstHitTime == 0) currentMobFirstHitTime = currentTime;
        boolean isBoss = (currentTime - currentMobFirstHitTime) > 5000; 
        
        if (!isBoss) {
            if (currentTime >= nextClickTime) {
                injectHardwareClick(client);
                nextClickTime = currentTime + 300 + random.nextInt(60); 
            }
        } else {
            if (!announcedBossMode) { sendMessage(client, "§e[MobileMiner] Tank detected! Burst Mode."); announcedBossMode = true; }
            if (burstClicksRemaining > 0 && currentTime >= nextClickTime) {
                injectHardwareClick(client); burstClicksRemaining--;
                nextClickTime = currentTime + 50 + random.nextInt(30); 
            } else if (currentTime >= burstCooldownEndTime) {
                burstClicksRemaining = 3 + random.nextInt(4); 
                burstCooldownEndTime = currentTime + 140 + random.nextInt(80);
                nextClickTime = currentTime;
            }
        }
    }

    private void injectHardwareClick(Minecraft client) {
        try {
            Field f = KeyMapping.class.getDeclaredField("clickCount"); f.setAccessible(true);
            f.setInt(client.options.keyAttack, f.getInt(client.options.keyAttack) + 1);
        } catch (Exception e) {}
    }

    private boolean isStandingOnStairOrSlab(Minecraft client) {
        BlockPos f = client.player.blockPosition(); BlockState sf = client.level.getBlockState(f), sb = client.level.getBlockState(f.below());
        return sf.getBlock() instanceof StairBlock || sf.getBlock() instanceof SlabBlock || sb.getBlock() instanceof StairBlock || sb.getBlock() instanceof SlabBlock;
    }

    private void navigateWithStrafing(Minecraft client, BlockPos waypoint, double dx, double dz) {
        applyWASDKeys(client, (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f); client.options.keySprint.setDown(true);
        if ((waypoint.getY() > client.player.getY() || client.player.horizontalCollision) && canJump(client) && !isStandingOnStairOrSlab(client)) client.options.keyJump.setDown(true);
    }

    private void directStrafeChase(Minecraft client, Entity mob) {
        applyWASDKeys(client, (float) Math.toDegrees(Math.atan2(mob.getZ() - client.player.getZ(), mob.getX() - client.player.getX())) - 90.0f); client.options.keySprint.setDown(true);
        if (client.player.horizontalCollision && canJump(client) && !isStandingOnStairOrSlab(client)) client.options.keyJump.setDown(true);
    }

    private void applyWASDKeys(Minecraft client, float moveYaw) {
        float diff = wrapAngle(moveYaw - client.player.getYRot());
        client.options.keyUp.setDown(diff > -60 && diff < 60);
        client.options.keyDown.setDown(diff < -120 || diff > 120);
        client.options.keyLeft.setDown(diff < -30 && diff > -150);
        client.options.keyRight.setDown(diff > 30 && diff < 150);
    }

    private boolean canJump(Minecraft client) {
        return client.level.getBlockState(new BlockPos((int)client.player.getX(), (int)client.player.getY() + 2, (int)client.player.getZ())).isAir(); 
    }

    private Entity scanForBestClusterMob(Minecraft client, double range) {
        Entity best = null; double bestScore = Double.MAX_VALUE;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e != client.player && isTargetValid(e, client) && client.player.distanceTo(e) < range) {
                double score = client.player.distanceTo(e);
                if (score < bestScore) { bestScore = score; best = e; }
            }
        }
        return best;
    }

    private boolean hasClearLineOfSight(Minecraft client, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end); if (dist < 2.5) return true;
        int steps = (int) Math.ceil(dist * 2);
        for (int i = 1; i < steps; i++) {
            if (client.level.getBlockState(new BlockPos((int)(start.x + ((end.x - start.x)/steps) * i), (int)(start.y + ((end.y - start.y)/steps) * i), (int)(start.z + ((end.z - start.z)/steps) * i))).isSolidRender()) return false;
        }
        return true; 
    }

    private Entity getRealFleshEntity(Minecraft client, Entity scanned) {
        if (scanned.getClass().getSimpleName().contains("ArmorStand")) {
            for (Entity e : client.level.getEntities(scanned, scanned.getBoundingBox().inflate(1.5))) {
                if (e instanceof LivingEntity && !e.getClass().getSimpleName().contains("ArmorStand") && ((LivingEntity) e).getHealth() > 0) return e;
            }
        }
        return scanned;
    }

    private boolean isTargetValid(Entity entity, Minecraft client) {
        if (entity.isRemoved() || deadEntityBlacklist.containsKey(entity.getId()) || unreachableEntityBlacklist.containsKey(entity.getId())) return false;
        Entity flesh = getRealFleshEntity(client, entity);
        if (flesh == null || (flesh instanceof LivingEntity && ((LivingEntity) flesh).getHealth() <= 0)) return false;
        String name = entity.getName().getString().toLowerCase();
        for (String target : targetMobs) if (name.contains(target)) return true;
        return false;
    }

    private void stopMovement(Minecraft client) {
        client.options.keyUp.setDown(false); client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false); client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false); client.options.keySprint.setDown(false); client.options.keyAttack.setDown(false);
    }
    private void sendMessage(Minecraft client, String text) { if (client.player != null) client.player.sendSystemMessage(Component.literal(text)); }
}
