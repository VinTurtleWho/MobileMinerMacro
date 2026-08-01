package com.mobileminer.planning;

import com.mobileminer.core.BotContext;
import com.mobileminer.core.BotPhase;
import com.mobileminer.core.BotTask;
import com.mobileminer.perception.PlayerSnapshot;
import com.mobileminer.perception.WorldSnapshot;

public class TaskPlanner {
    // Temporary test threshold for eye-to-center distance transition
    private static final double TEST_NAV_DISTANCE_THRESHOLD = 3.5;

    public void evaluate(BotContext context, PlayerSnapshot playerSnap, WorldSnapshot worldSnap) {
        if (playerSnap == null || worldSnap == null) return;

        // If no target block in search volume, return to IDLE / SEARCHING
        if (worldSnap.closestTarget == null) {
            context.setTask(BotTask.IDLE);
            context.setPhase(BotPhase.SEARCHING);
            return;
        }

        // Target found! Switch to MINING task
        context.setTask(BotTask.MINING);

        // Evaluate phase based on distance
        if (worldSnap.closestTarget.distance > TEST_NAV_DISTANCE_THRESHOLD) {
            context.setPhase(BotPhase.NAVIGATING);
        } else {
            context.setPhase(BotPhase.AIMING);
        }
    }
}
