package com.onionmcc.client.module.modules.player;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class ChestStealer extends Module {

    private final Setting<Double> startDelay = addSetting(Setting.ofNumber("Start Delay", "Delay before starting (ms)", 150, 0, 500, 10));
    private final Setting<Double> stealDelay = addSetting(Setting.ofNumber("Steal Delay", "Delay between items (ms)", 100, 0, 500, 10));
    private final Setting<Double> closeDelay = addSetting(Setting.ofNumber("Close Delay", "Delay before closing (ms)", 150, 0, 500, 10));
    private final Setting<Boolean> autoClose = addSetting(Setting.ofBoolean("Auto Close", "Close chest when done", true));
    private final Setting<String> mode = addSetting(Setting.ofMode("Mode", "Sorting mode", "Smart", "Smart", "Normal", "Random"));

    private boolean inChest = false;
    private long actionTimer = 0L;
    private long chestOpenedAt = 0L;
    private boolean closeTimerArmed = false;
    private List<SlotEntry> stealQueue = new ArrayList<>();
    private Random rand = new Random();

    public ChestStealer() {
        super("ChestStealer", "Automatically loots chests stealthily", ModuleCategory.UTILITY, 0);
    }

    @Override
    protected void onEnable() {
        inChest = false;
        stealQueue.clear();
        closeTimerArmed = false;
    }

    @Override
    protected void onDisable() {
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;

        Object container = mc.getOpenContainer();
        if (container == null) {
            inChest = false;
            stealQueue.clear();
            closeTimerArmed = false;
            return;
        }

        int windowId = mc.getContainerWindowId(container);
        if (windowId == 0) { // Player inventory is always 0
            inChest = false;
            stealQueue.clear();
            closeTimerArmed = false;
            return;
        }

        List<Object> slots = mc.getContainerSlots(container);
        if (slots == null || slots.size() < 36) {
            inChest = false;
            return;
        }

        int chestSize = slots.size() - 36;
        if (chestSize != 27 && chestSize != 54) {
            inChest = false;
            return;
        }

        if (!inChest) {
            inChest = true;
            chestOpenedAt = System.currentTimeMillis();
            buildStealQueue(mc, container);
            actionTimer = System.currentTimeMillis() + (long) startDelay.getValue().doubleValue() + rand.nextInt(50);
            closeTimerArmed = false;
        }

        if (System.currentTimeMillis() < actionTimer) return;

        if (!stealQueue.isEmpty()) {
            SlotEntry entry = stealQueue.remove(0);
            
            // Check if slot still has item
            if (entry.slotIndex < slots.size()) {
                Object slotObj = slots.get(entry.slotIndex);
                if (mc.getSlotStack(slotObj) != null) {
                    mc.runOnMainThread(() -> mc.windowClick(windowId, entry.slotIndex, 0, 1)); // shift click
                }
            }
            actionTimer = System.currentTimeMillis() + (long) stealDelay.getValue().doubleValue() + rand.nextInt(30);
        } else if (autoClose.getValue()) {
            if (!closeTimerArmed) {
                actionTimer = System.currentTimeMillis() + (long) closeDelay.getValue().doubleValue() + rand.nextInt(50);
                closeTimerArmed = true;
            } else {
                mc.runOnMainThread(() -> mc.closeScreen());
                inChest = false;
                closeTimerArmed = false;
            }
        }
    }

    private void buildStealQueue(MinecraftAccessor mc, Object container) {
        stealQueue.clear();
        List<Object> slots = mc.getContainerSlots(container);
        if (slots == null || slots.isEmpty()) return;

        int chestSize = slots.size() - 36; // Vanilla chest inventory size
        if (chestSize <= 0) return;

        List<SlotEntry> candidates = new ArrayList<>();
        for (int i = 0; i < chestSize; i++) {
            Object slot = slots.get(i);
            Object stack = mc.getSlotStack(slot);
            if (stack != null) {
                // Here we could add smart filters based on item type if we map them.
                // For now, we steal everything in the chest part.
                candidates.add(new SlotEntry(i));
            }
        }

        String sortMode = mode.getValue();
        if (sortMode.equals("Random")) {
            Collections.shuffle(candidates);
        } else if (sortMode.equals("Smart") && !candidates.isEmpty()) {
            List<SlotEntry> result = new ArrayList<>();
            SlotEntry current = candidates.remove(rand.nextInt(candidates.size()));
            result.add(current);

            while (!candidates.isEmpty()) {
                final SlotEntry cur = current;
                SlotEntry nearest = candidates.stream()
                        .min(Comparator.comparingDouble(s -> s.distanceTo(cur)))
                        .get();
                candidates.remove(nearest);
                result.add(nearest);
                current = nearest;
            }
            candidates = result;
        }

        stealQueue.addAll(candidates);
    }

    private static class SlotEntry {
        final int slotIndex;
        final int row;
        final int col;

        SlotEntry(int slotIndex) {
            this.slotIndex = slotIndex;
            int cols = 9;
            this.row = slotIndex / cols;
            this.col = slotIndex % cols;
        }

        double distanceTo(SlotEntry other) {
            return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
        }
    }
}
