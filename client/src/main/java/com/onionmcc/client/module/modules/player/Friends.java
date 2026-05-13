package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;

import java.util.HashSet;
import java.util.Set;

public class Friends extends Module {

    private static final Set<String> friends = new HashSet<>();
    private boolean wasMiddleClicking = false;

    public Friends() {
        super("Friends", "Middle-click players to friend them", ModuleCategory.PLAYER, 0);
    }
    
    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
@Override
public void onTick() {
    MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
    if (!mc.isInGame() || !mc.isMinecraftWindowActive()) return;

    boolean isMiddleClicking = mc.isMouseButtonDown(2); // Middle click
    if (isMiddleClicking && !wasMiddleClicking) {
        Object targetEntity = mc.getObjectMouseOverEntity();
        if (targetEntity != null) {
            if (mc.isPlayerEntity(targetEntity)) {
                String name = mc.getEntityName(targetEntity);
                if (name != null && !name.isEmpty()) {
                    if (friends.contains(name)) {
                        friends.remove(name);
                    } else {
                        friends.add(name);
                    }
                }
            }
        }
    }
    wasMiddleClicking = isMiddleClicking;
}

    public static boolean isFriend(String name) {
        return friends.contains(name);
    }
}
