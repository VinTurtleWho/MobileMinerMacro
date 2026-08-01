package com.mobileminer.core;

public class BotContext {
    private BotTask currentTask = BotTask.IDLE;
    private BotPhase currentPhase = BotPhase.SEARCHING;
    private String lastError = "";

    public void setTask(BotTask task) { this.currentTask = task; }
    public BotTask getTask() { return currentTask; }

    public void setPhase(BotPhase phase) { this.currentPhase = phase; }
    public BotPhase getPhase() { return currentPhase; }

    public void setError(String errorMsg) { this.lastError = errorMsg; }
    public String getLastError() { return lastError; }
}
