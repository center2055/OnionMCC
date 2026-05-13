package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.render.OverlayRenderer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayListMod extends Module {

    public ArrayListMod() {
        super("ArrayList", "Displays enabled modules on screen", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        OverlayRenderer.getInstance().clearArrayList();
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) {
            OverlayRenderer.getInstance().clearArrayList();
            return;
        }
        
        // Only show if F3 debug screen is closed, but we don't map F3 so we just check inGame.
        
        List<String> active = OnionMCC.getInstance().getModuleManager().getEnabledModules().stream()
            .filter(m -> !m.getName().equalsIgnoreCase("ArrayList") && !m.getName().equalsIgnoreCase("Fullbright") && !m.getName().equalsIgnoreCase("Teams") && !m.getName().equalsIgnoreCase("Friends"))
            .map(Module::getName)
            .sorted(Comparator.comparingInt(String::length).reversed()) // sort by length descending
            .collect(Collectors.toList());
            
        OverlayRenderer.getInstance().updateArrayList(active);
    }
}
