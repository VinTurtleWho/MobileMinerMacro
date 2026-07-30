# Inject the wandering variables
sed -i 's/private net.minecraft.world.entity.Entity currentTarget = null;/private net.minecraft.world.entity.Entity currentTarget = null;\n    private float aimOffsetX = 0f;\n    private float aimOffsetY = 0f;\n    private int aimTicks = 0;/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

# Update the aimAt method to use the wandering offset
sed -i 's/double dx = target.getX() - client.player.getX();/aimTicks++; if (aimTicks > 10) { aimOffsetX = (float)((Math.random() - 0.5) * 0.6); aimOffsetY = (float)((Math.random() - 0.5) * 0.8); aimTicks = 0; }\n        double dx = target.getX() - client.player.getX() + aimOffsetX;/' src/main/java/com/mobilemacrofarm/FarmingMacro.java
sed -i 's/double dy = (target.getY() + target.getEyeHeight() \/ 2.0) - client.player.getEyeY();/double dy = (target.getY() + target.getEyeHeight() \/ 2.0) - client.player.getEyeY() + aimOffsetY;/' src/main/java/com/mobilemacrofarm/FarmingMacro.java
sed -i 's/double dz = target.getZ() - client.player.getZ();/double dz = target.getZ() - client.player.getZ() + aimOffsetX;/' src/main/java/com/mobilemacrofarm/FarmingMacro.java

