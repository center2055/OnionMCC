package com.onionmcc.client.module.modules.render;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleCategory;
import com.onionmcc.client.render.DisplayAccess;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

public class Borderless extends Module {

    private int originalStyle = 0;
    private HWND currentHwnd = null;

    public Borderless() {
        super("Borderless", "Forces borderless windowed mode to allow overlays", ModuleCategory.RENDER, 0);
    }

    @Override
    protected void onEnable() {
        MinecraftAccessor mc = OnionMCC.getInstance().getMinecraft();
        if (mc == null) return;

        currentHwnd = DisplayAccess.findMinecraftWindow();
        if (currentHwnd != null) {
            originalStyle = User32.INSTANCE.GetWindowLong(currentHwnd, WinUser.GWL_STYLE);
            
            int newStyle = originalStyle;
            newStyle &= ~WinUser.WS_CAPTION;
            newStyle &= ~WinUser.WS_THICKFRAME;
            newStyle &= ~WinUser.WS_MINIMIZEBOX;
            newStyle &= ~WinUser.WS_MAXIMIZEBOX;
            newStyle &= ~WinUser.WS_SYSMENU;

            User32.INSTANCE.SetWindowLong(currentHwnd, WinUser.GWL_STYLE, newStyle);
            User32.INSTANCE.ShowWindow(currentHwnd, WinUser.SW_MAXIMIZE);
            
            // Turn off Minecraft's internal fullscreen to release exclusive lock
            try {
                Object settings = mc.getGameSettings();
                if (settings != null) {
                    // Try to toggle if it is on
                    // We don't have a direct setter for fullScreen easily, but we can just let the user know.
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDisable() {
        if (currentHwnd != null && originalStyle != 0) {
            User32.INSTANCE.SetWindowLong(currentHwnd, WinUser.GWL_STYLE, originalStyle);
            User32.INSTANCE.ShowWindow(currentHwnd, WinUser.SW_RESTORE);
        }
    }

    @Override
    public void onTick() {
        // Nothing needed on tick
    }
}
