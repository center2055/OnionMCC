package com.onionmcc.client.render;

import com.onionmcc.client.minecraft.MinecraftAccessor;

import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * Projects world-space entities into 2D overlay primitives.
 */
public final class ProjectionHelper {

    private static final double PLAYER_EYE_HEIGHT = 1.62;

    private ProjectionHelper() {
    }

    public static OverlayRenderer.EspBox projectEspBox(MinecraftAccessor mc, Object player, Object entity) {
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();
        if (display == null || display.width <= 0 || display.height <= 0 || !display.active) {
            return null;
        }

        Point2D.Double foot = worldToScreen(mc, player,
                mc.getEntityPosX(entity),
                mc.getEntityPosY(entity),
                mc.getEntityPosZ(entity),
                display);
        Point2D.Double head = worldToScreen(mc, player,
                mc.getEntityPosX(entity),
                mc.getEntityPosY(entity) + getEntityHeight(mc, entity),
                mc.getEntityPosZ(entity),
                display);

        if (foot == null || head == null) {
            return null;
        }

        double height = Math.abs(foot.y - head.y);
        if (height < 6.0) {
            return null;
        }

        double width = Math.max(5.0, height * 0.42);
        double top = Math.min(head.y, foot.y);
        double left = head.x - (width / 2.0);
        double health = mc.getEntityHealth(entity);
        double maxHealth = Math.max(1.0, mc.getEntityMaxHealth(entity));

        return new OverlayRenderer.EspBox(
                left,
                top,
                width,
                height,
                clamp(health / maxHealth),
                mc.getEntityName(entity),
                getColor(mc, entity));
    }

    public static OverlayRenderer.TracerLine projectTracer(MinecraftAccessor mc, Object player, Object entity) {
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();
        if (display == null || display.width <= 0 || display.height <= 0 || !display.active) {
            return null;
        }

        Point2D.Double center = worldToScreen(mc, player,
                mc.getEntityPosX(entity),
                mc.getEntityPosY(entity) + (getEntityHeight(mc, entity) * 0.5),
                mc.getEntityPosZ(entity),
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
        double camX = mc.getEntityPosX(player);
        double camY = mc.getEntityPosY(player) + PLAYER_EYE_HEIGHT;
        double camZ = mc.getEntityPosZ(player);

        double dx = worldX - camX;
        double dy = worldY - camY;
        double dz = worldZ - camZ;

        double yaw = Math.toRadians(-mc.getEntityYaw(player));
        double pitch = Math.toRadians(mc.getEntityPitch(player));

        double x = dz * Math.sin(yaw) - dx * Math.cos(yaw);
        double z = dz * Math.cos(yaw) + dx * Math.sin(yaw);

        double y = dy;
        double y2 = (y * Math.cos(pitch)) + (z * Math.sin(pitch));
        double z2 = (z * Math.cos(pitch)) - (y * Math.sin(pitch));

        if (z2 < 0.1) {
            return null;
        }

        double fov = Math.max(30.0, Math.min(170.0, mc.getFovSetting()));
        double focalLength = display.width / (2.0 * Math.tan(Math.toRadians(fov / 2.0)));

        double screenX = (display.width / 2.0) + ((x / z2) * focalLength);
        double screenY = (display.height / 2.0) - ((y2 / z2) * focalLength);

        if (screenX < (-display.width * 0.5) || screenX > (display.width * 1.5)
                || screenY < (-display.height * 0.5) || screenY > (display.height * 1.5)) {
            return null;
        }

        return new Point2D.Double(screenX, screenY);
    }

    private static double getEntityHeight(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? 1.8 : 1.6;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Color getColor(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? new Color(64, 255, 160, 220) : new Color(255, 190, 64, 220);
    }
}
