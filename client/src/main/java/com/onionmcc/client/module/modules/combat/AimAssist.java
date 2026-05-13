package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import java.util.List;

public class AimAssist extends Module {

    private final Setting<Double> strength = addSetting(
            Setting.ofNumber("Strength", "Assist strength scaling", 0.06, 0.01, 0.15, 0.01));
    private final Setting<Double> fov = addSetting(
            Setting.ofNumber("FOV", "Max FOV to assist", 60.0, 5.0, 180.0, 5.0));
    private final Setting<Double> range = addSetting(
            Setting.ofNumber("Range", "Max range", 5.0, 1.0, 12.0, 0.5));
    private final Setting<Boolean> attackingOnly = addSetting(
            Setting.ofBoolean("AttackingOnly", "Only assist when holding attack", true));

    private float lastClientYaw = 0f;
    private float lastClientPitch = 0f;
    private boolean hasLastRotation = false;

    public AimAssist() {
        super("AimAssist", "Delta-scaled rotation adjustment", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {
        hasLastRotation = false;
    }

    @Override
    protected void onDisable() {
        hasLastRotation = false;
    }

    private int trackingTicks = 0;
    private int overshootRecoveryTicks = 0;
    private long lastAttackMs = 0L;
    private float postAttackYawOffset = 0f;
    private float postAttackPitchOffset = 0f;
    private Object currentTarget = null;

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) {
            hasLastRotation = false;
            return;
        }

        if (attackingOnly.getValue() && !mc.isMouseButtonDown(0)) {
            hasLastRotation = false;
            trackingTicks = 0;
            return;
        }

        Object player = mc.getPlayer();
        if (player == null) return;

        Object target = findTarget(mc, player);
        if (target == null) {
            currentTarget = null;
            trackingTicks = 0;
            hasLastRotation = false;
            return;
        }

        if (target != currentTarget) {
            trackingTicks = 0;
            currentTarget = target;
        }
        trackingTicks++;

        double targetY = mc.getEntityPosY(target) + 0.9;
        
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

        if (Math.abs(yawDiff) > fov.getValue() / 2.0 || Math.abs(pitchDiff) > fov.getValue() / 2.0) {
            hasLastRotation = false;
            return;
        }
        
        if (mc.getLeftClickCounter() == 0 && mc.isMouseButtonDown(0)) {
             // Attack just happened (heuristically)
             lastAttackMs = System.currentTimeMillis();
             postAttackYawOffset = (float) ((Math.random() - 0.5) * 2.0);
             postAttackPitchOffset = (float) ((Math.random() - 0.5) * 1.0);
        }

        float absYaw = Math.abs(yawDiff);
        float absPitch = Math.abs(pitchDiff);
        float distAng = (float) Math.sqrt(absYaw * absYaw + absPitch * absPitch);

        float yawSpeed = strength.getValue().floatValue() * 6.6f; // Scale up to match crow's 0.01-1.0
        float pitchSpeed = strength.getValue().floatValue() * 6.6f;

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

        float randAmount = 2.0F; // Crow default
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

        // Apply GCD (Mouse Sensitivity scaling) to DELTAS so rotations aren't flagged as Linear/Perfect
        float f = mc.getMouseSensitivity() * 0.6f + 0.2f;
        float f1 = f * f * f * 8.0f;
        
        int mouseDeltaX = (int) Math.round(yawStep / (float)((double)f1 * 0.15D));
        int mouseDeltaY = (int) Math.round(pitchStep / (float)((double)f1 * 0.15D));

        float finalYaw = currentYaw + (float)((double)((float)mouseDeltaX * f1) * 0.15D);
        float finalPitch = currentPitch + (float)((double)((float)mouseDeltaY * f1) * 0.15D);

        mc.setEntityYaw(player, finalYaw);
        mc.setEntityPitch(player, finalPitch);
    }

    private Object findTarget(MinecraftAccessor mc, Object player) {
        List<Object> entities = mc.getWorldPlayers();
        Object closest = null;
        double closestDist = range.getValue();

        for (Object entity : entities) {
            if (entity == player || mc.isEntityDead(entity) || mc.getEntityHealth(entity) <= 0) continue;

            com.onionmcc.client.module.Module teams = OnionMCC.getInstance().getModuleManager().getModule("Teams");
            if (teams != null && teams.isEnabled() && mc.isOnSameTeam(player, entity)) continue;

            String entityName = mc.getEntityName(entity);
            if (com.onionmcc.client.module.modules.player.Friends.isFriend(entityName)) continue;

            double dist = mc.distanceTo(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }

    private float wrapAngle(float angle) {
        angle %= 360;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
