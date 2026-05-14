package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

public class LegitScaffold extends Module {

    private final Setting<Double> shiftTime = addSetting(Setting.ofNumber("ShiftTime", "Time to hold shift (ms)", 140, 50, 300, 10));
    private final Setting<Boolean> autoPlace = addSetting(Setting.ofBoolean("AutoPlace", "Automatically place blocks", true));

    private long shiftStartTime = 0L;
    private long lastPlaceTime = 0L;
    private boolean isShifting = false;

    public LegitScaffold() {
        super("LegitScaffold", "Safewalk + AutoBridge logic", ModuleCategory.PLAYER, 0);
    }

    @Override
    protected void onEnable() {
        isShifting = false;
        shiftStartTime = 0L;
    }

    @Override
    protected void onDisable() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc != null && isShifting) {
            mc.runOnMainThread(() -> mc.setKeyBindState("keyBindSneak", "af", "field_74311_E", false));
            isShifting = false;
        }
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;

        Object player = mc.getPlayer();
        if (player == null) return;

        if (!mc.isEntityOnGround(player)) {
            unShift(mc);
            return;
        }

        float pitch = mc.getEntityPitch(player);
        if (pitch < 70.0f) {
            unShift(mc);
            return;
        }
        
        double motionX = mc.getEntityMotionX(player);
        double motionZ = mc.getEntityMotionZ(player);
        
        // Extrapolate slightly ahead of the player's movement to detect the edge before walking off
        double x = mc.getEntityPosX(player) + motionX * 1.5;
        double y = mc.getEntityPosY(player) - 0.5D;
        double z = mc.getEntityPosZ(player) + motionZ * 1.5;

        boolean overAir = mc.isAirBlock(Math.floor(x), Math.floor(y), Math.floor(z));

        if (overAir) {
            if (!isShifting) {
                shiftStartTime = System.currentTimeMillis();
                isShifting = true;
                mc.runOnMainThread(() -> mc.setKeyBindState("keyBindSneak", "af", "field_74311_E", true));
            }
            
            if (autoPlace.getValue() && mc.isLookingAtBlock()) {
                if (System.currentTimeMillis() - lastPlaceTime > 100) {
                    mc.runOnMainThread(() -> mc.simulateClick(false));
                    lastPlaceTime = System.currentTimeMillis();
                }
            }
        } else {
            if (isShifting && System.currentTimeMillis() - shiftStartTime >= shiftTime.getValue()) {
                unShift(mc);
            }
        }
    }
    
    private void unShift(MinecraftAccessor mc) {
        if (isShifting) {
            mc.runOnMainThread(() -> mc.setKeyBindState("keyBindSneak", "af", "field_74311_E", false));
            isShifting = false;
        }
    }
}
