package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import java.lang.reflect.Field;

public class TriggerBot extends Module {

    private final Setting<Double> range = addSetting(Setting.ofNumber("Range", "Maximum range", 3.0, 1.0, 6.0, 0.1));
    private final Setting<Double> minDelay = addSetting(Setting.ofNumber("MinDelay", "Min extra ticks", 0.0, -5.0, 5.0, 1.0));
    private final Setting<Double> maxDelay = addSetting(Setting.ofNumber("MaxDelay", "Max extra ticks", 2.0, -5.0, 5.0, 1.0));
    private final Setting<Boolean> reqMouseDown = addSetting(Setting.ofBoolean("RequireMouseDown", "Only trigger when clicking", false));
    private final Setting<Boolean> cooldown = addSetting(Setting.ofBoolean("Cooldown", "Wait for 1.9+ attack cooldown", true));

    private long lastTrigger = 0L;
    private long nextTriggerDelay = 100L;

    public TriggerBot() {
        super("TriggerBot", "Attack-strength scaled targeting", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {
        lastTrigger = 0L;
        nextTriggerDelay = generateDelay();
    }

    @Override
    protected void onDisable() {}

    private long generateDelay() {
        double minT = minDelay.getValue();
        double maxT = maxDelay.getValue();
        double ticks = minT + Math.random() * (maxT - minT);
        // Base delay of ~100ms (10 CPS) + extra ticks
        return (long) (100.0 + Math.max(0, ticks * 50.0));
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;

        if (reqMouseDown.getValue() && !mc.isMouseButtonDown(0)) return;

        Object val = mc.getObjectMouseOverEntity();
        if (val == null) return;

        try {
            double dist = mc.distanceTo(val);
            if (dist > range.getValue() || mc.isEntityDead(val)) return;
            
            float health = mc.getEntityHealth(val);
            if (health <= 0) return;

            com.onionmcc.client.module.Module teams = OnionMCC.getInstance().getModuleManager().getModule("Teams");
            if (teams != null && teams.isEnabled() && mc.isOnSameTeam(mc.getPlayer(), val)) return;

            String entityName = mc.getEntityName(val);
            if (com.onionmcc.client.module.modules.player.Friends.isFriend(entityName)) return;

            long now = System.currentTimeMillis();
            String version = OnionMCC.getInstance().getMappingManager().getDetectedVersion();
            
            if (!version.startsWith("1.8") && cooldown.getValue()) {
                if (mc.getCooledAttackStrength(mc.getPlayer()) >= 1.0f) {
                    mc.runOnMainThread(() -> {
                        mc.setLeftClickCounter(0);
                        mc.simulateClick(true);
                    });
                }
                return;
            }

            if (now - lastTrigger >= nextTriggerDelay) {
                lastTrigger = now;
                
                // Advanced AntiCheat bypass: Prevent StdDev 0.0 using Thread + Gaussian
                double meanDelayMs = generateDelay(); // our base delay config
                java.util.Random rand = new java.util.Random();
                double sigma = 0.20 + Math.random() * 0.15;
                double delayMs = meanDelayMs * Math.exp(sigma * rand.nextGaussian());
                
                nextTriggerDelay = Math.max(10L, Math.round(delayMs));
                long sleepTime = nextTriggerDelay;

                new Thread(() -> {
                    try {
                        Thread.sleep(sleepTime);
                        mc.setLeftClickCounter(0);
                        mc.simulateClick(true);
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception ignored) {}
    }
}
