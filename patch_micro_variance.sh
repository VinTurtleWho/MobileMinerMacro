# Replace the hardcoded 0.20f with a dynamic smoothing variable (18% to 23%)
sed -i 's/float stepYaw = Math.max(-maxTurn, Math.min(maxTurn, yawDiff \* 0.20f));/float dynamicSmoothing = 0.18f + (float)(Math.random() \* 0.05f);\n        float stepYaw = Math.max(-maxTurn, Math.min(maxTurn, yawDiff * dynamicSmoothing));/' src/main/java/com/mobilemacrofarm/rotation/RotationHandler.java

sed -i 's/float stepPitch = Math.max(-maxTurn, Math.min(maxTurn, pitchDiff \* 0.20f));/float stepPitch = Math.max(-maxTurn, Math.min(maxTurn, pitchDiff * dynamicSmoothing));/' src/main/java/com/mobilemacrofarm/rotation/RotationHandler.java
