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

    // Combat Variables
    private long lastClickTime = 0;
    private long mobTargetTime = 0;
    private int clickState = 0; // 0 = idle, 1 = pressed, 2 = released
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
            sendMessage(client, "§c[MobileMiner] Combat Mode Activated. Hunting...");
        } else {
            isCombatActive = false;
            stopMovement(client);
            currentMob = null;
            currentPath = null;
            clickState = 0;
            sendMessage(client, "§7[MobileMiner] Combat Mode Deactivated.");
        }
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !isCombatActive) return;

        // 1. Strict State-Machine Clicker (Fixes the perma-hold glitch)
        if (clickState == 1) {
            client.options.keyAttack.setDown(false); // Force release after 1 tick
            clickState = 2; // Mark as safely released
        }

        // 2. Anti-Stuck Memory Check
        double moveDist = client.player.position().distanceToSqr(lastPlayerPos);
        if (client.options.keyUp.isDown() && moveDist < 0.01) {
            stuckTicks++;
            if (stuckTicks > 15) { // Stuck for nearly 1 second
                client.options.keyJump.setDown(true); // Panic jump
                currentPath = null; // Force recalculation
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastPlayerPos = client.player.position();

        // 3. Find or validate target
        if (currentMob == null || !isTargetValid(currentMob)) {
            if (currentMob != null) stopMovement(client); 
            
            currentMob = scanForClosestMob(client, 30);
            currentPath = null;
            mobTargetTime = System.currentTimeMillis();
        }

        if (currentMob == null) {
            stopMovement(client);
            return;
        }

        double distance = client.player.distanceTo(currentMob);

        // 4. COMBAT BRAIN
        if (distance <= 3.0) {
            stopMovement(client);
            lookAtTorso(client, currentMob);
            executeAdaptiveAttack(client);
            return;
        }

        // 5. PATHFINDING BRAIN
        BlockPos mobPos = currentMob.blockPosition();
        
        if (currentPath == null && !calculatingPath) {
            calculatingPath = true;
            Pathfinder.calculatePathAsync(client, client.player.blockPosition(), mobPos)
                .thenAccept(path -> {
                    currentPath = path;
                    calculatingPath = false;
                });
        }

        if (currentPath != null && !currentPath.isEmpty()) {
            BlockPos nextWaypoint = currentPath.get(0);
            
            // Increased drop radius to 1.5 to prevent edge-clipping
            if (client.player.blockPosition().closerThan(nextWaypoint, 1.5)) {
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
        humanizedLookTowards(client, targetYaw, client.player.getXRot());

        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);

        if (waypoint.getY() > client.player.getY() && client.player.horizontalCollision) {
            client.options.keyJump.setDown(true);
        } else if (stuckTicks == 0) {
            client.options.keyJump.setDown(false);
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
        
        if (client.player.horizontalCollision) {
            client.options.keyJump.setDown(true);
        } else if (stuckTicks == 0) {
            client.options.keyJump.setDown(false);
        }
    }

    private void executeAdaptiveAttack(Minecraft client) {
        long timeAlive = System.currentTimeMillis() - mobTargetTime;
        long currentTime = System.currentTimeMillis();
        boolean isBoss = timeAlive > 1000;
        
        long clickDelay = isBoss ? (120 + random.nextInt(80)) : 400;

        if (currentTime - lastClickTime >= clickDelay && clickState != 1) {
            client.options.keyAttack.setDown(true);
            clickState = 1; // Mark as pressed (will be released next tick)
            lastClickTime = currentTime;
        }
    }

    private void humanizedLookTowards(Minecraft client, float targetYaw, float targetPitch) {
        float currentYaw = client.player.getYRot();
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;

        float currentPitch = client.player.getXRot();
        float pitchDiff = targetPitch - currentPitch;

        // GCD Math from Layer 1 for buttery human aim
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

    // THE ARMOR STAND FIX
    private void lookAtTorso(Minecraft client, Entity entity) {
        Vec3 eyes = client.player.getEyePosition();
        
        double torsoY = entity.getY();
        
        // Check if the entity is an invisible Armor Stand acting as a nametag
        if (entity.getClass().getSimpleName().contains("ArmorStand")) {
            torsoY -= 1.0; // Aim 1 block straight down to hit the actual zombie underneath
        } else {
            torsoY += (entity.getBbHeight() * 0.5); // Normal torso aim
        }
        
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
        if (clickState != 1) client.options.keyAttack.setDown(false);
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

    private boolean isTargetValid(Entity entity) {
        if (entity.isRemoved()) return false;
        
        // Ignore dead entities
        if (entity instanceof LivingEntity && (((LivingEntity) entity).getHealth() <= 0 || ((LivingEntity) entity).isDeadOrDying())) {
            return false;
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
