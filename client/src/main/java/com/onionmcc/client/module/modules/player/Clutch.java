package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;

public class Clutch extends Module {

    public Clutch() {
        super("Clutch", "Automatically places blocks to save you from falling", ModuleCategory.PLAYER, 0);
    }
    
    @Override
    protected void onEnable() {}
    
    @Override
    protected void onDisable() {}
    
    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        Object player = mc.getPlayer();
        if (player == null) return;
        
        // If falling fast
        if (mc.getEntityMotionY(player) < -0.4 && !mc.isEntityOnGround(player)) {
            // Check if pitch is looking downwards enough
            if (mc.getEntityPitch(player) > 60.0f) {
                // Just spam right click so it places on the side of the block we just fell off of
                mc.runOnMainThread(() -> mc.simulateClick(false)); // Right click
            }
        }
    }
}
