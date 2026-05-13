package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

/**
 * Velocity — legitimately jump-resets to reduce knockback and avoid Simulation flags.
 */
public class Velocity extends Module {

    private final Setting<Double> chance = addSetting(
            Setting.ofNumber("Chance", "Chance to jump-reset", 80.0, 0.0, 100.0, 5.0));

    private boolean wasJumping = false;

    public Velocity() {
        super("Velocity", "Legitimately jump-resets on damage", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {
        wasJumping = false;
    }

    @Override
    protected void onDisable() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc != null && wasJumping) {
            mc.runOnMainThread(() -> mc.setKeyBindState("keyBindJump", "Y", "field_74314_A", false));
            wasJumping = false;
        }
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame() || !mc.isMinecraftWindowActive()) return;

        Object player = mc.getPlayer();
        if (player == null) return;

        int hurtTime = mc.getEntityHurtTime(player);
        
        if (hurtTime == 9 && mc.isEntityOnGround(player)) { // Peak hurt time
            if (Math.random() * 100.0 < chance.getValue()) {
                mc.runOnMainThread(() -> mc.setKeyBindState("keyBindJump", "Y", "field_74314_A", true));
                wasJumping = true;
            }
        }

        // Release jump immediately after getting off ground
        if (wasJumping && !mc.isEntityOnGround(player)) {
            mc.runOnMainThread(() -> mc.setKeyBindState("keyBindJump", "Y", "field_74314_A", false));
            wasJumping = false;
        }
    }
}
