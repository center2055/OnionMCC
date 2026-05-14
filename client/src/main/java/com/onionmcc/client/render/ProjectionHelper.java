package com.onionmcc.client.render;

import com.onionmcc.client.minecraft.MinecraftAccessor;

import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * Projects world-space entities into 2D overlay primitives.
 */
public final class ProjectionHelper {

    private ProjectionHelper() {
    }

    public static Frame captureFrame(MinecraftAccessor mc, Object player) {
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();
        if (display == null || display.width <= 0 || display.height <= 0 || !display.active) {
            return null;
        }

        double[] modelView = new double[16];
        double[] projection = new double[16];
        int[] viewport = new int[4];
        if (!mc.getActiveRenderInfo(modelView, projection, viewport)) {
            return null;
        }

        float partialTicks = mc.getPartialTicks();
        double localX = mc.getEntityLastTickPosX(player) + ((mc.getEntityPosX(player) - mc.getEntityLastTickPosX(player)) * partialTicks);
        double localY = mc.getEntityLastTickPosY(player) + ((mc.getEntityPosY(player) - mc.getEntityLastTickPosY(player)) * partialTicks);
        double localZ = mc.getEntityLastTickPosZ(player) + ((mc.getEntityPosZ(player) - mc.getEntityLastTickPosZ(player)) * partialTicks);

        return new Frame(display, modelView, projection, viewport, partialTicks, localX, localY, localZ);
    }

    public static OverlayRenderer.EspBox projectEspBox(MinecraftAccessor mc, Frame frame, Object entity, Color color) {
        if (frame == null) {
            return null;
        }

        double entityX = mc.getEntityLastTickPosX(entity) + ((mc.getEntityPosX(entity) - mc.getEntityLastTickPosX(entity)) * frame.partialTicks);
        double entityY = mc.getEntityLastTickPosY(entity) + ((mc.getEntityPosY(entity) - mc.getEntityLastTickPosY(entity)) * frame.partialTicks);
        double entityZ = mc.getEntityLastTickPosZ(entity) + ((mc.getEntityPosZ(entity) - mc.getEntityLastTickPosZ(entity)) * frame.partialTicks);
        double height = getEntityHeight(mc, entity);

        Point2D.Double feet = worldToScreen(frame, entityX, entityY, entityZ);
        Point2D.Double head = worldToScreen(frame, entityX, entityY + height, entityZ);
        if (feet == null || head == null) {
            return null;
        }

        double boxHeight = Math.abs(feet.y - head.y);
        if (!Double.isFinite(boxHeight) || boxHeight < 8.0) {
            return null;
        }
        double boxWidth = Math.max(6.0, boxHeight * 0.42);
        double centerX = (feet.x + head.x) * 0.5;
        double top = Math.min(feet.y, head.y);

        double health = mc.getEntityHealth(entity);
        double maxHealth = Math.max(1.0, mc.getEntityMaxHealth(entity));

        return new OverlayRenderer.EspBox(
                centerX - (boxWidth * 0.5),
                top,
                boxWidth,
                boxHeight,
                clamp(health / maxHealth),
                mc.getEntityName(entity),
                color);
    }

    public static OverlayRenderer.TracerLine projectTracer(MinecraftAccessor mc, Frame frame, Object entity,
            double originX, double originY, Color color) {
        if (frame == null) {
            return null;
        }

        double entityX = mc.getEntityLastTickPosX(entity) + ((mc.getEntityPosX(entity) - mc.getEntityLastTickPosX(entity)) * frame.partialTicks);
        double entityY = mc.getEntityLastTickPosY(entity) + ((mc.getEntityPosY(entity) - mc.getEntityLastTickPosY(entity)) * frame.partialTicks);
        double entityZ = mc.getEntityLastTickPosZ(entity) + ((mc.getEntityPosZ(entity) - mc.getEntityLastTickPosZ(entity)) * frame.partialTicks);

        Point2D.Double center = worldToScreen(frame,
                entityX,
                entityY + (getEntityHeight(mc, entity) * 0.5),
                entityZ);
        if (center == null) {
            return null;
        }

        return new OverlayRenderer.TracerLine(
                originX,
                originY,
                center.x,
                center.y,
                color);
    }

    private static Point2D.Double worldToScreen(Frame frame, double worldX, double worldY, double worldZ) {
        double relX = worldX - frame.localX;
        double relY = worldY - frame.localY;
        double relZ = worldZ - frame.localZ;
        double[] modelView = frame.modelView;
        double[] projection = frame.projection;
        int[] viewport = frame.viewport;

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
            return null;
        }

        double nx = clip[0] / clip[3];
        double ny = clip[1] / clip[3];
        double sx = viewport[0] + ((nx * 0.5 + 0.5) * viewport[2]);
        double sy = viewport[1] + viewport[3] - ((ny * 0.5 + 0.5) * viewport[3]);

        double viewportWidth = Math.max(1.0, viewport[2]);
        double viewportHeight = Math.max(1.0, viewport[3]);
        double screenX = sx * (frame.display.width / viewportWidth);
        double screenY = sy * (frame.display.height / viewportHeight);

        return new Point2D.Double(screenX, screenY);
    }

    private static double getEntityHeight(MinecraftAccessor mc, Object entity) {
        return mc.isPlayerEntity(entity) ? 1.8 : 1.6;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static final class Frame {
        private final DisplayAccess.Snapshot display;
        private final double[] modelView;
        private final double[] projection;
        private final int[] viewport;
        private final float partialTicks;
        private final double localX;
        private final double localY;
        private final double localZ;

        private Frame(DisplayAccess.Snapshot display, double[] modelView, double[] projection, int[] viewport,
                float partialTicks, double localX, double localY, double localZ) {
            this.display = display;
            this.modelView = modelView;
            this.projection = projection;
            this.viewport = viewport;
            this.partialTicks = partialTicks;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
        }

        public int getScreenWidth() {
            return display.width;
        }

        public int getScreenHeight() {
            return display.height;
        }
    }
}
