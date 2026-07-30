# Equip vacuum as soon as it locks onto a target
sed -i 's/currentTarget = scanForTarget(client);/currentTarget = scanForTarget(client); if(currentTarget != null) equipSlot(client, vacuumSlot);/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

# Stop the bot from holding right-click if the target is further than 3.5 blocks to prevent Reach bans
sed -i 's/InputController.setPressed(client.options.keyUse, true);/if(client.player.distanceTo(currentTarget) <= 3.5) InputController.setPressed(client.options.keyUse, true); else InputController.setPressed(client.options.keyUse, false);/' src/main/java/com/mobilemacrofarm/FarmingMacro.java
