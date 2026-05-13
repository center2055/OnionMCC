package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

public class HitBox extends Module {

    private static HitBox instance;

    private final Setting<Double> expand = addSetting(
            Setting.ofNumber("Expand", "Expands entity hitboxes by extra blocks.", 0.1, 0.05, 0.5, 0.05));

    public HitBox() {
        super("HitBox", "Expands entity hitboxes to make hitting them easier", ModuleCategory.COMBAT, 0);
        instance = this;
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    @Override
    public void onTick() {}

    public static double getExpansion() {
        if (instance != null && instance.isEnabled()) {
            return instance.expand.getValue();
        }
        return 0.0;
    }
}
