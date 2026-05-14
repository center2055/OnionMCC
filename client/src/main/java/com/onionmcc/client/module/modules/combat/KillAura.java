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
    private final Setting<Boolean> targetPlayers = addSetting(
            Setting.ofBoolean("Players", "Target players", true));
    private final Setting<Boolean> targetMobs = addSetting(
            Setting.ofBoolean("Mobs", "Target mobs and animals", false));
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
                
                boolean isPlayer = mc.isPlayerEntity(entity);
                boolean isMob = mc.isLivingEntity(entity) && !isPlayer;
                
                if (!((targetPlayers.getValue() && isPlayer) || (targetMobs.getValue() && isMob))) {
                    continue;
                }

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
                targetAps += (Math.random() - 0.5) * 6.0;
                targetAps = Math.max(2.0, Math.min(20.0, targetAps));
                
                double meanTicks = 20.0 / targetAps;
                int baseTicks = (int) Math.floor(meanTicks);
                if (Math.random() < (meanTicks - baseTicks)) {
                    baseTicks++;
                }
                if (Math.random() < 0.40) {
                    baseTicks += (Math.random() > 0.5 ? 1 : (baseTicks > 1 ? -1 : 2));
                }
                baseTicks = Math.max(1, baseTicks);
                nextAttackDelay = baseTicks * 50L;
                
                final Object targetToAttack = currentTarget;
                mc.runOnMainThread(() -> {
                    try {
                        mc.attackEntity(targetToAttack);
                        mc.swingItem(player);
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {
        }
    }
    
    private int trackingTicks = 0;
    private int overshootRecoveryTicks = 0;
    private long lastAttackMs = 0L;
    private float postAttackYawOffset = 0f;
    private float postAttackPitchOffset = 0f;

    private void faceEntity(MinecraftAccessor mc, Object player, Object target) {
        trackingTicks++;
        double currentYOffset = 1.2 + Math.random() * 0.4;
        double targetY = mc.getEntityPosY(target) + currentYOffset;

        double diffX = mc.getEntityPosX(target) - mc.getEntityPosX(player);
        double diffY = targetY - (mc.getEntityPosY(player) + 1.62);
        double diffZ = mc.getEntityPosZ(target) - mc.getEntityPosZ(player);
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));
        
        float currentYaw = mc.getEntityYaw(player);
        float currentPitch = mc.getEntityPitch(player);
        
        float yawDiff = wrapAngle(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        
        if (mc.getLeftClickCounter() == 0 && mc.isMouseButtonDown(0)) {
             lastAttackMs = System.currentTimeMillis();
             postAttackYawOffset = (float) ((Math.random() - 0.5) * 2.0);
             postAttackPitchOffset = (float) ((Math.random() - 0.5) * 1.0);
        }

        float absYaw = Math.abs(yawDiff);
        float absPitch = Math.abs(pitchDiff);
        float distAng = (float) Math.sqrt(absYaw * absYaw + absPitch * absPitch);

        float yawSpeed = 0.6f;
        float pitchSpeed = 0.6f;

        float gainHiYaw   = 0.014F + yawSpeed   * 0.125F;
        float gainLoYaw   = 0.005F + yawSpeed   * 0.038F;
        float gainHiPitch = 0.014F + pitchSpeed * 0.125F;
        float gainLoPitch = 0.005F + pitchSpeed * 0.038F;
        
        float pivot = 11.0F;
        float yawBlend   = absYaw   / (absYaw   + pivot);
        float pitchBlend = absPitch / (absPitch + pivot);
        float yawGain   = gainLoYaw   + (gainHiYaw   - gainLoYaw)   * yawBlend;
        float pitchGain = gainLoPitch + (gainHiPitch - gainLoPitch) * pitchBlend;

        float rampMultiplier = 1.0F;
        if (trackingTicks == 1) rampMultiplier = 0.30F + (float)Math.random() * 0.12F;
        else if (trackingTicks == 2) rampMultiplier = 0.55F + (float)Math.random() * 0.12F;
        else if (trackingTicks == 3) rampMultiplier = 0.78F + (float)Math.random() * 0.10F;
        else if (trackingTicks == 4) rampMultiplier = 0.92F + (float)Math.random() * 0.06F;
        
        yawGain   *= rampMultiplier;
        pitchGain *= rampMultiplier;

        float settle = Math.max(0.0F, Math.min(1.0F, (distAng - 1.0F) / 4.0F));

        if (overshootRecoveryTicks > 0) {
            overshootRecoveryTicks--;
            yawGain *= 0.55F;
            pitchGain *= 0.55F;
        }

        float yawStep = yawDiff * yawGain;
        float pitchStep = pitchDiff * pitchGain;

        float maxStep = 6.0F + yawSpeed * 32.0F;
        if (yawStep > maxStep)  yawStep = maxStep;
        if (yawStep < -maxStep) yawStep = -maxStep;
        float maxStepP = 4.0F + pitchSpeed * 22.0F;
        if (pitchStep > maxStepP)  pitchStep = maxStepP;
        if (pitchStep < -maxStepP) pitchStep = -maxStepP;

        float randAmount = 2.0F;
        if (randAmount > 0 && settle > 0F) {
            float yawNoise = ((float)Math.random() - 0.5F) * randAmount * 0.04F * settle;
            float pitchNoise = ((float)Math.random() - 0.5F) * randAmount * 0.03F * settle;
            yawStep += yawNoise;
            pitchStep += pitchNoise;
        }

        if (settle > 0F) {
            float wobble = 1.0F - 0.04F * settle + 0.08F * settle * (float)Math.random();
            yawStep *= wobble;
            pitchStep *= wobble;
        }

        long nowMs = System.currentTimeMillis();
        long sinceAttack = nowMs - lastAttackMs;
        if (lastAttackMs > 0L && sinceAttack >= 0L && sinceAttack < 280L) {
            float decay = 1.0F - (sinceAttack / 280.0F);
            float driftEase = decay * decay;
            yawStep   += postAttackYawOffset   * driftEase * 0.06F;
            pitchStep += postAttackPitchOffset * driftEase * 0.06F;
        }

        float f = mc.getMouseSensitivity() * 0.6f + 0.2f;
        float f1 = f * f * f * 8.0f;
        
        int mouseDeltaX = (int) Math.round(yawStep / (float)((double)f1 * 0.15D));
        int mouseDeltaY = (int) Math.round(pitchStep / (float)((double)f1 * 0.15D));
        
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
