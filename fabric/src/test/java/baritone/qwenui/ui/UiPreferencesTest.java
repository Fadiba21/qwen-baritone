package baritone.qwenui.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class UiPreferencesTest {
    @Test public void fontIsAlwaysMinecraft(){assertEquals("minecraft",UiPreferences.normalizeFont(null));assertEquals("minecraft",UiPreferences.normalizeFont("sans"));assertEquals("minecraft",UiPreferences.normalizeFont("Minecraft"));}
    @Test public void scalePreferenceIsBounded(){assertEquals(0,UiPreferences.clampScalePx(-99));assertEquals(8,UiPreferences.clampScalePx(999));assertEquals(3,UiPreferences.clampScalePx(3));}
    @Test public void explicitScaleChangesRenderScale(){assertEquals(1f,UiPreferences.renderPixels(1920,1080,3,1),0f);assertEquals(2f,UiPreferences.renderPixels(1920,1080,3,2),0f);assertEquals(3f,UiPreferences.renderPixels(1920,1080,3,3),0f);}
    @Test public void explicitScaleIsCappedToWhatFits(){assertEquals(2f,UiPreferences.renderPixels(1280,720,3,8),0f);assertEquals(2,UiPreferences.maxRenderScale(1280,720));assertEquals(1,UiPreferences.maxRenderScale(400,300));}
    @Test public void smallWindowsKeepMinimumUsableViewport(){for(int w:new int[]{640,854,1280,1920})for(int h:new int[]{360,480,720,1080})for(int req=0;req<=8;req++){float scale=UiPreferences.renderPixels(w,h,4,req);assertTrue(w/scale>=519.99||scale==1f);assertTrue(h/scale>=359.99||scale==1f);}}
    @Test public void backdropIsConstantNotTabOrAnimationDependent(){assertEquals(110,UiPreferences.BACKDROP_COLOR>>>24);assertEquals(0,UiPreferences.BACKDROP_COLOR&0xFFFFFF);}
    @Test public void renderScaleIsAlwaysAWholeNumberSoEdgesStaySharp(){for(int w:new int[]{427,640,854,1280,1366,1920,2560})for(int h:new int[]{240,360,480,720,768,1080,1440})for(int gui=1;gui<=6;gui++)for(int req=0;req<=8;req++){float scale=UiPreferences.renderPixels(w,h,gui,req);assertEquals(Math.rint(scale),scale,0.0f);assertTrue(scale>=1);}}
    @Test public void tinyWindowsFallBackToScaleOne(){assertEquals(1f,UiPreferences.renderPixels(400,300,4,8),0f);assertEquals(1f,UiPreferences.renderPixels(640,360,2,0),0f);}
}
