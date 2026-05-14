package com.onionmcc.client.render;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public final class DisplayAccess {

    public interface ExtendedUser32 extends StdCallLibrary {
        ExtendedUser32 INSTANCE = (ExtendedUser32) Native.loadLibrary("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean ClientToScreen(HWND hWnd, POINT lpPoint);
    }

    private DisplayAccess() {
    }

    public static Snapshot snapshot() {
        try {
            HWND hwnd = User32.INSTANCE.FindWindow("LWJGL", null);
            if (hwnd == null) {
                hwnd = User32.INSTANCE.FindWindow("GLFW30", null);
            }
            if (hwnd == null) {
                return null;
            }

            RECT rect = new RECT();
            User32.INSTANCE.GetClientRect(hwnd, rect);

            POINT pt = new POINT(0, 0);
            ExtendedUser32.INSTANCE.ClientToScreen(hwnd, pt);

            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;

            HWND foreground = User32.INSTANCE.GetForegroundWindow();
            boolean active = true; // Force true to bypass foreground window matching issues

            return new Snapshot(pt.x, pt.y, width, height, active);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Snapshot {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final boolean active;

        public Snapshot(int x, int y, int width, int height, boolean active) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.active = active;
        }
    }
}
