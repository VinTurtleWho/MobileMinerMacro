package com.mobileminer.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class BotContext {
    private BotState currentState = BotState.IDLE;
    private BlockPos currentTargetBlock = null;
    private Entity currentTargetEntity = null;
    private String lastError = "";

    public void transitionTo(BotState newState) {
        this.currentState = newState;
    }

    public BotState getCurrentState() {
        return currentState;
    }

    public void setTargetBlock(BlockPos pos) {
        this.currentTargetBlock = pos;
    }

    public BlockPos getTargetBlock() {
        return currentTargetBlock;
    }

    public void setTargetEntity(Entity entity) {
        this.currentTargetEntity = entity;
    }

    public Entity getTargetEntity() {
        return currentTargetEntity;
    }

    public void setError(String errorMsg) {
        this.lastError = errorMsg;
        transitionTo(BotState.ERROR);
    }
}
