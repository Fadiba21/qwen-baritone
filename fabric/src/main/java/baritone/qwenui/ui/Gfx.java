package baritone.qwenui.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * Crisp 2D drawing in whole screen pixels. {@link #pixelsPerUnit} is the integer number of framebuffer
 * pixels covered by one UI unit; every shape is rasterised in framebuffer pixels with anti-aliased edges,
 * so corners and icons stay smooth at any window size.
 */
public final class Gfx {
    private static final int SS = 4;
    /** Framebuffer pixels per UI unit; set by the screen on every layout. Always >= 1. */
    public static volatile int pixelsPerUnit = 1;
    private static final Map<Integer, byte[]> CORNERS = new HashMap<>();
    private static final Map<Long, byte[]> ICON_MASKS = new HashMap<>();
    public static final int ICON_UNITS = 12;

    private Gfx() { }

    private static int alpha(int color, int level, int levels) {
        int a = (color >>> 24) * level / levels;
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Level 0..16 coverage of the top-left R x R corner square of a rounded rect with radius R. */
    private static byte[] corner(int r) {
        synchronized (CORNERS) {
            return CORNERS.computeIfAbsent(r, radius -> {
                byte[] out = new byte[radius * radius];
                double rr = (double) radius * radius;
                for (int j = 0; j < radius; j++) {
                    for (int i = 0; i < radius; i++) {
                        int hit = 0;
                        for (int sy = 0; sy < SS; sy++) {
                            for (int sx = 0; sx < SS; sx++) {
                                double dx = radius - (i + (sx + 0.5) / SS);
                                double dy = radius - (j + (sy + 0.5) / SS);
                                if (dx * dx + dy * dy <= rr) hit++;
                            }
                        }
                        out[j * radius + i] = (byte) hit;
                    }
                }
                return out;
            });
        }
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int e = Math.max(1, pixelsPerUnit);
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.pose().pushMatrix();
        g.pose().scale(1f / e, 1f / e);
        int px = x * e, py = y * e, pw = w * e, ph = h * e, pr = r * e;
        try {
            if (pr == 0) { g.fill(px, py, px + pw, py + ph, color); return; }
            g.fill(px, py + pr, px + pw, py + ph - pr, color);
            byte[] mask = corner(pr);
            int levels = SS * SS;
            for (int j = 0; j < pr; j++) {
                int full = pr;
                for (int i = 0; i < pr; i++) if (mask[j * pr + i] == levels) { full = i; break; }
                int top = py + j, bottom = py + ph - 1 - j;
                g.fill(px + full, top, px + pw - full, top + 1, color);
                g.fill(px + full, bottom, px + pw - full, bottom + 1, color);
                int i = 0;
                while (i < full) {
                    int level = mask[j * pr + i];
                    int k = i + 1;
                    while (k < full && mask[j * pr + k] == level) k++;
                    if (level > 0) {
                        int c = alpha(color, level, levels);
                        g.fill(px + i, top, px + k, top + 1, c);
                        g.fill(px + pw - k, top, px + pw - i, top + 1, c);
                        g.fill(px + i, bottom, px + k, bottom + 1, c);
                        g.fill(px + pw - k, bottom, px + pw - i, bottom + 1, c);
                    }
                    i = k;
                }
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    // ---------------------------------------------------------------- icons

    private static boolean seg(double u, double v, double x1, double y1, double x2, double y2, double w) {
        double dx = x2 - x1, dy = y2 - y1, len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((u - x1) * dx + (v - y1) * dy) / len2));
        double px = x1 + t * dx - u, py = y1 + t * dy - v;
        return px * px + py * py <= w * w / 4;
    }
    private static boolean rect(double u, double v, double x, double y, double w, double h) { return u >= x && u <= x + w && v >= y && v <= y + h; }
    private static boolean rrect(double u, double v, double x, double y, double w, double h, double r) {
        if (!rect(u, v, x, y, w, h)) return false;
        double cx = Math.max(x + r, Math.min(u, x + w - r)), cy = Math.max(y + r, Math.min(v, y + h - r));
        return (u - cx) * (u - cx) + (v - cy) * (v - cy) <= r * r;
    }
    private static boolean rrectLine(double u, double v, double x, double y, double w, double h, double r, double t) {
        return rrect(u, v, x, y, w, h, r) && !rrect(u, v, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t));
    }
    private static boolean disc(double u, double v, double cx, double cy, double r) { return (u - cx) * (u - cx) + (v - cy) * (v - cy) <= r * r; }
    private static boolean ring(double u, double v, double cx, double cy, double r, double t) {
        double d = Math.hypot(u - cx, v - cy);
        return d <= r + t / 2 && d >= r - t / 2;
    }
    private static boolean poly(double u, double v, double... p) {
        boolean in = false;
        for (int i = 0, j = p.length - 2; i < p.length; j = i, i += 2) {
            if ((p[i + 1] > v) != (p[j + 1] > v) && u < (p[j] - p[i]) * (v - p[i + 1]) / (p[j + 1] - p[i + 1]) + p[i]) in = !in;
        }
        return in;
    }

    /** Whether the point (u, v) in 0..12 icon space lies inside the icon shape. */
    static boolean inside(int kind, double u, double v) {
        switch (kind) {
            case 0: return rrect(u, v, 0.8, 0.8, 4.4, 4.4, 1) || rrect(u, v, 6.8, 0.8, 4.4, 4.4, 1) || rrect(u, v, 0.8, 6.8, 4.4, 4.4, 1) || rrect(u, v, 6.8, 6.8, 4.4, 4.4, 1);
            case 1: case 6:
                return seg(u, v, 0.8, 2, 11.2, 2, 1.2) || seg(u, v, 0.8, 6, 11.2, 6, 1.2) || seg(u, v, 0.8, 10, 11.2, 10, 1.2)
                        || disc(u, v, 3.5, 2, 1.7) || disc(u, v, 8, 6, 1.7) || disc(u, v, 5, 10, 1.7);
            case 2: return seg(u, v, 2, 3, 5.2, 6, 1.5) || seg(u, v, 5.2, 6, 2, 9, 1.5) || seg(u, v, 6.6, 9.2, 10.6, 9.2, 1.5);
            case 3: return rrectLine(u, v, 1, 1, 10, 10, 1.6, 1.4) || rrect(u, v, 4, 4, 4, 4, 0.8);
            case 4: return rrect(u, v, 1.4, 6, 2.6, 5.2, 0.9) || rrect(u, v, 4.7, 1.4, 2.6, 9.8, 0.9) || rrect(u, v, 8, 3.8, 2.6, 7.4, 0.9);
            case 7: return disc(u, v, 6, 3.6, 2.5) || (v <= 11.6 && v >= 7 && ((u - 6) * (u - 6)) / 23.04 + ((v - 11.6) * (v - 11.6)) / 21.0 <= 1);
            case 8: return (rrect(u, v, 1.4, 3.6, 9.2, 7, 2) && !disc(u, v, 4.3, 7, 1.05) && !disc(u, v, 7.7, 7, 1.05))
                    || seg(u, v, 6, 1.4, 6, 3.8, 1.2) || disc(u, v, 6, 1.3, 1);
            case 9: return ring(u, v, 5, 5, 3.4, 1.5) || seg(u, v, 7.5, 7.5, 10.8, 10.8, 1.8);
            case 10: return poly(u, v, 2.6, 1.2, 10.8, 6, 2.6, 10.8);
            case 11: return ring(u, v, 6, 6, 5, 1.2) || disc(u, v, 6, 3.5, 0.9) || seg(u, v, 6, 5.5, 6, 9, 1.5);
            case 12: return seg(u, v, 1.6, 6.6, 4.6, 9.6, 1.8) || seg(u, v, 4.6, 9.6, 10.6, 2.8, 1.8);
            case 13: return rrect(u, v, 0.8, 3.2, 10.4, 7.8, 1.2) || rrect(u, v, 0.8, 1.6, 4.8, 3, 0.8);
            case 14: return rrect(u, v, 1.6, 1.6, 8.8, 8.8, 1.4);
            case 15: {
                boolean gap = u > 6 && v < 6 && (u - 6) > (6 - v) * 0.2 && v < 4.4;
                return (ring(u, v, 6, 6, 4.2, 1.6) && !gap) || poly(u, v, 7.4, 0.6, 11, 3.6, 6.6, 4.8);
            }
            case 16: return seg(u, v, 2, 2, 10, 10, 1.7) || seg(u, v, 2, 10, 10, 2, 1.7);
            case 17: return rrectLine(u, v, 0.7, 2.4, 10.6, 7.2, 1.4, 1.1) || rrect(u, v, 2.4, 4.2, 1.5, 1.2, 0.3) || rrect(u, v, 5.2, 4.2, 1.5, 1.2, 0.3)
                    || rrect(u, v, 8, 4.2, 1.5, 1.2, 0.3) || rrect(u, v, 3, 6.6, 6, 1.2, 0.3);
            case 18: return rrectLine(u, v, 0.8, 2, 10.4, 8, 1, 1.1) || seg(u, v, 0.8, 6, 11.2, 6, 1) || seg(u, v, 6, 2, 6, 6, 1) || seg(u, v, 3.4, 6, 3.4, 10, 1) || seg(u, v, 8.6, 6, 8.6, 10, 1);
            case 19: return seg(u, v, 6, 1.2, 6, 8, 1.8) || poly(u, v, 2.4, 6.2, 9.6, 6.2, 6, 10.6) || seg(u, v, 1.4, 11, 10.6, 11, 1.3);
            case 20: return seg(u, v, 1.4, 6, 10.6, 6, 1.8) || seg(u, v, 6, 1.4, 6, 10.6, 1.8);
            case 21: return seg(u, v, 1.4, 6, 10.6, 6, 1.8);
            default: return poly(u, v, 6, 0.5, 7.4, 4.6, 11.5, 6, 7.4, 7.4, 6, 11.5, 4.6, 7.4, 0.5, 6, 4.6, 4.6);
        }
    }

    private static byte[] iconMask(int kind, int size) {
        long key = ((long) kind << 32) | size;
        synchronized (ICON_MASKS) {
            return ICON_MASKS.computeIfAbsent(key, k -> {
                byte[] out = new byte[size * size];
                double scale = ICON_UNITS / (double) size;
                for (int j = 0; j < size; j++) {
                    for (int i = 0; i < size; i++) {
                        int hit = 0;
                        for (int sy = 0; sy < SS; sy++) {
                            for (int sx = 0; sx < SS; sx++) {
                                if (inside(kind, (i + (sx + 0.5) / SS) * scale, (j + (sy + 0.5) / SS) * scale)) hit++;
                            }
                        }
                        out[j * size + i] = (byte) hit;
                    }
                }
                return out;
            });
        }
    }

    /** Draws a 12 x 12 UI-unit icon with its top-left corner at (x, y). */
    public static void icon(GuiGraphicsExtractor g, int x, int y, int kind, int color) {
        int e = Math.max(1, pixelsPerUnit);
        int size = ICON_UNITS * e;
        byte[] mask = iconMask(kind, size);
        int levels = SS * SS;
        g.pose().pushMatrix();
        g.pose().scale(1f / e, 1f / e);
        try {
            int ox = x * e, oy = y * e;
            for (int j = 0; j < size; j++) {
                int i = 0;
                while (i < size) {
                    int level = mask[j * size + i];
                    int k = i + 1;
                    while (k < size && mask[j * size + k] == level) k++;
                    if (level > 0) g.fill(ox + i, oy + j, ox + k, oy + j + 1, alpha(color, level, levels));
                    i = k;
                }
            }
        } finally {
            g.pose().popMatrix();
        }
    }
}
