sed -i 's/state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch);/state = MacroState.ALIGNING; rotationHandler.startRotation(defaultYaw, defaultPitch);\n                if (client.player != null) client.player.sendSystemMessage(Component.literal("§a[MobileMacro] Started! Mode: " + mode));/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

sed -i 's/double dist = Math.sqrt(dx \* dx + dz \* dz);/double dist = Math.sqrt(dx \* dx + dz \* dz);\n        if (dist < 0.05) dist = 0.05; \/\/ PREVENT NaN CRASH/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

sed -i 's/rotationHandler.startRotation(targetYaw, targetPitch);/rotationHandler.updateTarget(targetYaw, targetPitch);/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

