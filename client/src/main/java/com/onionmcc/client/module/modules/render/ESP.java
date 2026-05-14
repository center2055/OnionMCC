package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.render.DisplayAccess;
import com.onionmcc.client.render.OverlayRenderer;
import com.onionmcc.client.render.ProjectionHelper;

import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {

    private long lastDebugLog;

    public ESP() {
        super("ESP", "Draws boxes around players", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {
        lastDebugLog = 0L;
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
        DisplayAccess.Snapshot display = DisplayAccess.snapshot();

        for (Object entity : playerSnapshot) {
            if (entity == player || entity == null) continue;
            if (mc.isEntityDead(entity) || mc.isInvisible(entity)) continue;

            OverlayRenderer.EspBox box = ProjectionHelper.projectEspBox(mc, player, entity);
            if (box != null) {
                boxes.add(box);
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastDebugLog >= 2000L) {
            lastDebugLog = now;
            String displayText = display == null
                    ? "null"
                    : (display.width + "x" + display.height + "@" + display.x + "," + display.y + " active=" + display.active);
            OnionMCC.getInstance().logToFile(
                    "ESP debug: players=" + playerSnapshot.length
                            + ", boxes=" + boxes.size()
                            + ", display=" + displayText
                            + ", projection=" + ProjectionHelper.getLastProjectionFailure());
        }
        
        OverlayRenderer.getInstance().updateEsp(boxes, true);
    }
}
