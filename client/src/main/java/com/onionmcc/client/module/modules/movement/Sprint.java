package com.onionmcc.client.module.modules.movement;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

/**
 * Sprint keeps sprinting enabled while the player is moving in game.
 */
public class Sprint extends Module {

    private final Setting<Boolean> omni = addSetting(
            Setting.ofBoolean("Omnidirectional", "Sprint in all directions", false));

    public Sprint() {
        super("Sprint", "Automatically sprints while moving", ModuleCategory.MOVEMENT, 0);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        Object player = mc.getPlayer();
        if (player != null) {
            mc.setSprinting(player, false);
        }
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame())
            return;

        Object player = mc.getPlayer();
        if (player == null)
            return;

        double motionX = mc.getEntityMotionX(player);
        double motionZ = mc.getEntityMotionZ(player);
        boolean moving = Math.abs(motionX) > 0.01 || Math.abs(motionZ) > 0.01;

        if (!moving) {
            return;
        }

        if (!omni.getValue()) {
            float yaw = mc.getEntityYaw(player);
            double forward = -Math.sin(Math.toRadians(yaw)) * motionX + Math.cos(Math.toRadians(yaw)) * motionZ;
            if (forward <= 0.01) {
                return;
            }
        }

        mc.setSprinting(player, true);
    }
}
