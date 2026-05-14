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

public class ESP extends Module {

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

            OverlayRenderer.EspBox box = ProjectionHelper.projectEspBox(mc, frame, entity, new Color(64, 255, 160, 220));
            if (box != null) {
                boxes.add(box);
            }
        }
        
        OverlayRenderer.getInstance().updateEsp(boxes, true);
    }
}
