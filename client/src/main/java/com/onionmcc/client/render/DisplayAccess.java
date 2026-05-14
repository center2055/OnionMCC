package com.onionmcc.client.render;

import java.lang.reflect.Method;
import com.onionmcc.client.OnionMCC;

public final class DisplayAccess {

    private static Class<?> displayClass;
    private static Method getX;
    private static Method getY;
    private static Method getWidth;
    private static Method getHeight;
    private static Method isActive;

    private DisplayAccess() {
    }

    public static Snapshot snapshot() {
        try {
            if (displayClass == null) {
                java.lang.instrument.Instrumentation inst = OnionMCC.getInstance().getInstrumentation();
                if (inst != null) {
                    for (Class<?> c : inst.getAllLoadedClasses()) {
                        if ("org.lwjgl.opengl.Display".equals(c.getName())) {
                            displayClass = c;
                            break;
                        }
                    }
                }
                if (displayClass == null) {
                    try {
                        displayClass = Class.forName("org.lwjgl.opengl.Display");
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                }

                getX = displayClass.getMethod("getX");
                getY = displayClass.getMethod("getY");
                getWidth = displayClass.getMethod("getWidth");
                getHeight = displayClass.getMethod("getHeight");
                isActive = displayClass.getMethod("isActive");
            }

            int x = (Integer) getX.invoke(null);
            int y = (Integer) getY.invoke(null);
            int width = (Integer) getWidth.invoke(null);
            int height = (Integer) getHeight.invoke(null);
            // On Windows 10+, the LWJGL2 window bounds include the title bar and borders. 
            // We apply a rough heuristic to offset it for the client area (y+31, x+8, w-16, h-39).
            // However, this depends on the theme. For now, let's just use the raw coordinates.
            
            // Wait, we need the window to be somewhat accurate. We will just use the exact returned bounds.
            boolean active = (Boolean) isActive.invoke(null);

            return new Snapshot(x, y, width, height, active);
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
