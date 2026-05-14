package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;
import com.onionmcc.client.render.OverlayRenderer;
import com.onionmcc.client.render.ProjectionHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Tracers extends Module {

    private final Setting<String> origin = addSetting(
            Setting.ofMode("Origin", "Tracer start position", "Bottom", "Top", "Crosshair", "Bottom"));
    private final Setting<Double> nearDistance = addSetting(
            Setting.ofNumber("Near Distance", "Distance for close tracer color", 4.0, 1.0, 24.0, 1.0));
    private final Setting<Double> farDistance = addSetting(
            Setting.ofNumber("Far Distance", "Distance for far tracer color", 48.0, 8.0, 128.0, 1.0));

    public Tracers() {
        super("Tracers", "Draws lines to players", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {
        OverlayRenderer.getInstance().clearTracers();
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) {
            OverlayRenderer.getInstance().clearTracers();
            return;
        }

        Object player = mc.getPlayer();
        List<Object> players = mc.getWorldPlayers();
        Object[] playerSnapshot = players.toArray();
        List<OverlayRenderer.TracerLine> lines = new ArrayList<>();
        ProjectionHelper.Frame frame = ProjectionHelper.captureFrame(mc, player);
        if (frame == null) {
            OverlayRenderer.getInstance().clearTracers();
            return;
        }

        for (Object entity : playerSnapshot) {
            if (entity == player || entity == null) continue;
            if (mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;

            double[] start = getOrigin(frame);
            OverlayRenderer.TracerLine line = ProjectionHelper.projectTracer(mc, frame, entity,
                    start[0],
                    start[1],
                    getDistanceColor(mc.distanceTo(entity)));
            if (line != null) {
                lines.add(line);
            }
        }
        
        OverlayRenderer.getInstance().updateTracers(lines);
    }

    private double[] getOrigin(ProjectionHelper.Frame frame) {
        double x = frame.getScreenWidth() / 2.0;
        String mode = origin.getValue();
        if ("Top".equalsIgnoreCase(mode)) {
            return new double[] { x, 0.0 };
        }
        if ("Crosshair".equalsIgnoreCase(mode)) {
            return new double[] { x, frame.getScreenHeight() / 2.0 };
        }
        return new double[] { x, frame.getScreenHeight() };
    }

    private Color getDistanceColor(double distance) {
        double min = Math.min(nearDistance.getValue(), farDistance.getValue() - 1.0);
        double max = Math.max(min + 1.0, farDistance.getValue());
        double t = Math.max(0.0, Math.min(1.0, (distance - min) / (max - min)));

        int red = (int) Math.round(255.0 + ((72.0 - 255.0) * t));
        int green = (int) Math.round(72.0 + ((255.0 - 72.0) * t));
        int blue = (int) Math.round(72.0 + ((120.0 - 72.0) * t));
        return new Color(red, green, blue, 220);
    }
}
