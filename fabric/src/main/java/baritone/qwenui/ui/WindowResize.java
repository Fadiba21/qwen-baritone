package baritone.qwenui.ui;

/** Geometry helpers for hit-testing and dragging a resizable window. */
public final class WindowResize {
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int TOP = 4;
    public static final int BOTTOM = 8;
    public static final int MOVE = 16;

    private static final int MARGIN = 8;
    private static final int MIN_WIDTH = 504;
    private static final int MIN_HEIGHT = 320;

    private WindowResize() {
    }

    public record Rect(int x, int y, int width, int height) {
    }

    /**
     * Returns the applicable resize edge(s), MOVE for the non-edge interior,
     * or zero when the point is outside the rectangle and its grip.
     */
    public static int hit(Rect r, double x, double y, int grip) {
        if (r == null || r.width() < 0 || r.height() < 0
                || Double.isNaN(x) || Double.isNaN(y)) {
            return 0;
        }

        double allowance = Math.max(0, grip);
        double left = r.x();
        double top = r.y();
        double right = left + r.width();
        double bottom = top + r.height();
        if (x < left - allowance || x > right + allowance
                || y < top - allowance || y > bottom + allowance) {
            return 0;
        }

        int edges = 0;
        if (x <= left + allowance) {
            edges |= LEFT;
        }
        if (x >= right - allowance) {
            edges |= RIGHT;
        }
        if (y <= top + allowance) {
            edges |= TOP;
        }
        if (y >= bottom - allowance) {
            edges |= BOTTOM;
        }
        if (edges != 0) {
            return edges;
        }
        return x >= left && x <= right && y >= top && y <= bottom ? MOVE : 0;
    }

    /**
     * Applies a drag, keeping the result within the viewport with an 8-pixel
     * margin when possible. Minimum dimensions are reduced when the viewport
     * cannot accommodate them.
     */
    public static Rect drag(Rect start, int edges, int dx, int dy,
                            int viewportWidth, int viewportHeight) {
        if (start == null) {
            throw new NullPointerException("start");
        }

        int vw = Math.max(0, viewportWidth);
        int vh = Math.max(0, viewportHeight);
        int marginX = vw >= 2 * MARGIN ? MARGIN : 0;
        int marginY = vh >= 2 * MARGIN ? MARGIN : 0;
        int availableWidth = Math.max(0, vw - 2 * marginX);
        int availableHeight = Math.max(0, vh - 2 * marginY);
        int minWidth = Math.min(MIN_WIDTH, availableWidth);
        int minHeight = Math.min(MIN_HEIGHT, availableHeight);

        long x = start.x();
        long y = start.y();
        long width = Math.max(0L, start.width());
        long height = Math.max(0L, start.height());

        boolean resizeLeft = (edges & LEFT) != 0;
        boolean resizeRight = !resizeLeft && (edges & RIGHT) != 0;
        boolean resizeTop = (edges & TOP) != 0;
        boolean resizeBottom = !resizeTop && (edges & BOTTOM) != 0;

        if (resizeLeft || resizeRight) {
            if (resizeLeft) {
                long anchor = clamp((long) start.x() + Math.max(0L, start.width()),
                        marginX, (long) vw - marginX);
                long capacity = Math.max(0L, anchor - marginX);
                long maxWidth = Math.min(availableWidth, capacity);
                long axisMin = Math.min(minWidth, maxWidth);
                width = clamp((long) start.width() - dx, axisMin, maxWidth);
                x = anchor - width;
            } else {
                long anchor = clamp(start.x(), marginX, (long) vw - marginX);
                long capacity = Math.max(0L, (long) vw - marginX - anchor);
                long maxWidth = Math.min(availableWidth, capacity);
                long axisMin = Math.min(minWidth, maxWidth);
                width = clamp((long) start.width() + dx, axisMin, maxWidth);
                x = anchor;
            }
        } else {
            width = clamp(width, minWidth, availableWidth);
            x = clamp(x + ((edges & MOVE) != 0 ? dx : 0),
                    marginX, (long) vw - marginX - width);
        }

        if (resizeTop || resizeBottom) {
            if (resizeTop) {
                long anchor = clamp((long) start.y() + Math.max(0L, start.height()),
                        marginY, (long) vh - marginY);
                long capacity = Math.max(0L, anchor - marginY);
                long maxHeight = Math.min(availableHeight, capacity);
                long axisMin = Math.min(minHeight, maxHeight);
                height = clamp((long) start.height() - dy, axisMin, maxHeight);
                y = anchor - height;
            } else {
                long anchor = clamp(start.y(), marginY, (long) vh - marginY);
                long capacity = Math.max(0L, (long) vh - marginY - anchor);
                long maxHeight = Math.min(availableHeight, capacity);
                long axisMin = Math.min(minHeight, maxHeight);
                height = clamp((long) start.height() + dy, axisMin, maxHeight);
                y = anchor;
            }
        } else {
            height = clamp(height, minHeight, availableHeight);
            y = clamp(y + ((edges & MOVE) != 0 ? dy : 0),
                    marginY, (long) vh - marginY - height);
        }

        return new Rect(toInt(x), toInt(y), toInt(width), toInt(height));
    }

    private static long clamp(long value, long min, long max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int toInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
