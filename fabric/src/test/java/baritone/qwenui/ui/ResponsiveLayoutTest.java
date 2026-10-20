package baritone.qwenui.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResponsiveLayoutTest {
    @Test public void largeViewportHonorsAllThreeCustomScales() {
        for(int requested=1;requested<=3;requested++){
            var fit=ResponsiveLayout.fit(1920,1440,requested,900,560);
            assertEquals((float)requested,fit.scale(),0.00001f);
            assertTrue((fit.px()+fit.pw())*fit.scale()<=1920);
            assertTrue((fit.py()+fit.ph())*fit.scale()<=1440);
        }
    }

    @Test
    public void integerRenderScaleTracksResolutionWithoutFractionalScaling() {
        assertEquals(2, ResponsiveLayout.integerRenderScale(1920, 1080, 4));
        assertEquals(1, ResponsiveLayout.integerRenderScale(1280, 720, 4));
        assertEquals(1, ResponsiveLayout.integerRenderScale(640, 360, 4));
        assertEquals(1, ResponsiveLayout.integerRenderScale(320, 240, 0));
        assertEquals(1, ResponsiveLayout.integerRenderScale(3840, 2160, 1));
        assertEquals(3, ResponsiveLayout.integerRenderScale(3840, 2160, 3));
    }

    @Test
    public void automaticLayoutUsesLogicalCoordinatesAndCapsDefaultPanel() {
        ResponsiveLayout.Layout layout = ResponsiveLayout.fitAutomatic(1920, 1080, 900, 560, false);
        assertEquals(1.0f, layout.scale(), 0.0f);
        assertEquals(1000, layout.pw());
        assertEquals(650, layout.ph());
        assertTrue(layout.px() >= 0 && layout.px() + layout.pw() <= 1920);
        assertTrue(layout.py() >= 0 && layout.py() + layout.ph() <= 1080);

        ResponsiveLayout.Layout lowResolution = ResponsiveLayout.fitAutomatic(640, 344, 900, 560, false);
        assertEquals(1.0f, lowResolution.scale(), 0.0f);
        assertTrue(lowResolution.pw() <= 624);
        assertTrue(lowResolution.ph() <= 328);
        assertTrue(lowResolution.px() >= 0 && lowResolution.px() + lowResolution.pw() <= 640);
        assertTrue(lowResolution.py() >= 0 && lowResolution.py() + lowResolution.ph() <= 344);
    }

    @Test
    public void customSizeIsClampedToMinimumAndViewport() {
        ResponsiveLayout.Layout minimum = ResponsiveLayout.fitAutomatic(1280, 720, 1, 1, true);
        assertEquals(504, minimum.pw());
        assertEquals(320, minimum.ph());

        ResponsiveLayout.Layout maximum = ResponsiveLayout.fitAutomatic(640, 360, 4096, 4096, true);
        assertEquals(624, maximum.pw());
        assertEquals(344, maximum.ph());
        assertTrue(maximum.px() >= 0 && maximum.px() + maximum.pw() <= 640);
        assertTrue(maximum.py() >= 0 && maximum.py() + maximum.ph() <= 360);

        ResponsiveLayout.Layout tiny = ResponsiveLayout.fitAutomatic(320, 240, 900, 560, true);
        assertTrue(tiny.pw() <= 304 && tiny.ph() <= 224);
    }

    private static final int[][] VIEWPORTS = {
            {320, 240}, {426, 240}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}
    };
    private static final int[] GUI_SCALES = {1, 2, 3, 4};
    private static final int[] REQUESTED_SCALES = {1, 2, 3};
    private static final int[][] PREFERENCES = {
            {240, 240}, {564, 444}, {700, 700}, {4096, 4096}
    };

    @Test
    public void viewportAndGuiScaleMatrixKeepsGeometryInsideScreen() {
        int checkedCases = 0;
        for (int[] viewport : VIEWPORTS) {
            for (int guiScale : GUI_SCALES) {
                int width = Math.max(1, viewport[0] / guiScale);
                int height = Math.max(1, viewport[1] / guiScale);
                for (int requestedScale : REQUESTED_SCALES) {
                    for (int[] preference : PREFERENCES) {
                        ResponsiveLayout.Layout layout = ResponsiveLayout.fit(
                                width, height, requestedScale, preference[0], preference[1]);
                        assertLayoutBounds(width, height, requestedScale, layout);
                        assertFormAndPaginationRows(layout);
                        checkedCases++;
                    }
                }
            }
        }
        assertEquals("6 viewports × 4 GUI scales × 3 requested scales × 4 preferences",
                288, checkedCases);
    }

    @Test
    public void nonpositiveViewportsAndOutOfRangeRequestsAreClamped() {
        ResponsiveLayout.Layout layout = ResponsiveLayout.fit(0, -8, 9, 240, 240);
        assertTrue(Float.isFinite(layout.scale()));
        assertTrue(layout.scale() > 0.0f);
        assertTrue(layout.scale() <= 3.0f);
        assertEquals(504, layout.pw());
        assertEquals(384, layout.ph());
        assertEquals(358, layout.cw());
        assertEquals(285, layout.ch());
    }

    private static void assertLayoutBounds(int width, int height, int requestedScale,
                                           ResponsiveLayout.Layout layout) {
        assertTrue("scale must be finite", Float.isFinite(layout.scale()));
        assertTrue("scale must be positive", layout.scale() > 0.0f);
        assertTrue("scale exceeds requested scale", layout.scale() <= requestedScale);
        assertTrue("scale exceeds width fit",
                layout.scale() <= width / 520.0f + 1.0e-7f);
        assertTrue("scale exceeds height fit",
                layout.scale() <= height / 400.0f + 1.0e-7f);

        int screenWidth = logicalDimension(width, layout.scale());
        int screenHeight = logicalDimension(height, layout.scale());
        assertTrue("panel width is not positive", layout.pw() > 0);
        assertTrue("panel height is not positive", layout.ph() > 0);
        assertTrue("panel not inside screen horizontally",
                layout.px() >= 0 && layout.px() + layout.pw() <= screenWidth);
        assertTrue("panel not inside screen vertically",
                layout.py() >= 0 && layout.py() + layout.ph() <= screenHeight);
        assertTrue("panel is not centered horizontally",
                Math.abs(2L * layout.px() + layout.pw() - screenWidth) <= 1);
        assertTrue("panel is not centered vertically",
                Math.abs(2L * layout.py() + layout.ph() - screenHeight) <= 1);

        assertEquals(layout.px() + layout.rail() + 16, layout.cx());
        assertEquals(layout.py() + 60, layout.cy());
        assertTrue("content must begin after the side rail", layout.cx() >= layout.px() + layout.rail());
        assertTrue("content exceeds panel horizontally",
                layout.cx() >= layout.px() && layout.cx() + layout.cw() <= layout.px() + layout.pw());
        assertTrue("content exceeds panel vertically",
                layout.cy() >= layout.py() && layout.cy() + layout.ch() <= layout.py() + layout.ph());
        assertTrue("content minimum width", layout.cw() >= 358);
        assertTrue("content minimum height", layout.ch() >= 285);
        assertTrue("rail does not match panel width",
                layout.rail() == (layout.pw() < 700 ? 116 : 144));
    }

    private static void assertFormAndPaginationRows(ResponsiveLayout.Layout layout) {
        int navHeight=Math.min(30,Math.max(22,(layout.ph()-100)/8-3));
        int navBottom=layout.py()+60+7*(navHeight+3)+navHeight;
        assertTrue("navigation overlaps stop footer",navBottom<=layout.py()+layout.ph()-39);
        int formRows = (layout.ch() - 143) / 43;
        assertTrue("form should have at least one row", formRows >= 1);
        int lastFormBottom = layout.cy() + 87 + (formRows - 1) * 43 + 38;
        int formFooterTop = layout.cy() + layout.ch() - 54;
        assertTrue("form row overlaps footer", lastFormBottom <= formFooterTop);

        int paginatedRows = (layout.ch() - 91) / 43;
        assertTrue("paginated view should have at least one row", paginatedRows >= 1);
        int lastPaginatedBottom = layout.cy() + 65 + (paginatedRows - 1) * 43 + 37;
        int pagerTop = layout.cy() + layout.ch() - 21;
        assertTrue("paginated row overlaps pager", lastPaginatedBottom <= pagerTop - 4);
    }

    private static int logicalDimension(int viewportDimension, float scale) {
        double quotient = viewportDimension / (double) scale;
        double epsilon = Math.max(1.0e-9, Math.ulp(quotient) * 4.0);
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(quotient + epsilon));
    }
}
