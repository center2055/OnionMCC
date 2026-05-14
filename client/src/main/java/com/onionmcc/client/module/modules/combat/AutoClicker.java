package com.onionmcc.client.module.modules.combat;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.module.Setting;

public class AutoClicker extends Module {

    private final Setting<Double> minCps = addSetting(Setting.ofNumber("Min CPS", "Minimum CPS", 8.0, 1.0, 20.0, 1.0));
    private final Setting<Double> maxCps = addSetting(Setting.ofNumber("Max CPS", "Maximum CPS", 12.0, 1.0, 20.0, 1.0));
    private final Setting<Boolean> leftClick = addSetting(Setting.ofBoolean("Left Click", "Auto-click while holding left mouse", true));
    private final Setting<Boolean> rightClick = addSetting(Setting.ofBoolean("Right Click", "Auto-click while holding right mouse", false));
    private final Setting<Boolean> breakBlocks = addSetting(Setting.ofBoolean("BreakBlocks", "Stop clicking when looking at a block", true));
    private final Setting<Boolean> allowEat = addSetting(Setting.ofBoolean("AllowEat", "Stop left clicking while right clicking", true));
    private final Setting<Boolean> jitter = addSetting(Setting.ofBoolean("Jitter", "Add slight aim jitter", false));

    private Thread clickerThread;
    private java.util.Random rand = new java.util.Random();
    private double jitterX = 0;
    private double jitterY = 0;

    public AutoClicker() {
        super("AutoClicker", "Automatically clicks while the chosen mouse button is held", ModuleCategory.COMBAT, 0);
    }

    private int originalLeftKeyCode = -999;
    private int originalRightKeyCode = -999;

    @Override
    protected void onEnable() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc != null) {
            originalLeftKeyCode = mc.getKeyBindCode("keyBindAttack", "ad", "field_74312_F");
            originalRightKeyCode = mc.getKeyBindCode("keyBindUseItem", "ae", "field_74313_G");
            mc.setKeyBindCode("keyBindAttack", "ad", "field_74312_F", 0);
            mc.setKeyBindCode("keyBindUseItem", "ae", "field_74313_G", 0);
        }

        clickerThread = new Thread(() -> {
            while (isEnabled()) {
                try {
                    if (mc != null && mc.isInGame() && mc.getCurrentScreen() == null) {
                        boolean isLeftDown = leftClick.getValue() && mc.isMouseButtonDown(0);
                        boolean isRightDown = rightClick.getValue() && mc.isMouseButtonDown(1);
                        
                        boolean cancelLeftClick = false;
                        if (allowEat.getValue() && mc.isMouseButtonDown(1)) {
                            cancelLeftClick = true;
                        }
                        if (breakBlocks.getValue() && mc.isLookingAtBlock()) {
                            cancelLeftClick = true;
                        }

                        if ((isLeftDown && !cancelLeftClick) || isRightDown) {
                            double min = minCps.getValue();
                            double max = maxCps.getValue();
                            double currentCps = min + Math.random() * (max - min);
                            
                            double meanTicks = 20.0 / currentCps;
                            int baseTicks = (int) Math.round(meanTicks + (Math.random() - 0.5) * 2.5);
                            if (Math.random() < 0.3) {
                                baseTicks += (Math.random() > 0.5 ? 2 : -1);
                            }
                            baseTicks = Math.max(1, baseTicks);
                            long sleepTime = baseTicks * 50L;

                            final boolean doLeft = isLeftDown && !cancelLeftClick;
                            
                            mc.runOnMainThread(() -> {
                                if (doLeft) mc.setLeftClickCounter(0);
                                mc.simulateClick(doLeft);
                                applyJitter(mc);
                            });
                            
                            Thread.sleep(sleepTime);
                            continue;
                        }
                    }
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        });
        clickerThread.setDaemon(true);
        clickerThread.start();
    }

    @Override
    protected void onDisable() {
        if (clickerThread != null) {
            clickerThread.interrupt();
            clickerThread = null;
        }
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc != null) {
            if (originalLeftKeyCode != -999) mc.setKeyBindCode("keyBindAttack", "ad", "field_74312_F", originalLeftKeyCode);
            if (originalRightKeyCode != -999) mc.setKeyBindCode("keyBindUseItem", "ae", "field_74313_G", originalRightKeyCode);
        }
    }

    @Override
    public void onTick() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (!mc.isInGame()) return;
        
        // Ensure keys stay unbound during gameplay if the user changes settings
        if (mc.getKeyBindCode("keyBindAttack", "ad", "field_74312_F") != 0) {
            originalLeftKeyCode = mc.getKeyBindCode("keyBindAttack", "ad", "field_74312_F");
            mc.setKeyBindCode("keyBindAttack", "ad", "field_74312_F", 0);
        }
        if (mc.getKeyBindCode("keyBindUseItem", "ae", "field_74313_G") != 0) {
            originalRightKeyCode = mc.getKeyBindCode("keyBindUseItem", "ae", "field_74313_G");
            mc.setKeyBindCode("keyBindUseItem", "ae", "field_74313_G", 0);
        }
    }

    private void applyJitter(MinecraftAccessor mc) {
        if (!jitter.getValue()) return;
        Object player = mc.getPlayer();
        if (player == null) return;

        jitterX += (Math.random() - 0.5) * 0.5;
        jitterY += (Math.random() - 0.5) * 0.5;
        jitterX = Math.max(-1.5, Math.min(1.5, jitterX));
        jitterY = Math.max(-1.0, Math.min(1.0, jitterY));

        float currentYaw = mc.getEntityYaw(player);
        float currentPitch = mc.getEntityPitch(player);
        
        float f = mc.getMouseSensitivity() * 0.6f + 0.2f;
        float f1 = f * f * f * 8.0f;
        
        int mouseDeltaX = (int) Math.round(jitterX / (float)((double)f1 * 0.15D));
        int mouseDeltaY = (int) Math.round(jitterY / (float)((double)f1 * 0.15D));
        
        if (mouseDeltaX == 0 && mouseDeltaY == 0) return;

        float finalYaw = currentYaw + (float)((double)((float)mouseDeltaX * f1) * 0.15D);
        float finalPitch = currentPitch + (float)((double)((float)mouseDeltaY * f1) * 0.15D);
        
        mc.setEntityYaw(player, finalYaw);
        mc.setEntityPitch(player, Math.max(-90.0f, Math.min(90.0f, finalPitch)));
    }
}