package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import java.util.List;

public class SilentKiller extends Module {
    private final Setting<Double> range = addSetting(Setting.ofNumber("Range", "Attack range", 4.0, 1.0, 6.0, 0.1));
    private final Setting<Double> aps = addSetting(Setting.ofNumber("APS", "Attacks per second", 12.0, 1.0, 20.0, 0.5));
    private long lastAttack = 0L;

    public SilentKiller() {
        super("SilentKiller", "Attacks entities without aiming", ModuleCategory.COMBAT, 0);
    }
    
    @Override
    protected void onEnable() {
        lastAttack = 0L;
    }

    @Override
    protected void onDisable() {}

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        Object player = mc.getPlayer();
        if (player == null) return;
        
        long now = System.currentTimeMillis();
        long delay = (long)(1000.0 / aps.getValue());
        if (now - lastAttack < delay) return;

        try {
            List<Object> candidates = mc.getLoadedEntities();
            if (candidates == null) return;
            Object bestTarget = null;
            double bestDist = range.getValue();

            for (Object entity : candidates) {
                if (entity == null || entity == player || mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;
                if (!mc.isLivingEntity(entity)) continue;
                if (mc.getEntityHealth(entity) <= 0) continue;
                
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

            if (bestTarget != null) {
                lastAttack = now;
                final Object target = bestTarget;
                
                double targetAps = aps.getValue();
                double meanDelayMs = 1000.0 / targetAps;
                java.util.Random rand = new java.util.Random();
                double sigma = 0.20 + Math.random() * 0.15;
                double delayMs = meanDelayMs * Math.exp(sigma * rand.nextGaussian());
                long sleepTime = Math.max(1L, Math.round(delayMs));

                new Thread(() -> {
                    try {
                        Thread.sleep(sleepTime);
                        mc.swingItem(player);
                        mc.attackEntity(target);
                    } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception ignored) {}
    }
}
