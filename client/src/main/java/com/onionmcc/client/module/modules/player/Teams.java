package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;

public class Teams extends Module {
    public Teams() {
        super("Teams", "Prevents combat modules from attacking teammates", ModuleCategory.PLAYER, 0);
    }
    
    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    @Override
    public void onTick() {}
}
