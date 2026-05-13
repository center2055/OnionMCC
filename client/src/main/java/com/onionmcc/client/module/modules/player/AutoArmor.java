package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

import java.util.List;

public class AutoArmor extends Module {

    private final Setting<Double> delay = addSetting(Setting.ofNumber("Delay", "Delay between equips (ms)", 150, 0, 1000, 10));
    private long actionTimer = 0L;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the best armor", ModuleCategory.PLAYER, 0);
    }

    @Override
    protected void onEnable() {
        actionTimer = 0L;
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        
        Object screen = mc.getCurrentScreen();
        if (screen != null && !screen.getClass().getName().contains("GuiInventory")) {
            return;
        }

        if (System.currentTimeMillis() < actionTimer) return;

        Object player = mc.getPlayer();
        if (player == null) return;

        Object container = mc.getOpenContainer();
        if (container == null) return;

        // Player inventory is container 0
        int windowId = mc.getContainerWindowId(container);
        if (windowId != 0) return;

        List<Object> slots = mc.getContainerSlots(container);
        if (slots == null || slots.size() < 45) return;

        // Armor slots are 5, 6, 7, 8 in vanilla inventory container
        // Inventory slots are 9 to 44
        boolean equipped = false;
        
        for (int type = 0; type < 4; type++) {
            int armorSlot = 5 + type;
            Object currentArmorStack = mc.getSlotStack(slots.get(armorSlot));
            int bestArmorScore = getArmorScore(mc, currentArmorStack, type);
            int bestArmorSlot = -1;

            for (int i = 9; i < 45; i++) {
                Object stack = mc.getSlotStack(slots.get(i));
                if (stack != null) {
                    int score = getArmorScore(mc, stack, type);
                    if (score > bestArmorScore) {
                        bestArmorScore = score;
                        bestArmorSlot = i;
                    }
                }
            }

            if (bestArmorSlot != -1) {
                final int slotToClick = bestArmorSlot;
                // Shift click to equip
                mc.runOnMainThread(() -> mc.windowClick(0, slotToClick, 0, 1));
                actionTimer = System.currentTimeMillis() + delay.getValue().longValue();
                equipped = true;
                break;
            }
        }
    }

    private int getArmorScore(MinecraftAccessor mc, Object stack, int type) {
        if (stack == null) return -1;
        String name = mc.getItemName(stack).toLowerCase();
        
        String[] types = {"helmet", "chestplate", "leggings", "boots"};
        if (!name.contains(types[type])) return -1;

        int score = 0;
        if (name.contains("diamond")) score += 40;
        else if (name.contains("iron")) score += 30;
        else if (name.contains("chainmail")) score += 20;
        else if (name.contains("gold")) score += 10;
        else if (name.contains("leather")) score += 5;

        // Add protection enchant level check if we could map it, but name might have enchants if we use getDisplayName?
        // Usually we can't reliably get enchant level just from display name. This simple material check is enough.
        return score;
    }
}
