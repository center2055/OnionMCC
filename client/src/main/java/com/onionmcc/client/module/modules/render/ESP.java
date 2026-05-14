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

public class ESP extends Module {

    private final Setting<Boolean> showHealth = addSetting(
            Setting.ofBoolean("Health Bar", "Draws health next to the ESP box", true));
    private final Setting<Double> red = addSetting(
            Setting.ofNumber("Red", "ESP red color channel", 64.0, 0.0, 255.0, 1.0));
    private final Setting<Double> green = addSetting(
            Setting.ofNumber("Green", "ESP green color channel", 255.0, 0.0, 255.0, 1.0));
    private final Setting<Double> blue = addSetting(
            Setting.ofNumber("Blue", "ESP blue color channel", 160.0, 0.0, 255.0, 1.0));
    private final Setting<Double> alpha = addSetting(
            Setting.ofNumber("Alpha", "ESP opacity", 220.0, 30.0, 255.0, 1.0));

    public ESP() {
        super("ESP", "Draws boxes around players", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        OverlayRenderer.getInstance().clearEsp();
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) {
            OverlayRenderer.getInstance().clearEsp();
            return;
        }

        Object player = mc.getPlayer();
        List<Object> players = mc.getWorldPlayers();
        Object[] playerSnapshot = players.toArray();
        List<OverlayRenderer.EspBox> boxes = new ArrayList<>();
        ProjectionHelper.Frame frame = ProjectionHelper.captureFrame(mc, player);
        if (frame == null) {
            OverlayRenderer.getInstance().clearEsp();
            return;
        }

        for (Object entity : playerSnapshot) {
            if (entity == player || entity == null) continue;
            if (mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;

            OverlayRenderer.EspBox box = ProjectionHelper.projectEspBox(mc, frame, entity, getEspColor());
            if (box != null) {
                boxes.add(box);
            }
        }
        
        OverlayRenderer.getInstance().updateEsp(boxes, showHealth.getValue());
    }

    private Color getEspColor() {
        return new Color(channel(red), channel(green), channel(blue), channel(alpha));
    }

    private int channel(Setting<Double> setting) {
        return Math.max(0, Math.min(255, setting.getValue().intValue()));
    }
}
