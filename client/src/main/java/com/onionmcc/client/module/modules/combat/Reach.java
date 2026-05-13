package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.mapping.ClassMapping;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

public class Reach extends Module {

    private final Setting<Double> minReach = addSetting(
            Setting.ofNumber("MinReach", "Minimum reach distance", 3.0, 3.0, 6.0, 0.05));
    private final Setting<Double> maxReach = addSetting(
            Setting.ofNumber("MaxReach", "Maximum reach distance", 3.5, 3.0, 6.0, 0.05));
    private final Setting<Double> chance = addSetting(
            Setting.ofNumber("Chance", "Chance to apply reach per tick", 100, 0, 100, 1));

    public Reach() {
        super("Reach", "Dynamic tick-based reach buffer", ModuleCategory.COMBAT, 0);
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    private long lastClick = 0L;

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        Object player = mc.getPlayer();
        if (player == null) return;

        if (mc.isMouseButtonDown(0)) {
            long now = System.currentTimeMillis();
            if (now - lastClick > 250) { // Limit to 4 CPS for the explicit reach hit so we don't spam packets
                double c = chance.getValue();
                if (c >= 100 || (Math.random() * 100) < c) {
                    double lo = minReach.getValue();
                    double hi = maxReach.getValue();
                    double mid = (lo + hi) / 2.0;
                    double range = (hi - lo) / 2.0;
                    
                    java.util.Random rand = new java.util.Random();
                    double g = rand.nextGaussian() * 0.4;
                    g = Math.max(-1.0, Math.min(1.0, g));
                    double tickReach = mid + range * g;
                    
                    Object target = findTargetInCrosshair(mc, player, tickReach);
                    if (target != null && mc.distanceTo(target) > 3.0) {
                        mc.runOnMainThread(() -> {
                            mc.swingItem(player);
                            mc.attackEntity(target);
                        });
                        lastClick = now;
                    }
                }
            }
        } else {
            lastClick = 0L;
        }
    }

    private Object findTargetInCrosshair(MinecraftAccessor mc, Object player, double reach) {
        float currentYaw = mc.getEntityYaw(player);
        float currentPitch = mc.getEntityPitch(player);
        
        double dirX = -Math.sin(Math.toRadians(currentYaw)) * Math.cos(Math.toRadians(currentPitch));
        double dirY = -Math.sin(Math.toRadians(currentPitch));
        double dirZ = Math.cos(Math.toRadians(currentYaw)) * Math.cos(Math.toRadians(currentPitch));
        
        double pX = mc.getEntityPosX(player);
        double pY = mc.getEntityPosY(player) + 1.62; // eye height
        double pZ = mc.getEntityPosZ(player);
        
        Object bestTarget = null;
        double bestDist = reach;
        
        java.util.List<Object> entities = mc.getLoadedEntities();
        com.onionmcc.client.module.Module teams = OnionMCC.getInstance().getModuleManager().getModule("Teams");
        
        for (Object entity : entities) {
            if (entity == player || mc.isEntityDead(entity) || mc.getEntityHealth(entity) <= 0) continue;
            if (teams != null && teams.isEnabled() && mc.isOnSameTeam(player, entity)) continue;
            
            double eX = mc.getEntityPosX(entity);
            double eY = mc.getEntityPosY(entity);
            double eZ = mc.getEntityPosZ(entity);
            
            // Basic AABB check
            double minX = eX - 0.4;
            double maxX = eX + 0.4;
            double minY = eY;
            double maxY = eY + 1.9;
            double minZ = eZ - 0.4;
            double maxZ = eZ + 0.4;
            
            // Expand by hitbox padding
            double expand = com.onionmcc.client.module.modules.combat.HitBox.getExpansion();
            minX -= expand; maxX += expand;
            minY -= expand; maxY += expand;
            minZ -= expand; maxZ += expand;

            double distToEntity = mc.distanceTo(entity);
            if (distToEntity > reach) continue;

            // Simplified raycast intersection logic
            for (double t = 0; t <= reach; t += 0.1) {
                double rayX = pX + dirX * t;
                double rayY = pY + dirY * t;
                double rayZ = pZ + dirZ * t;
                
                if (rayX >= minX && rayX <= maxX && rayY >= minY && rayY <= maxY && rayZ >= minZ && rayZ <= maxZ) {
                    if (distToEntity < bestDist) {
                        bestDist = distToEntity;
                        bestTarget = entity;
                    }
                    break;
                }
            }
        }
        return bestTarget;
    }
}
