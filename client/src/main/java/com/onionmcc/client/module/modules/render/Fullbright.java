package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.mapping.ClassMapping;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

/**
 * Fullbright — sets gamma to maximum for full visibility in dark areas.
 */
public class Fullbright extends Module {

    private final Setting<Double> brightness = addSetting(
            Setting.ofNumber("Brightness", "Gamma level", 15.0, 1.0, 100.0, 1.0));

    private float originalGamma = 1.0f;

    public Fullbright() {
        super("Fullbright", "Maximum brightness everywhere", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {
        // Store original gamma
        try {
            ClassMapping gsMapping = OnionMCC.getInstance().getMappingManager()
                    .getMapping("net.minecraft.client.settings.GameSettings");
            Object settings = OnionMCC.getInstance().getMinecraft().getGameSettings();
            if (settings != null && gsMapping != null) {
                Object val = gsMapping.getField("gammaSetting").get(settings);
                if (val instanceof Number)
                    originalGamma = ((Number) val).floatValue();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDisable() {
        OnionMCC.getInstance().getMinecraft().setGamma(originalGamma);
    }

    @Override
    public void onTick() {
        OnionMCC.getInstance().getMinecraft().setGamma(brightness.getValue().floatValue());
    }
}
