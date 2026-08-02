package com.mobileminer.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import com.mobileminer.perception.*;
import com.mobileminer.planning.TaskPlanner;
import com.mobileminer.control.AimCalculator;
import com.mobileminer.control.DesiredRotation;

public class BotController {
    private final BotContext context;
    private final WorldObserver worldObserver;
    private final PlayerObserver playerObserver; 
    private final TaskPlanner taskPlanner;

    public BotController() {
        this.context = new BotContext();
        this.worldObserver = new WorldObserver();
        this.playerObserver = new PlayerObserver(); 
        this.taskPlanner = new TaskPlanner();
    }

    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        // 1. Perception
        PlayerSnapshot pSnap = playerObserver.getSnapshot(client);
        WorldSnapshot wSnap = worldObserver.getSnapshot(client, "diamond_ore", 5);
        
        // 2. Planning
        taskPlanner.evaluate(context, pSnap, wSnap);

        // 3. Aim Calculation Debug (Runs ONLY when in AIMING phase)
        DesiredRotation desired = null;
        float deltaYaw = 0.0f;
        float deltaPitch = 0.0f;

        if (context.getPhase() == BotPhase.AIMING && wSnap.closestTarget != null) {
            Vec3 eyePos = client.player.getEyePosition();
            Vec3 blockCenter = Vec3.atCenterOf(wSnap.closestTarget.pos);
            
            desired = AimCalculator.calculate(eyePos, blockCenter);
            deltaYaw = AimCalculator.getAngleDifference(pSnap.yaw, desired.yaw);
            deltaPitch = AimCalculator.getAngleDifference(pSnap.pitch, desired.pitch);
        }

        // 4. Debug Output (Every 10 ticks)
        if (pSnap != null && pSnap.tick % 10 == 0) {
            String stateTxt = String.format("§e[%s-%s]", context.getTask(), context.getPhase());
            
            String aimTxt = "§8No Target";
            if (desired != null) {
                aimTxt = String.format("§bTgt:[%.0f, %.0f] §aΔ:[%.0f, %.0f]", 
                    desired.yaw, desired.pitch, deltaYaw, deltaPitch);
            }

            String debugTxt = String.format("%s | %s", stateTxt, aimTxt);
            client.player.sendOverlayMessage(Component.literal(debugTxt));
        }
    }
}
