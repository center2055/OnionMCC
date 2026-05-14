package com.onionmcc.client.render;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.lang.management.ManagementFactory;

public final class DisplayAccess {

    private static final String LWJGL2_CLASS = "LWJGL";
    private static final String GLFW_CLASS = "GLFW30";

    public interface ExtendedUser32 extends StdCallLibrary {
        ExtendedUser32 INSTANCE = (ExtendedUser32) Native.loadLibrary("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean ClientToScreen(HWND hWnd, POINT lpPoint);
    }

    private DisplayAccess() {
    }

    public static Snapshot snapshot() {
        try {
            Snapshot lwjglSnapshot = snapshotFromLwjglDisplay();
            if (lwjglSnapshot != null) {
                return lwjglSnapshot;
            }

            HWND hwnd = findMinecraftWindow();
            if (hwnd == null) {
                return null;
            }

            RECT rect = new RECT();
            if (!User32.INSTANCE.GetClientRect(hwnd, rect)) {
                return null;
            }

            POINT pt = new POINT(0, 0);
            if (!ExtendedUser32.INSTANCE.ClientToScreen(hwnd, pt)) {
                return null;
            }

            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;

            boolean active = width > 0 && height > 0;

            return new Snapshot(pt.x, pt.y, width, height, active);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Snapshot snapshotFromLwjglDisplay() {
        try {
            Class<?> displayClass = Class.forName("org.lwjgl.opengl.Display");
            Boolean created = (Boolean) displayClass.getMethod("isCreated").invoke(null);
            if (created == null || !created) {
                return null;
            }

            int x = ((Number) displayClass.getMethod("getX").invoke(null)).intValue();
            int y = ((Number) displayClass.getMethod("getY").invoke(null)).intValue();
            int width = ((Number) displayClass.getMethod("getWidth").invoke(null)).intValue();
            int height = ((Number) displayClass.getMethod("getHeight").invoke(null)).intValue();
            boolean active = Boolean.TRUE.equals(displayClass.getMethod("isActive").invoke(null));

            if (width <= 0 || height <= 0) {
                return null;
            }

            return new Snapshot(x, y, width, height, active);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static HWND findMinecraftWindow() {
        HWND preferred = findWindowForCurrentProcess(true);
        if (preferred != null) {
            return preferred;
        }

        HWND fallback = findWindowForCurrentProcess(false);
        if (fallback != null) {
            return fallback;
        }

        HWND hwnd = User32.INSTANCE.FindWindow(LWJGL2_CLASS, null);
        if (hwnd == null) {
            hwnd = User32.INSTANCE.FindWindow(GLFW_CLASS, null);
        }
        return hwnd;
    }

    private static HWND findWindowForCurrentProcess(boolean preferredOnly) {
        final int pid = getCurrentPid();
        final HWND[] bestWindow = new HWND[1];
        final long[] bestArea = new long[] { -1L };

        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (hwnd == null || !User32.INSTANCE.IsWindowVisible(hwnd)) {
                return true;
            }

            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            if (pidRef.getValue() != pid) {
                return true;
            }

            String className = getWindowClassName(hwnd);
            boolean preferredClass = LWJGL2_CLASS.equals(className) || GLFW_CLASS.equals(className);
            if (preferredOnly && !preferredClass) {
                return true;
            }

            RECT rect = new RECT();
            if (!User32.INSTANCE.GetClientRect(hwnd, rect)) {
                return true;
            }

            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;
            if (width <= 0 || height <= 0) {
                return true;
            }

            long area = (long) width * height;
            if (area > bestArea[0]) {
                bestArea[0] = area;
                bestWindow[0] = hwnd;
            }
            return true;
        }, Pointer.NULL);

        return bestWindow[0];
    }

    private static int getCurrentPid() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int atIndex = runtimeName.indexOf('@');
            String pid = atIndex >= 0 ? runtimeName.substring(0, atIndex) : runtimeName;
            return Integer.parseInt(pid);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String getWindowClassName(HWND hwnd) {
        char[] buffer = new char[256];
        int len = User32.INSTANCE.GetClassName(hwnd, buffer, buffer.length);
        if (len <= 0) {
            return "";
        }
        return Native.toString(buffer);
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
