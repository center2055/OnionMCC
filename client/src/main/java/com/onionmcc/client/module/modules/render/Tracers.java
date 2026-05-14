package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.render.OverlayRenderer;
import com.onionmcc.client.render.ProjectionHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Tracers extends Module {

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

            OverlayRenderer.TracerLine line = ProjectionHelper.projectTracer(mc, frame, entity,
                    frame.getScreenWidth() / 2.0,
                    frame.getScreenHeight(),
                    new Color(64, 255, 160, 220));
            if (line != null) {
                lines.add(line);
            }
        }
        
        OverlayRenderer.getInstance().updateTracers(lines);
    }
}
