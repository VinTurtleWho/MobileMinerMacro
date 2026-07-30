package com.mobileminer;

import com.mobileminer.input.InputController;
import com.mobileminer.rotation.RotationHandler;
import com.mobileminer.state.MacroState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MiningMacro {
    private static final MiningMacro INSTANCE = new MiningMacro();
    private MacroState state = MacroState.IDLE;
    private final RotationHandler rotationHandler = new RotationHandler();
    private float defaultYaw = 45.0f;
    private float defaultPitch = 0.0f;
    private String mode = "cane"; 
    private boolean pestOnlyMode = false;
    private String targetMode = "pest"; 
    
    private int toolSlot = 0;
    private int vacuumSlot = 1;
    private boolean isMovingLeft = true;
    private boolean isMovingForward = true; 
    private int stuckTicks = 0;
    private int guiDelayTicks = 0;
    private int guiTimeoutTicks = 0; 
    private final List<TickRecord> recordedPath = new ArrayList<>();
    private int replayIndex = 0;
    private Integer endX = null;
    private Integer endY = null;
    private Integer endZ = null;
    private int preRestartWait = 0;
    private int targetPreRestartWait = 20;
    private int restartTicks = 0;

    // Aimbot Tolerance Variables
    private float lastTargetYaw = 0.0f;
    private float lastTargetPitch = 0.0f;

    // Chest Stealer GUI Tester Variables
    private boolean testingChest = false;
    private int chestSlotIndex = 0;

    private final String[] PEST_NAMES = {"Beetle", "Fly", "Cricket", "Locust", "Rat", "Mosquito", "Earthworm", "Mite", "Moth", "Slug", "Firefly", "Dragonfly", "Mantis", "Mouse"};
    private net.minecraft.world.entity.Entity currentTarget = null;

    public static MiningMacro getInstance() { return INSTANCE; }
    public void setYaw(float yaw) { this.defaultYaw = yaw; }
    public void setPitch(float pitch) { this.defaultPitch = pitch; }
    public void setMode(String newMode) { this.mode = newMode; }
    public void setPestOnly(boolean val) { this.pestOnlyMode = val; }
    public void setTargetMode(String mode) { this.targetMode = mode; }
    public void setTestingChest(boolean val) { this.testingChest = val; this.chestSlotIndex = 0; this.guiDelayTicks = 10; }
    public void setToolSlot(int slot) { this.toolSlot = slot; }
    public void setVacuumSlot(int slot) { this.vacuumSlot = slot; }
    public int getVacuumSlot() { return this.vacuumSlot; }
    public void setEndBlock(Integer x, Integer y, Integer z) { this.endX = x; this.endY = y; this.endZ = z; }

    public void toggle(Minecraft client) {
        if (state == MacroState.IDLE) {
            if (pestOnlyMode) {
                state = MacroState.PEST_HUNTING; 
                if (client.player != null) client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Started PEST ONLY Mode! Target: " + targetMode));
            } else {
                state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch);
                if (client.player != null) client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Started! Mode: " + mode));
            }
        } else { stop(client); }
    }

    public void stop(Minecraft client) {
        state = MacroState.IDLE; InputController.releaseAll(client);
        testingChest = false; stuckTicks = 0; restartTicks = 0; preRestartWait = 0; guiDelayTicks = 0; guiTimeoutTicks = 0; currentTarget = null;
        if (client.player != null) client.player.sendSystemMessage(Component.literal("§c[MobileMiner] Stopped!"));
    }

    public void triggerPestProtocol(Minecraft client) {
        if (state == MacroState.IDLE || pestOnlyMode) return; 
        InputController.releaseAll(client);
        if (client.player.connection != null) {
            client.player.connection.sendCommand("setspawnlocation"); client.player.connection.sendCommand("desk");
        }
        state = MacroState.WAITING_FOR_DESK; guiTimeoutTicks = 0;
    }

    private void equipSlot(Minecraft client, int slot) {
        if (client.player == null) return;
        try {
            Field field = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("selected");
            field.setAccessible(true); field.set(client.player.getInventory(), slot);
        } catch (Exception e) {
            try {
                Field obfField = net.minecraft.world.entity.player.Inventory.class.getDeclaredField("field_7545");
                obfField.setAccessible(true); obfField.set(client.player.getInventory(), slot);
            } catch (Exception ex) { }
        }
    }

    private void safeClick(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
        try {
            for (Method m : client.gameMode.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 5 && p[0] == int.class && p[1] == int.class && p[2] == int.class && p[3].isEnum() && net.minecraft.world.entity.player.Player.class.isAssignableFrom(p[4])) {
                    Object action = null;
                    for (Object ec : p[3].getEnumConstants()) {
                        String n = ec.toString();
                        if (n.equals("QUICK_MOVE") || n.equals("SHIFT_CLICK")) { action = ec; break; }
                    }
                    if (action != null) { m.invoke(client.gameMode, screen.getMenu().containerId, slot.index, 0, action, client.player); }
                    break;
                }
            }
        } catch (Exception e) { }
    }

    @SuppressWarnings("unchecked")
    private List<Component> getSafeTooltip(ItemStack stack, Minecraft client) {
        try {
            for (Method m : stack.getClass().getMethods()) {
                if (m.getName().equals("getTooltipLines")) {
                    if (m.getParameterCount() == 3) {
                        Class<?> ctxClass = Class.forName("net.minecraft.world.item.Item$TooltipContext");
                        Method ofMethod = ctxClass.getMethod("of", net.minecraft.world.level.Level.class);
                        Object context = ofMethod.invoke(null, client.level);
                        return (List<Component>) m.invoke(stack, context, client.player, TooltipFlag.Default.NORMAL);
                    } else if (m.getParameterCount() == 2) { return (List<Component>) m.invoke(stack, client.player, TooltipFlag.Default.NORMAL); }
                }
            }
        } catch (Exception e) { } return new ArrayList<>();
    }

    public void startRecording(Minecraft client) { recordedPath.clear(); state = MacroState.RECORDING; }
    public void startReplay(Minecraft client) {
        if (recordedPath.isEmpty()) return;
        InputController.releaseAll(client); replayIndex = 0; state = MacroState.REPLAYING;
    }

    private net.minecraft.world.entity.Entity scanForTarget(Minecraft client) {
        net.minecraft.world.entity.Entity closest = null; double minDistance = 9999.0;
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            boolean isValid = false;
            if (targetMode.equals("player")) {
                if (entity instanceof net.minecraft.world.entity.player.Player && entity != client.player) isValid = true;
            } else {
                if (entity.hasCustomName()) {
                    String name = entity.getCustomName().getString();
                    for (String p : PEST_NAMES) { if (name.contains(p)) { isValid = true; break; } }
                }
            }
            if (isValid) {
                double dist = client.player.distanceToSqr(entity);
                if (dist < minDistance) { minDistance = dist; closest = entity; }
            }
        }
        return closest;
    }

    private void aimAt(Minecraft client, net.minecraft.world.entity.Entity target) {
        double dx = target.getX() - client.player.getX();
        double dy = (target.getY() + target.getEyeHeight() / 2.0) - client.player.getEyeY();
        double dz = target.getZ() - client.player.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        
        float exactYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float exactPitch = (float) -(Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

        if (horizDist > 25.0) {
            // Lazy Aim: Look generally towards them, but keep head level
            lastTargetYaw = exactYaw;
            lastTargetPitch = 0.0f; 
        } else {
            // Precision Aim: Lock onto the exact body coordinates
            lastTargetYaw = exactYaw;
            lastTargetPitch = exactPitch;
        }
        rotationHandler.updateTarget(lastTargetYaw, lastTargetPitch);
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (testingChest) {
            if (client.screen instanceof AbstractContainerScreen) {
                guiDelayTicks--;
                if (guiDelayTicks <= 0) {
                    AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.screen;
                    int containerSlots = screen.getMenu().slots.size() - 36;
                    while (chestSlotIndex < containerSlots && !screen.getMenu().slots.get(chestSlotIndex).hasItem()) { chestSlotIndex++; }
                    if (chestSlotIndex < containerSlots) {
                        Slot slot = screen.getMenu().slots.get(chestSlotIndex);
                        safeClick(client, screen, slot);
                        chestSlotIndex++;
                        guiDelayTicks = 4 + (int)(Math.random() * 5); 
                    } else {
                        client.player.closeContainer(); testingChest = false;
                        if (client.player != null) client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Chest Steal Complete!"));
                    }
                }
            }
            return;
        }

        if (state == MacroState.IDLE) return;
        boolean isGuiState = (state == MacroState.WAITING_FOR_DESK || state == MacroState.DESK_DELAY || state == MacroState.WAITING_FOR_PLOT);
        if (client.screen != null && !isGuiState) { InputController.releaseAll(client); return; }

        switch (state) {
            case RECORDING: handleRecording(client); break;
            case REPLAYING: handleReplaying(client); break;
            case WAITING_FOR_DESK:
                guiTimeoutTicks++;
                if (client.screen instanceof AbstractContainerScreen) { guiDelayTicks = 10 + (int)(Math.random() * 10); state = MacroState.DESK_DELAY; } 
                else if (guiTimeoutTicks > 60) { state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch); }
                break;
            case DESK_DELAY:
                guiDelayTicks--;
                if (guiDelayTicks <= 0 && client.screen instanceof AbstractContainerScreen) {
                    AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.screen; boolean found = false;
                    for (Slot slot : screen.getMenu().slots) {
                        if (slot.getItem().getHoverName().getString().contains("Configure Plot")) { safeClick(client, screen, slot); found = true; break; }
                    }
                    if (found) { guiDelayTicks = 15 + (int)(Math.random() * 10); state = MacroState.WAITING_FOR_PLOT; } 
                    else { client.player.closeContainer(); state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch); }
                }
                break;
            case WAITING_FOR_PLOT:
                guiDelayTicks--;
                if (guiDelayTicks <= 0 && client.screen instanceof AbstractContainerScreen) {
                    AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.screen; int targetPlot = -1;
                    for (Slot slot : screen.getMenu().slots) {
                        ItemStack stack = slot.getItem(); String name = stack.getHoverName().getString();
                        if (name.contains("Plot")) {
                            boolean hasPest = false; List<Component> lore = getSafeTooltip(stack, client);
                            for (Component comp : lore) { if (comp.getString().contains("Pests:")) { hasPest = true; break; } }
                            if (hasPest) { String cleaned = name.replaceAll("[^0-9]", ""); if (!cleaned.isEmpty()) { targetPlot = Integer.parseInt(cleaned); break; } }
                        }
                    }
                    client.player.closeContainer(); 
                    if (targetPlot != -1) {
                        if (client.player.connection != null) client.player.connection.sendCommand("tptoplot " + targetPlot);
                        equipSlot(client, vacuumSlot);
                        state = MacroState.PEST_HUNTING; 
                    } else { state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch); }
                }
                break;
            case PEST_HUNTING:
                rotationHandler.updateRotation(client); 
                if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) {
                    InputController.setPressed(client.options.keyUse, false);
                    InputController.setPressed(client.options.keyUp, false);
                    InputController.setPressed(client.options.keyJump, false);
                    InputController.setPressed(client.options.keyShift, false);
                    currentTarget = scanForTarget(client);
                    if (currentTarget == null) {
                        if (!pestOnlyMode) {
                            if (client.player != null) client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Plot is clean! Returning to farm..."));
                            if (client.player.connection != null) client.player.connection.sendCommand("warp garden");
                            state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch);
                        }
                    } else { equipSlot(client, vacuumSlot); }
                    return;
                }

                if (currentTarget != null) {
                    aimAt(client, currentTarget);

                    // 1. Vertical Height Adjustment
                    double yDiff = currentTarget.getY() - client.player.getY();
                    if (yDiff > 1.2) {
                        InputController.setPressed(client.options.keyJump, true);
                        InputController.setPressed(client.options.keyShift, false);
                    } else if (yDiff < -1.2) {
                        InputController.setPressed(client.options.keyJump, false);
                        InputController.setPressed(client.options.keyShift, true);
                    } else {
                        InputController.setPressed(client.options.keyJump, false);
                        InputController.setPressed(client.options.keyShift, false);
                    }

                    // 2. Horizontal Distance Adjustment
                    double dx = currentTarget.getX() - client.player.getX();
                    double dz = currentTarget.getZ() - client.player.getZ();
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    if (horizDist > 3.0) { InputController.setPressed(client.options.keyUp, true); } 
                    else { InputController.setPressed(client.options.keyUp, false); }

                    // 3. Vacuum Sucking (10-Degree Jiggle Tolerance Locking)
                    double totalDist = client.player.distanceTo(currentTarget);
                    float currentYaw = client.player.getYRot();
                    float currentPitch = client.player.getXRot();
                    
                    float yawDiff = Math.abs(currentYaw - lastTargetYaw) % 360.0f;
                    if (yawDiff > 180.0f) yawDiff = 360.0f - yawDiff;
                    float pitchDiff = Math.abs(currentPitch - lastTargetPitch);
                    
                    boolean isWithinTolerance = (yawDiff <= 10.0f && pitchDiff <= 10.0f);
                    
                    if (totalDist <= 5.0 && isWithinTolerance) {
                        InputController.setPressed(client.options.keyUse, true);
                    } else {
                        InputController.setPressed(client.options.keyUse, false);
                    }
                }
                break;
            case ALIGNING: if (rotationHandler.updateRotation(client)) { equipSlot(client, toolSlot); state = MacroState.FARMING; } break;
            case FARMING: handleFarming(client); break;
            case SHIFTING_LANE: handleLaneShift(client); break;
            case RESTARTING: handleRestart(client); break;
            case PEST_ONLY_MODE: break;
        }
    }

    private void handleRecording(Minecraft client) {
        TickRecord record = new TickRecord(
            client.options.keyUp.isDown(), client.options.keyLeft.isDown(), client.options.keyDown.isDown(), client.options.keyRight.isDown(),
            client.options.keyJump.isDown(), client.options.keyShift.isDown(), client.options.keyAttack.isDown(), client.options.keyUse.isDown(),
            client.player.getYRot(), client.player.getXRot()
        ); recordedPath.add(record);
    }

    private void handleReplaying(Minecraft client) {
        if (replayIndex >= recordedPath.size()) {
            InputController.releaseAll(client); if (client.player.connection != null) client.player.connection.sendCommand("setspawnlocation");
            state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch); return;
        }
        TickRecord frame = recordedPath.get(replayIndex);
        InputController.setPressed(client.options.keyUp, frame.w); InputController.setPressed(client.options.keyLeft, frame.a);
        InputController.setPressed(client.options.keyDown, frame.s); InputController.setPressed(client.options.keyRight, frame.d);
        InputController.setPressed(client.options.keyJump, frame.space); InputController.setPressed(client.options.keyShift, frame.sneak); 
        InputController.setPressed(client.options.keyAttack, false); InputController.setPressed(client.options.keyUse, false);
        client.player.setYRot(frame.yaw); client.player.setXRot(frame.pitch); replayIndex++;
    }

    private void handleFarming(Minecraft client) {
        if (endX != null && endY != null && endZ != null) {
            if (client.player.getBlockX() == endX && client.player.getBlockY() == endY && client.player.getBlockZ() == endZ) {
                if (!recordedPath.isEmpty()) startReplay(client);
                else { state = MacroState.RESTARTING; preRestartWait = 0; restartTicks = 0; targetPreRestartWait = 15 + (int)(Math.random() * 25); }
                return;
            }
        }
        InputController.setPressed(client.options.keyAttack, true);
        if (mode.equals("mushroom")) {
            InputController.setPressed(client.options.keyLeft, false); InputController.setPressed(client.options.keyRight, false);
            InputController.setPressed(client.options.keyUp, isMovingForward); InputController.setPressed(client.options.keyDown, !isMovingForward);
        } else if (mode.equals("cane")) {
            InputController.setPressed(client.options.keyLeft, false); InputController.setPressed(client.options.keyDown, false);
            InputController.setPressed(client.options.keyUp, isMovingForward); InputController.setPressed(client.options.keyRight, !isMovingForward);
        } else if (mode.equals("melon")) {
            InputController.setPressed(client.options.keyDown, false); InputController.setPressed(client.options.keyUp, true);
            InputController.setPressed(client.options.keyLeft, isMovingLeft); InputController.setPressed(client.options.keyRight, !isMovingLeft);
        }
        double speed = client.player.getDeltaMovement().horizontalDistanceSqr();
        if (speed < 0.001) { stuckTicks++; if (stuckTicks > 5) { stuckTicks = 0; state = MacroState.SHIFTING_LANE; } } else { stuckTicks = 0; }
    }

    private void handleLaneShift(Minecraft client) {
        InputController.releaseAll(client);
        if (mode.equals("mushroom") || mode.equals("cane")) { isMovingForward = !isMovingForward; } 
        else if (mode.equals("melon")) { isMovingLeft = !isMovingLeft; }
        state = MacroState.FARMING;
    }

    private void handleRestart(Minecraft client) {
        InputController.releaseAll(client); preRestartWait++;
        if (preRestartWait == targetPreRestartWait) {
            if (client.player.connection != null) client.player.connection.sendCommand("warp garden");
        } else if (preRestartWait > targetPreRestartWait) {
            restartTicks++;
            if (restartTicks > 100) { restartTicks = 0; preRestartWait = 0; state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch); }
        }
    }
    public MacroState getState() { return state; }
}
