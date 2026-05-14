package com.onionmcc.client.render;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transparent click-through overlay used by ESP and Tracers.
 */
public final class OverlayRenderer {

    private static final OverlayRenderer INSTANCE = new OverlayRenderer();
    private static final int WS_EX_TOOLWINDOW = 0x00000080;

    private final Object windowLock = new Object();
    private volatile List<EspBox> espBoxes = Collections.emptyList();
    private volatile List<TracerLine> tracerLines = Collections.emptyList();
    private volatile List<String> arrayListModules = Collections.emptyList();
    private volatile boolean showHealth;
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);

    private JWindow window;
    private OverlayPanel panel;

    private OverlayRenderer() {
    }

    public static OverlayRenderer getInstance() {
        return INSTANCE;
    }

    public void updateEsp(List<EspBox> boxes, boolean showHealth) {
        espBoxes = Collections.unmodifiableList(new ArrayList<>(boxes));
        this.showHealth = showHealth;
        refresh();
    }

    public void updateTracers(List<TracerLine> lines) {
        tracerLines = Collections.unmodifiableList(new ArrayList<>(lines));
        refresh();
    }

    public void updateArrayList(List<String> modules) {
        arrayListModules = Collections.unmodifiableList(new ArrayList<>(modules));
        refresh();
    }

    public void clearEsp() {
        espBoxes = Collections.emptyList();
        refresh();
    }

    public void clearTracers() {
        tracerLines = Collections.emptyList();
        refresh();
    }

    public void clearArrayList() {
        arrayListModules = Collections.emptyList();
        refresh();
    }

    public void shutdown() {
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.setVisible(false);
                window.dispose();
                window = null;
                panel = null;
            }
        });
    }

    private void refresh() {
        ensureWindow();
        if (!refreshQueued.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                if (window == null || panel == null) {
                    return;
                }

                DisplayAccess.Snapshot display = DisplayAccess.snapshot();
                boolean visible = hasContent() && display != null && display.width > 0 && display.height > 0;

                if (!visible) {
                    window.setVisible(false);
                    return;
                }

                if (window.getX() != display.x || window.getY() != display.y
                        || window.getWidth() != display.width || window.getHeight() != display.height) {
                    window.setBounds(display.x, display.y, display.width, display.height);
                    panel.setPreferredSize(new Dimension(display.width, display.height));
                    panel.setSize(display.width, display.height);
                    panel.revalidate();
                }

                if (!window.isVisible()) {
                    window.setVisible(true);
                    applyClickThrough();
                }

                panel.paintImmediately(0, 0, panel.getWidth(), panel.getHeight());
            } finally {
                refreshQueued.set(false);
            }
        });
    }

    private boolean hasContent() {
        return !espBoxes.isEmpty() || !tracerLines.isEmpty() || !arrayListModules.isEmpty();
    }

    private void ensureWindow() {
        if (window != null) {
            return;
        }
        synchronized (windowLock) {
            if (window != null) {
                return;
            }
            try {
                SwingUtilities.invokeAndWait(() -> {
                    window = new JWindow();
                    window.setBackground(new Color(0, 0, 0, 0));
                    window.setAutoRequestFocus(false);
                    window.setFocusableWindowState(false);
                    window.setAlwaysOnTop(true);
                    panel = new OverlayPanel();
                    panel.setOpaque(false);
                    window.setContentPane(panel);
                    window.setVisible(false);
                });
            } catch (Exception ignored) {
            }
        }
    }

    private void applyClickThrough() {
        if (window == null) {
            return;
        }
        try {
            Pointer pointer = Native.getComponentPointer(window);
            if (pointer == null) {
                return;
            }
            HWND hwnd = new HWND(pointer);
            int style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
            style |= WinUser.WS_EX_LAYERED;
            style |= WinUser.WS_EX_TRANSPARENT;
            style |= WS_EX_TOOLWINDOW;
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, style);
        } catch (Throwable t) {
        }
    }

    private final class OverlayPanel extends JComponent {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                drawTracers(g);
                drawEsp(g);
                drawArrayList(g);
            } finally {
                g.dispose();
            }
        }

        private void drawArrayList(Graphics2D g) {
            if (arrayListModules.isEmpty()) return;
            
            g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 15));
            java.awt.FontMetrics fm = g.getFontMetrics();
            
            int yOffset = 10;
            for (int i = 0; i < arrayListModules.size(); i++) {
                String mod = arrayListModules.get(i);
                int textWidth = fm.stringWidth(mod);
                int x = getWidth() - textWidth - 10;
                
                // Minimalistic gradient color for text
                float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
                hue += (i * 0.05f); 
                Color textColor = Color.getHSBColor(hue % 1.0f, 0.7f, 1.0f);
                
                // Draw a subtle modern shadow
                g.setColor(new Color(0, 0, 0, 180));
                g.drawString(mod, x + 1, yOffset + 1);
                
                // Draw text
                g.setColor(textColor);
                g.drawString(mod, x, yOffset);
                
                yOffset += fm.getHeight() + 2;
            }
        }

        private void drawTracers(Graphics2D g) {
            for (TracerLine line : tracerLines) {
                g.setColor(line.color);
                g.setStroke(new BasicStroke(1.6f));
                g.drawLine((int) line.x1, (int) line.y1, (int) line.x2, (int) line.y2);
            }
        }

        private void drawEsp(Graphics2D g) {
            for (EspBox box : espBoxes) {
                int x = (int) Math.round(box.x);
                int y = (int) Math.round(box.y);
                int w = (int) Math.round(box.width);
                int h = (int) Math.round(box.height);

                int fillAlpha = Math.max(18, Math.min(80, box.color.getAlpha() / 3));
                g.setColor(new Color(box.color.getRed(), box.color.getGreen(), box.color.getBlue(), fillAlpha));
                g.fillRect(x, y, w, h);

                g.setColor(box.color);
                g.setStroke(new BasicStroke(1.4f));
                g.drawRect(x, y, w, h);

                if (showHealth) {
                    int barHeight = (int) Math.round(h * box.healthRatio);
                    int barX = x - 5;
                    int barY = y + (h - barHeight);
                    g.setColor(new Color(20, 20, 20, 180));
                    g.fillRect(barX, y, 3, h);
                    g.setColor(new Color(88, 255, 88, 220));
                    g.fillRect(barX, barY, 3, barHeight);
                }

                if (box.label != null && !box.label.isEmpty()) {
                    g.setColor(new Color(255, 255, 255, 220));
                    g.drawString(box.label, x, Math.max(12, y - 4));
                }
            }
        }
    }

    public static final class EspBox {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final double healthRatio;
        public final String label;
        public final Color color;

        public EspBox(double x, double y, double width, double height, double healthRatio, String label, Color color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.healthRatio = healthRatio;
            this.label = label;
            this.color = color;
        }
    }

    public static final class TracerLine {
        public final double x1;
        public final double y1;
        public final double x2;
        public final double y2;
        public final Color color;

        public TracerLine(double x1, double y1, double x2, double y2, Color color) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
        }
    }
}
