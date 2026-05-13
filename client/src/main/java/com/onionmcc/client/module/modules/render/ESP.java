package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.render.OverlayRenderer;
import com.onionmcc.client.render.ProjectionHelper;

import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {

    public ESP() {
        super("ESP", "Draws boxes around players", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {}

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
        List<OverlayRenderer.EspBox> boxes = new ArrayList<>();

        for (Object entity : players) {
            if (entity == player || entity == null) continue;
            if (mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;

            OverlayRenderer.EspBox box = ProjectionHelper.projectEspBox(mc, player, entity);
            if (box != null) {
                boxes.add(box);
            }
        }
        
        OverlayRenderer.getInstance().updateEsp(boxes, true);
    }
}