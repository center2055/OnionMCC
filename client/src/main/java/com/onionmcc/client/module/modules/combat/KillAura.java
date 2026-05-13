package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import java.util.List;

public class KillAura extends Module {

    private final Setting<Double> range = addSetting(
            Setting.ofNumber("Range", "Attack range in blocks", 3.5, 1.0, 6.0, 0.1));
    private final Setting<Double> aps = addSetting(
            Setting.ofNumber("APS", "Attacks per second", 10.0, 1.0, 20.0, 0.5));
    private final Setting<String> targetMode = addSetting(
            Setting.ofMode("Targeting", "Targeting style", "Switch", "Single", "Switch", "Multi"));
    private final Setting<Boolean> autoWeapon = addSetting(
            Setting.ofBoolean("AutoWeapon", "Automatically swap to best weapon", true));
    private final Setting<Boolean> cooldown = addSetting(
            Setting.ofBoolean("Cooldown", "Wait for 1.9+ attack cooldown", true));

    private long lastAttack = 0L;
    private long nextAttackDelay = 100L;
    private Object currentTarget = null;

    public KillAura() {
        super("KillAura", "Fractional APS Aura", ModuleCategory.COMBAT, 0x52); 
    }

    @Override
    protected void onEnable() {
        lastAttack = 0L;
        nextAttackDelay = 100L;
        currentTarget = null;
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        Object player = mc.getPlayer();
        if (player == null) return;

        try {
            List<Object> candidates = mc.getLoadedEntities();
            if (candidates == null) return;
            Object bestTarget = null;
            double bestDist = range.getValue();

            for (Object entity : candidates) {
                if (entity == null || entity == player || mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;
                
                // For KillAura, restrict to living entities to avoid hitting armor stands/items
                if (!mc.isLivingEntity(entity)) continue;

                // Ensure it's a living entity (has health) and is not us
                float health = mc.getEntityHealth(entity);
                if (health <= 0) continue;
                
                com.onionmcc.client.module.Module teams = OnionMCC.getInstance().getModuleManager().getModule("Teams");
                if (teams != null && teams.isEnabled() && mc.isOnSameTeam(player, entity)) continue;
                
                String entityName = mc.getEntityName(entity);
                if (com.onionmcc.client.module.modules.player.Friends.isFriend(entityName)) continue;
                
                double dist = mc.distanceTo(entity);
                if (dist <= bestDist) {
                    bestDist = dist;
                    bestTarget = entity;
                }
            }

            if (targetMode.getValue().equals("Single") && currentTarget != null && candidates.contains(currentTarget) && !mc.isEntityDead(currentTarget) && mc.distanceTo(currentTarget) <= range.getValue()) {
                // Maintain lock
            } else {
                currentTarget = bestTarget;
            }

            if (currentTarget == null) return;
            
            // Aim at target (basic rotation)
            faceEntity(mc, player, currentTarget);

            // 1.9+ Combat Cooldown Support
            String version = OnionMCC.getInstance().getMappingManager().getDetectedVersion();
            if (!version.startsWith("1.8") && cooldown.getValue()) {
                float strength = mc.getCooledAttackStrength(player);
                if (strength >= 1.0f) {
                    mc.runOnMainThread(() -> mc.simulateClick(true));
                }
                return; // Let the cooldown dictate the APS
            }

            long now = System.currentTimeMillis();
            if (now - lastAttack >= nextAttackDelay) {
                lastAttack = now;
                
                double targetAps = aps.getValue();
                double meanDelayMs = 1000.0 / targetAps;
                
                // Pure Gaussian delay for the threaded clicker
                java.util.Random rand = new java.util.Random();
                double sigma = 0.20 + Math.random() * 0.15;
                double delayMs = meanDelayMs * Math.exp(sigma * rand.nextGaussian());
                
                nextAttackDelay = Math.max(10L, Math.round(delayMs));
                
                // Bypass 50ms tick quantization by firing the click asynchronously
                // We use Thread.sleep to exactly nail the fractional millisecond timing before jumping back to main thread
                long sleepTime = Math.max(1L, nextAttackDelay - (System.currentTimeMillis() - now));
                new Thread(() -> {
                    try {
                        Thread.sleep(sleepTime);
                        mc.simulateClick(true);
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception ignored) {
        }
    }
    
    private void faceEntity(MinecraftAccessor mc, Object player, Object target) {
        double diffX = mc.getEntityPosX(target) - mc.getEntityPosX(player);
        // Add random offsets to avoid aiming perfectly center (Aim Constant bypass)
        double randomOffsetY = (Math.random() - 0.5) * 0.4;
        double diffY = (mc.getEntityPosY(target) + 1.0 + randomOffsetY) - (mc.getEntityPosY(player) + 1.62);
        double diffZ = mc.getEntityPosZ(target) - mc.getEntityPosZ(player);
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        
        float currentYaw = mc.getEntityYaw(player);
        float currentPitch = mc.getEntityPitch(player);
        
        float yawDiff = wrapAngle(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        
        // Easing curve (acceleration) instead of instant snapping to fix Vulcan Aim Constant/Linear and Matrix Killaura Aim
        float absYaw = Math.abs(yawDiff);
        // Ramp up speed based on distance to target angle. Creates a smooth deceleration curve.
        float smoothSpeed = 0.1f + (absYaw / (absYaw + 15.0f)) * 0.5f; 
        
        float f = mc.getMouseSensitivity() * 0.6f + 0.2f;
        float f1 = f * f * f * 8.0f;
        
        int mouseDeltaX = (int) Math.round((yawDiff * smoothSpeed) / (float)((double)f1 * 0.15D));
        int mouseDeltaY = (int) Math.round((pitchDiff * smoothSpeed) / (float)((double)f1 * 0.15D));
        
        float finalYaw = currentYaw + (float)((double)((float)mouseDeltaX * f1) * 0.15D);
        float finalPitch = currentPitch + (float)((double)((float)mouseDeltaY * f1) * 0.15D);
        
        mc.runOnMainThread(() -> {
            mc.setEntityYaw(player, finalYaw);
            mc.setEntityPitch(player, finalPitch);
        });
    }
    
    private float wrapAngle(float angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
