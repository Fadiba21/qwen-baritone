package baritone.qwenui.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WindowResizeTest {
    private static final WindowResize.Rect WINDOW = new WindowResize.Rect(100, 100, 600, 400);

    @Test
    public void hitIdentifiesAllEightResizeHandlesAndMoveArea() {
        assertEquals(WindowResize.LEFT, WindowResize.hit(WINDOW, 100, 250, 8));
        assertEquals(WindowResize.RIGHT, WindowResize.hit(WINDOW, 700, 250, 8));
        assertEquals(WindowResize.TOP, WindowResize.hit(WINDOW, 400, 100, 8));
        assertEquals(WindowResize.BOTTOM, WindowResize.hit(WINDOW, 400, 500, 8));

        assertEquals(WindowResize.LEFT | WindowResize.TOP, WindowResize.hit(WINDOW, 100, 100, 8));
        assertEquals(WindowResize.RIGHT | WindowResize.TOP, WindowResize.hit(WINDOW, 700, 100, 8));
        assertEquals(WindowResize.LEFT | WindowResize.BOTTOM, WindowResize.hit(WINDOW, 100, 500, 8));
        assertEquals(WindowResize.RIGHT | WindowResize.BOTTOM, WindowResize.hit(WINDOW, 700, 500, 8));

        assertEquals(WindowResize.MOVE, WindowResize.hit(WINDOW, 400, 300, 8));
        assertEquals(0, WindowResize.hit(WINDOW, 91.99, 250, 8));
        assertEquals(WindowResize.LEFT, WindowResize.hit(WINDOW, 95, 250, 8));
    }

    @Test
    public void dragMovesAndResizesWithOppositeEdgesAnchored() {
        assertEquals(new WindowResize.Rect(130, 100, 570, 400),
                WindowResize.drag(WINDOW, WindowResize.LEFT, 30, 0, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 100, 620, 400),
                WindowResize.drag(WINDOW, WindowResize.RIGHT, 20, 0, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 120, 600, 380),
                WindowResize.drag(WINDOW, WindowResize.TOP, 0, 20, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 100, 600, 430),
                WindowResize.drag(WINDOW, WindowResize.BOTTOM, 0, 30, 1000, 800));
        assertEquals(new WindowResize.Rect(130, 120, 570, 380),
                WindowResize.drag(WINDOW, WindowResize.LEFT | WindowResize.TOP,
                        30, 20, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 120, 620, 380),
                WindowResize.drag(WINDOW, WindowResize.RIGHT | WindowResize.TOP,
                        20, 20, 1000, 800));
        assertEquals(new WindowResize.Rect(130, 100, 570, 430),
                WindowResize.drag(WINDOW, WindowResize.LEFT | WindowResize.BOTTOM,
                        30, 30, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 100, 620, 430),
                WindowResize.drag(WINDOW, WindowResize.RIGHT | WindowResize.BOTTOM,
                        20, 30, 1000, 800));

        assertEquals(new WindowResize.Rect(150, 125, 600, 400),
                WindowResize.drag(WINDOW, WindowResize.MOVE, 50, 25, 1000, 800));
    }

    @Test
    public void resizeClampsToMinimumAndViewportBounds() {
        assertEquals(new WindowResize.Rect(196, 100, 504, 400),
                WindowResize.drag(WINDOW, WindowResize.LEFT, 500, 0, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 100, 504, 400),
                WindowResize.drag(WINDOW, WindowResize.RIGHT, -500, 0, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 180, 600, 320),
                WindowResize.drag(WINDOW, WindowResize.TOP, 0, 500, 1000, 800));
        assertEquals(new WindowResize.Rect(100, 100, 600, 320),
                WindowResize.drag(WINDOW, WindowResize.BOTTOM, 0, -500, 1000, 800));

        assertEquals(new WindowResize.Rect(8, 8, 600, 400),
                WindowResize.drag(WINDOW, WindowResize.MOVE, -500, -500, 800, 600));
        assertEquals(new WindowResize.Rect(100, 100, 692, 400),
                WindowResize.drag(WINDOW, WindowResize.RIGHT, 1000, 0, 800, 600));
        assertEquals(new WindowResize.Rect(8, 8, 304, 184),
                WindowResize.drag(WINDOW, WindowResize.MOVE, 0, 0, 320, 200));
    }

    @Test
    public void tinyViewportUsesBestEffortZeroMarginBounds() {
        assertEquals(new WindowResize.Rect(0, 0, 12, 8),
                WindowResize.drag(WINDOW, WindowResize.MOVE, -500, -500, 12, 8));
    }
}
