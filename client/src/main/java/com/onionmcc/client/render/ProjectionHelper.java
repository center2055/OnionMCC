package com.onionmcc.client.render;

import com.onionmcc.client.minecraft.MinecraftAccessor;

import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * Projects world-space entities into 2D overlay primitives.
 */
public final class ProjectionHelper {

    private static final double PLAYER_EYE_HEIGHT = 1.62;
    private static volatile String lastProjectionFailure = "none";

    private ProjectionHelper() {
    }

    public static OverlayRenderer.EspBox projectEspBox(MinecraftAccessor mc, Object player, Object entity) {
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();
        if (display == null || display.width <= 0 || display.height <= 0) {
            return null;
        }
        float partialTicks = mc.getPartialTicks();
        double entityX = mc.getEntityLastTickPosX(entity) + ((mc.getEntityPosX(entity) - mc.getEntityLastTickPosX(entity)) * partialTicks);
        double entityY = mc.getEntityLastTickPosY(entity) + ((mc.getEntityPosY(entity) - mc.getEntityLastTickPosY(entity)) * partialTicks);
        double entityZ = mc.getEntityLastTickPosZ(entity) + ((mc.getEntityPosZ(entity) - mc.getEntityLastTickPosZ(entity)) * partialTicks);
        double halfWidth = getEntityHalfWidth(mc, entity);
        double height = getEntityHeight(mc, entity);

        double[][] corners = {
                { entityX - halfWidth, entityY, entityZ - halfWidth },
                { entityX + halfWidth, entityY, entityZ - halfWidth },
                { entityX - halfWidth, entityY, entityZ + halfWidth },
                { entityX + halfWidth, entityY, entityZ + halfWidth },
                { entityX - halfWidth, entityY + height, entityZ - halfWidth },
                { entityX + halfWidth, entityY + height, entityZ - halfWidth },
                { entityX - halfWidth, entityY + height, entityZ + halfWidth },
                { entityX + halfWidth, entityY + height, entityZ + halfWidth }
        };

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int visibleCorners = 0;

        for (double[] corner : corners) {
            Point2D.Double screen = worldToScreen(mc, player, corner[0], corner[1], corner[2], display);
            if (screen == null) {
                continue;
            }

            visibleCorners++;
            minX = Math.min(minX, screen.x);
            minY = Math.min(minY, screen.y);
            maxX = Math.max(maxX, screen.x);
            maxY = Math.max(maxY, screen.y);
        }

        if (visibleCorners < 2 || !Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return null;
        }

        double boxWidth = maxX - minX;
        double boxHeight = maxY - minY;
        if (boxWidth < 4.0 || boxHeight < 8.0) {
            return null;
        }

        double health = mc.getEntityHealth(entity);
        double maxHealth = Math.max(1.0, mc.getEntityMaxHealth(entity));

        return new OverlayRenderer.EspBox(
                minX,
                minY,
                boxWidth,
                boxHeight,
                clamp(health / maxHealth),
                mc.getEntityName(entity),
                getColor(mc, entity));
    }

    public static OverlayRenderer.TracerLine projectTracer(MinecraftAccessor mc, Object player, Object entity) {
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();
        if (display == null || display.width <= 0 || display.height <= 0) {
            return null;
        }

        float partialTicks = mc.getPartialTicks();
        double entityX = mc.getEntityLastTickPosX(entity) + ((mc.getEntityPosX(entity) - mc.getEntityLastTickPosX(entity)) * partialTicks);
        double entityY = mc.getEntityLastTickPosY(entity) + ((mc.getEntityPosY(entity) - mc.getEntityLastTickPosY(entity)) * partialTicks);
        double entityZ = mc.getEntityLastTickPosZ(entity) + ((mc.getEntityPosZ(entity) - mc.getEntityLastTickPosZ(entity)) * partialTicks);

        Point2D.Double center = worldToScreen(mc, player,
                entityX,
                entityY + (getEntityHeight(mc, entity) * 0.5),
                entityZ,
                display);
        if (center == null) {
            return null;
        }

        return new OverlayRenderer.TracerLine(
                display.width / 2.0,
                display.height / 2.0,
                center.x,
                center.y,
                getColor(mc, entity));
    }

    private static Point2D.Double worldToScreen(MinecraftAccessor mc, Object player, double worldX, double worldY,
            double worldZ, DisplayAccess.Snapshot display) {
        double[] modelView = new double[16];
        double[] projection = new double[16];
        int[] viewport = new int[4];
        if (!mc.getActiveRenderInfo(modelView, projection, viewport)) {
            lastProjectionFailure = "active_render_info_unavailable";
            return null;
        }

        float partialTicks = mc.getPartialTicks();
        double localX = mc.getEntityLastTickPosX(player) + ((mc.getEntityPosX(player) - mc.getEntityLastTickPosX(player)) * partialTicks);
        double localY = mc.getEntityLastTickPosY(player) + ((mc.getEntityPosY(player) - mc.getEntityLastTickPosY(player)) * partialTicks);
        double localZ = mc.getEntityLastTickPosZ(player) + ((mc.getEntityPosZ(player) - mc.getEntityLastTickPosZ(player)) * partialTicks);

        double relX = worldX - localX;
        double relY = worldY - localY;
        double relZ = worldZ - localZ;

        double[] transformed = new double[] {
                modelView[0] * relX + modelView[4] * relY + modelView[8] * relZ + modelView[12],
                modelView[1] * relX + modelView[5] * relY + modelView[9] * relZ + modelView[13],
                modelView[2] * relX + modelView[6] * relY + modelView[10] * relZ + modelView[14],
                modelView[3] * relX + modelView[7] * relY + modelView[11] * relZ + modelView[15]
        };
        double[] clip = new double[] {
                projection[0] * transformed[0] + projection[4] * transformed[1] + projection[8] * transformed[2] + projection[12] * transformed[3],
                projection[1] * transformed[0] + projection[5] * transformed[1] + projection[9] * transformed[2] + projection[13] * transformed[3],
                projection[2] * transformed[0] + projection[6] * transformed[1] + projection[10] * transformed[2] + projection[14] * transformed[3],
                projection[3] * transformed[0] + projection[7] * transformed[1] + projection[11] * transformed[2] + projection[15] * transformed[3]
        };
        if (clip[3] <= 0.0) {
            lastProjectionFailure = "clip_w_non_positive";
            return null;
        }

        double nx = clip[0] / clip[3];
        double ny = clip[1] / clip[3];
        double sx = viewport[0] + ((nx * 0.5 + 0.5) * viewport[2]);
        double sy = viewport[1] + viewport[3] - ((ny * 0.5 + 0.5) * viewport[3]);

        double viewportWidth = Math.max(1.0, viewport[2]);
        double viewportHeight = Math.max(1.0, viewport[3]);
        double screenX = sx * (display.width / viewportWidth);
        double screenY = sy * (display.height / viewportHeight);

        lastProjectionFailure = "ok";
        return new Point2D.Double(screenX, screenY);
    }

    private static double getEntityHeight(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? 1.8 : 1.6;
    }

    private static double getEntityHalfWidth(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? 0.35 : 0.3;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Color getColor(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? new Color(64, 255, 160, 220) : new Color(255, 190, 64, 220);
    }

    public static String getLastProjectionFailure() {
        return lastProjectionFailure;
    }
}
