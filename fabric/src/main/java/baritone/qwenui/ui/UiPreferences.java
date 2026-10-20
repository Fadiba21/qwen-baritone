package baritone.qwenui.ui;

/** Pure preference policy shared by rendering, persistence and regression tests. */
public final class UiPreferences {
    public static final int BACKDROP_COLOR = 0x6E000000;
    /** The UI always uses the Minecraft font; the value is kept only so old config files still load. */
    public static final String MINECRAFT_FONT = "minecraft";
    public static final int MAX_SCALE = 8;
    private UiPreferences() { }
    public static String normalizeFont(String value) { return MINECRAFT_FONT; }
    public static int clampScalePx(int value) { return Math.max(0, Math.min(MAX_SCALE, value)); }
    /** Largest whole-number scale at which the minimum panel (520x360 units) still fits the window. */
    public static int maxRenderScale(int width, int height) {
        int limit = (int) Math.floor(Math.min(Math.max(1, width) / 520.0, Math.max(1, height) / 360.0));
        return Math.max(1, Math.min(MAX_SCALE, limit));
    }
    /**
     * Framebuffer pixels per UI unit, always a whole number so edges, glyphs and icons land on screen pixels.
     * requested == 0 means automatic (follows Minecraft's GUI scale); otherwise the explicit value is used,
     * capped to what the window can hold.
     */
    public static float renderPixels(int width, int height, int minecraftScale, int requested) {
        int max = maxRenderScale(width, height);
        int wanted = requested <= 0 ? ResponsiveLayout.integerRenderScale(width, height, minecraftScale) : requested;
        return Math.max(1, Math.min(wanted, max));
    }
}
