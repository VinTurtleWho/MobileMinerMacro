package com.mobileminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobileMiner implements ModInitializer {
    public static final String MOD_ID = "mobilemacro";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean wasKeyPressed = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing MobileMiner for Mojo Launcher!");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() != null) {
                boolean isPressed = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_O);
                if (isPressed && !wasKeyPressed) { MiningMacro.getInstance().toggle(client); }
                wasKeyPressed = isPressed;
            }
            MiningMacro.getInstance().onTick(client);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("A pest has appeared") || text.contains("A Pest has appeared")) {
                MiningMacro.getInstance().triggerPestProtocol(Minecraft.getInstance());
            }
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("!macro")) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return false;
                try {
                    String[] parts = message.split(" ");
                    if (parts.length >= 2) {
                        String type = parts[1].toLowerCase();
                        if (type.equals("record")) { 
                            MiningMacro.getInstance().startRecording(client); 
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Recording started... Walk your path!"));
                        } 
                        else if (type.equals("stoprecord")) { 
                            MiningMacro.getInstance().stop(client); 
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Recording saved!")); 
                        } 
                        else if (type.equals("chest")) { 
                            MiningMacro.getInstance().setTestingChest(true); 
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Open a chest now to test GUI clicks!")); 
                        }
                        else if (type.equals("pestonly")) { 
                            MiningMacro.getInstance().setPestOnly(true); 
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Mode set to PEST ONLY")); 
                        } 
                        else if (type.equals("farmonly")) { 
                            MiningMacro.getInstance().setPestOnly(false); 
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Mode set to NORMAL FARMING")); 
                        } 
                        else if (type.equals("tool")) { 
                            if (parts.length >= 3) {
                                int slot = Integer.parseInt(parts[2]) - 1; 
                                MiningMacro.getInstance().setToolSlot(slot); 
                                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Tool slot set to " + (slot+1))); 
                            }
                        } 
                        else if (type.equals("vacuum") || type.equals("vaccum")) { 
                            if (parts.length >= 3) {
                                int slot = Integer.parseInt(parts[2]) - 1; 
                                MiningMacro.getInstance().setVacuumSlot(slot); 
                                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Vacuum slot set to " + (slot+1))); 
                            } else {
                                client.player.sendSystemMessage(Component.literal("§e[MobileMiner] Current Vacuum Slot: " + (MiningMacro.getInstance().getVacuumSlot() + 1)));
                            }
                        } 
                        else if (type.equals("target") && parts.length >= 3) {
                            String targetType = parts[2].toLowerCase();
                            MiningMacro.getInstance().setTargetMode(targetType);
                            client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Target Mode set to: " + targetType));
                        }
                        else if (type.equals("end")) {
                            if (parts.length == 2) { 
                                MiningMacro.getInstance().setEndBlock(client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ()); 
                                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] End Block set!")); 
                            } 
                            else if (parts.length == 3 && parts[2].equalsIgnoreCase("clear")) { 
                                MiningMacro.getInstance().setEndBlock(null, null, null); 
                                client.player.sendSystemMessage(Component.literal("§a[MobileMiner] End Block cleared!")); 
                            }
                        } else if (parts.length >= 3) {
                            String valStr = parts[2].toLowerCase();
                            if (type.equals("mode")) { MiningMacro.getInstance().setMode(valStr); client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Farming Mode set to " + valStr)); } 
                            else if (type.equals("yaw")) { MiningMacro.getInstance().setYaw(Float.parseFloat(valStr)); client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Target Yaw set to " + valStr)); } 
                            else if (type.equals("pitch")) { MiningMacro.getInstance().setPitch(Float.parseFloat(valStr)); client.player.sendSystemMessage(Component.literal("§a[MobileMiner] Target Pitch set to " + valStr)); }
                        }
                    }
                } catch (Exception e) { client.player.sendSystemMessage(Component.literal("§c[MobileMiner] Invalid command format!")); }
                return false; 
            }
            return true; 
        });
    }
}
