package baritone.qwenui.ui;

/** Pure layout calculation for a centered, scalable UI panel. */
public final class ResponsiveLayout {
    private static final int MIN_LOGICAL_WIDTH = 520;
    private static final int MIN_LOGICAL_HEIGHT = 400;
    private static final int MIN_PANEL_WIDTH = 504;
    private static final int MIN_PANEL_HEIGHT = 320;

    private ResponsiveLayout() {
    }

    /**
     * Resulting scale and logical-coordinate rectangles. The screen itself is
     * the logical viewport implied by the supplied physical viewport and scale.
     */
    public record Layout(float scale, int px, int py, int pw, int ph,
                         int cx, int cy, int cw, int ch, int rail) {
    }

    /**
     * Selects an integer framebuffer-to-logical render scale no larger than
     * Minecraft's integer GUI scale. The minimum logical viewport is kept when
     * possible; tiny framebuffers fall back to scale 1 rather than being
     * fractionally downscaled.
     */
    public static int integerRenderScale(int framebufferWidth, int framebufferHeight,
                                         int minecraftGuiScale) {
        int guiScale = Math.max(1, minecraftGuiScale);
        int width = Math.max(1, framebufferWidth);
        int height = Math.max(1, framebufferHeight);
        int widthLimit = width / MIN_LOGICAL_WIDTH;
        int heightLimit = height / MIN_LOGICAL_HEIGHT;
        int fittingScale = Math.min(widthLimit, heightLimit);
        return Math.max(1, Math.min(guiScale, fittingScale));
    }

    /**
     * Fits the panel into a logical viewport. The returned drawing scale is
     * always 1: callers choose an integer framebuffer render scale separately.
     * Default sizing uses 92% × 90%, capped at 1000 × 650. When custom sizing
     * is enabled, the supplied preference is clamped to the available viewport
     * and a 504 × 320 minimum where the viewport permits it.
     */
    public static Layout fitAutomatic(int viewportWidth, int viewportHeight,
                                      int preferredWidth, int preferredHeight,
                                      boolean customSize) {
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        int availableWidth = Math.max(1, width - 16);
        int availableHeight = Math.max(1, height - 16);
        int panelWidth;
        int panelHeight;
        if (customSize) {
            panelWidth = boundedPanelDimension(preferredWidth, availableWidth, MIN_PANEL_WIDTH);
            panelHeight = boundedPanelDimension(preferredHeight, availableHeight, MIN_PANEL_HEIGHT);
        } else {
            int defaultWidth = Math.min(1000, (int) Math.floor(width * 0.92));
            int defaultHeight = Math.min(650, (int) Math.floor(height * 0.90));
            panelWidth = Math.min(availableWidth, Math.max(MIN_PANEL_WIDTH, defaultWidth));
            panelHeight = Math.min(availableHeight, Math.max(MIN_PANEL_HEIGHT, defaultHeight));
        }

        int rail = panelWidth < 700 ? 116 : 144;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int contentX = panelX + rail + 16;
        int contentY = panelY + 60;
        int contentWidth = Math.max(1, panelWidth - rail - 30);
        int contentHeight = Math.max(1, panelHeight - 99);
        return new Layout(1.0f, panelX, panelY, panelWidth, panelHeight,
                contentX, contentY, contentWidth, contentHeight, rail);
    }

    private static int boundedPanelDimension(int preferred, int available, int minimum) {
        return Math.min(available, Math.max(Math.min(minimum, available), preferred));
    }

    /**
     * Fits the UI into a physical viewport. A viewport dimension less than one
     * is treated as one pixel; logical dimensions are capped at {@code int}
     * range because all returned geometry is represented by integers.
     */
    public static Layout fit(int viewportWidth, int viewportHeight,
                             int requestedScale, int preferredWidth, int preferredHeight) {
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        int requested = Math.max(1, Math.min(3, requestedScale));

        double exactScale = Math.min(requested,
                Math.min(width / (double) MIN_LOGICAL_WIDTH,
                        height / (double) MIN_LOGICAL_HEIGHT));
        float scale = (float) exactScale;
        // A rounded-up float could exceed the limiting viewport ratio. Keep
        // the public float scale on the safe side of the exact fit.
        if (scale > exactScale) {
            scale = Math.nextDown(scale);
        }

        int logicalWidth = logicalDimension(width, scale);
        int logicalHeight = logicalDimension(height, scale);
        int panelWidth = (int) Math.min(Math.max(504L, (long) preferredWidth),
                (long) logicalWidth - 16L);
        int panelHeight = (int) Math.min(Math.max(384L, (long) preferredHeight),
                (long) logicalHeight - 16L);
        int rail = panelWidth < 700 ? 116 : 144;
        int panelX = (logicalWidth - panelWidth) / 2;
        int panelY = (logicalHeight - panelHeight) / 2;
        int contentX = panelX + rail + 16;
        int contentY = panelY + 60;
        int contentWidth = panelWidth - rail - 30;
        int contentHeight = panelHeight - 99;

        return new Layout(scale, panelX, panelY, panelWidth, panelHeight,
                contentX, contentY, contentWidth, contentHeight, rail);
    }

    private static int logicalDimension(int viewportDimension, float scale) {
        double quotient = viewportDimension / (double) scale;
        // Compensate for a few ULPs of division noise so an exact logical
        // minimum (for example 400) does not floor to one pixel less.
        double epsilon = Math.max(1.0e-9, Math.ulp(quotient) * 4.0);
        double floored = Math.floor(quotient + epsilon);
        return (int) Math.max(1.0, Math.min(Integer.MAX_VALUE, floored));
    }
}
