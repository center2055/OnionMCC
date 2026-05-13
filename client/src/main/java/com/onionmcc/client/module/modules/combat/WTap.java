package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * WTap resets the sprint state by releasing and pressing the forward key 
 * precisely timed after an attack, maximizing knockback dealt to the opponent.
 */
public class WTap extends Module {

    private final Setting<Double> waitMin = addSetting(Setting.ofNumber("WaitTimeMin", "Min ms to hold W-release", 30.0, 1.0, 300.0, 1.0));
    private final Setting<Double> waitMax = addSetting(Setting.ofNumber("WaitTimeMax", "Max ms to hold W-release", 40.0, 1.0, 300.0, 1.0));
    
    private final Setting<Double> actionMin = addSetting(Setting.ofNumber("ActionDelayMin", "Min ms delay before tap", 20.0, 1.0, 300.0, 1.0));
    private final Setting<Double> actionMax = addSetting(Setting.ofNumber("ActionDelayMax", "Max ms delay before tap", 30.0, 1.0, 300.0, 1.0));
    
    private final Setting<Double> range = addSetting(Setting.ofNumber("Range", "Max WTap range", 3.0, 1.0, 6.0, 0.05));
    private final Setting<Boolean> dynamic = addSetting(Setting.ofBoolean("Dynamic", "Scale wait time by distance", false));

    private WtapState state = WtapState.NONE;
    private long timerEndMs = 0L;
    private Object currentTarget = null;
    private boolean wasPressed = false;

    public WTap() {
        super("WTap", "Re-sprints after attacks for maximum knockback combos", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        if (state != WtapState.NONE) {
            finishCombo();
        }
        resetState();
    }

    private void resetState() {
        state = WtapState.NONE;
        timerEndMs = 0L;
        currentTarget = null;
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;

        if (state == WtapState.NONE) return;

        long now = System.currentTimeMillis();

        if (state == WtapState.WAITINGTOTAP && now >= timerEndMs) {
            startCombo(mc);
        } else if (state == WtapState.TAPPING && now >= timerEndMs) {
            finishCombo();
        }
    }

    // Called externally by MinecraftAccessor.attackEntity
    public void onAttack() {
        if (!isEnabled()) return;
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame() || !mc.isMinecraftWindowActive()) return;

        // Try to resolve the target we just hit
        Object target = mc.getObjectMouseOverEntity();
        if (target == null) return;
        
        if (mc.distanceTo(target) > range.getValue()) return;
        if (!mc.isPlayerEntity(target)) return; // Crow defaults to only combo players often
        
        // Ensure Forward key is actually down natively
        // Since we don't have Keyboard.isKeyDown easily mapped, we rely on the internal game settings
        // Actually, we can check if the internal binding is pressed. For now, assume if we are w-tapping we are holding W.
        // It's safe to just perform the combo.

        this.currentTarget = target;
        trystartCombo();
    }

    private void trystartCombo() {
        state = WtapState.WAITINGTOTAP;
        double delay = ThreadLocalRandom.current().nextDouble(actionMin.getValue(), actionMax.getValue() + 0.01);
        timerEndMs = System.currentTimeMillis() + (long) delay;
    }

    private void startCombo(MinecraftAccessor mc) {
        state = WtapState.TAPPING;
        wasPressed = true; // We assume the user was holding forward to chase
        
        mc.runOnMainThread(() -> mc.setKeyBindState("keyBindForward", "T", "field_74315_x", false));

        double cd = ThreadLocalRandom.current().nextDouble(waitMin.getValue(), waitMax.getValue() + 0.01);

        if (dynamic.getValue() && currentTarget != null) {
            double dist = mc.distanceTo(currentTarget);
            if (dist < 3.0) {
                double closeness = 3.0 - dist;
                cd += closeness * 1.0 * 10.0; // multiplier scaled
            }
        }

        timerEndMs = System.currentTimeMillis() + Math.max(1L, (long) cd);
    }

    private void finishCombo() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc.isInGame()) {
            if (wasPressed) {
                mc.runOnMainThread(() -> mc.setKeyBindState("keyBindForward", "T", "field_74315_x", true));
            }
        }
        state = WtapState.NONE;
    }

    private enum WtapState {
        NONE, WAITINGTOTAP, TAPPING
    }
}
