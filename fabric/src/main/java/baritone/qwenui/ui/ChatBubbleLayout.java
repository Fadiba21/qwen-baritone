package baritone.qwenui.ui;

import java.util.List;

/** Pure sizing policy for chat bubbles; callers wrap text first and pass each rendered line width. */
public final class ChatBubbleLayout {
    public static final int PADDING = 10;
    public static final int LINE_HEIGHT = 13;
    public static final int HEADING_HEIGHT = 9;
    public static final int HEADING_BODY_SPACING = 8;
    /** Additional room after the final body row; keeps the last glyph clear of the bubble edge. */
    public static final int BODY_BOTTOM_PADDING = 7;
    public static final int BODY_TOP_OFFSET = PADDING + HEADING_HEIGHT + HEADING_BODY_SPACING; // 27px
    public static final int MIN_WIDTH = 64;
    public static final int BUBBLE_SPACING = 6;

    private ChatBubbleLayout() { }

    /** Immutable measured dimensions in UI pixels. */
    public record Bubble(int width, int height) { }

    /**
     * Measures a bubble around an already-wrapped heading and body. The supplied heading width
     * may include a caller's avatar/label inset; lineWidths contains one width per wrapped body line.
     * Width is bounded by availableWidth, with 10px horizontal padding and a 64px preferred minimum.
     */
    public static Bubble measure(int availableWidth, int headingWidth, List<Integer> lineWidths) {
        int maxWidth = Math.max(0, availableWidth);
        long requiredContentWidth = Math.max(0, headingWidth);
        int lineCount = 0;
        if (lineWidths != null) {
            for (Integer width : lineWidths) {
                if (width == null) continue;
                lineCount++;
                requiredContentWidth = Math.max(requiredContentWidth, Math.max(0, width));
            }
        }
        long desiredWidth = requiredContentWidth + (long) PADDING * 2;
        int width = (int) Math.min(maxWidth, Math.max(Math.min((long) MIN_WIDTH, maxWidth), desiredWidth));

        boolean hasHeading = headingWidth > 0;
        long height;
        if (hasHeading) {
            // Heading top is at +10 and body top at +27; height matches 34 + 13 * body lines.
            height = (long) BODY_TOP_OFFSET + (long) lineCount * LINE_HEIGHT + BODY_BOTTOM_PADDING;
        } else if (lineCount > 0) {
            height = (long) PADDING * 2 + (long) lineCount * LINE_HEIGHT;
        } else {
            height = (long) PADDING * 2;
        }
        return new Bubble(width, (int) Math.min(Integer.MAX_VALUE, height));
    }

    /** Total vertical extent of bubbles stacked in order with BUBBLE_SPACING between neighbors. */
    public static int contentHeight(List<Bubble> bubbles) {
        if (bubbles == null || bubbles.isEmpty()) return 0;
        long total = (long) BUBBLE_SPACING * (bubbles.size() - 1);
        for (Bubble bubble : bubbles) if (bubble != null) total += Math.max(0, bubble.height());
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Bounds a scroll offset to the actual scrollable content range. */
    public static int clampScroll(int scroll, int contentHeight, int viewportHeight) {
        long maxScroll = Math.max(0L, (long) contentHeight - Math.max(0, viewportHeight));
        return (int) Math.max(0L, Math.min(maxScroll, scroll));
    }
}
