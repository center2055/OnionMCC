package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import java.util.List;

public class SilentAim extends Module {

    private final Setting<Double> fov = addSetting(Setting.ofNumber("FOV", "Aim FOV", 45.0, 1.0, 180.0, 1.0));
    private final Setting<Double> range = addSetting(Setting.ofNumber("Range", "Attack range", 4.0, 1.0, 6.0, 0.1));
    private boolean wasMouseDown = false;

    public SilentAim() {
        super("SilentAim", "Hits targets outside crosshair silently", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {
        wasMouseDown = false;
    }

    @Override
    protected void onDisable() {}

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame() || mc.getCurrentScreen() != null) return;

        boolean isMouseDown = mc.isMouseButtonDown(0); // Left click
        if (isMouseDown && !wasMouseDown) {
            // Just clicked!
            Object player = mc.getPlayer();
            if (player == null) return;

            // Check if we are already hovering an entity, if so let vanilla handle it
            if (mc.getObjectMouseOverEntity() != null) {
                wasMouseDown = true;
                return;
            }

            try {
                List<Object> candidates = mc.getLoadedEntities();
                if (candidates == null) return;
                Object bestTarget = null;
                double bestAngle = fov.getValue();

                for (Object entity : candidates) {
                    if (entity == null || entity == player || mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;
                    if (!mc.isLivingEntity(entity)) continue;
                    
                    double dist = mc.distanceTo(entity);
                    if (dist > range.getValue()) continue;
                    
                    double angle = getAngleDiff(mc, player, entity);
                    if (angle <= bestAngle) {
                        bestAngle = angle;
                        bestTarget = entity;
                    }
                }

                if (bestTarget != null) {
                    final Object target = bestTarget;
                    mc.runOnMainThread(() -> {
                        mc.attackEntity(target);
                        mc.swingItem(player);
                    });
                }
            } catch (Exception ignored) {}
        }
        wasMouseDown = isMouseDown;
    }

    private double getAngleDiff(MinecraftAccessor mc, Object player, Object target) {
        double diffX = mc.getEntityPosX(target) - mc.getEntityPosX(player);
        double diffY = (mc.getEntityPosY(target) + 1.0) - (mc.getEntityPosY(player) + 1.62);
        double diffZ = mc.getEntityPosZ(target) - mc.getEntityPosZ(player);
        
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX*diffX + diffZ*diffZ)));
        
        float currentYaw = mc.getEntityYaw(player);
        float currentPitch = mc.getEntityPitch(player);
        
        float yawDiff = Math.abs(wrapAngle(targetYaw - currentYaw));
        float pitchDiff = Math.abs(targetPitch - currentPitch);
        
        return Math.sqrt(yawDiff*yawDiff + pitchDiff*pitchDiff);
    }

    private float wrapAngle(float angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
