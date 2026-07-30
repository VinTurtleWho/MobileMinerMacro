sed -i '/private void safeClick/,/^\    }/c\
    private void safeClick(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {\
        try {\
            for (Method m : client.gameMode.getClass().getMethods()) {\
                Class<?>[] p = m.getParameterTypes();\
                if (p.length == 5 && p[0] == int.class && p[1] == int.class && p[2] == int.class && p[3].isEnum() && net.minecraft.world.entity.player.Player.class.isAssignableFrom(p[4])) {\
                    Object action = null;\
                    for (Object ec : p[3].getEnumConstants()) {\
                        String n = ec.toString();\
                        if (n.equals("QUICK_MOVE") || n.equals("SHIFT_CLICK")) { action = ec; break; }\
                    }\
                    if (action != null) {\
                        m.invoke(client.gameMode, screen.getMenu().containerId, slot.index, 0, action, client.player);\
                    }\
                    break;\
                }\
            }\
        } catch (Exception e) { }\
    }' src/main/java/com/mobilemacrofarm/FarmingMacro.java
