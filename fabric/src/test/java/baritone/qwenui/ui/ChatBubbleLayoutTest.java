package baritone.qwenui.ui;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChatBubbleLayoutTest {
    @Test public void shortAndLongWrappedTextGetContentSizedWidths() {
        var shortBubble = ChatBubbleLayout.measure(300, 42, List.of(28));
        var longBubble = ChatBubbleLayout.measure(300, 42, List.of(28, 95, 56));
        assertEquals(ChatBubbleLayout.MIN_WIDTH, shortBubble.width());
        assertEquals(115, longBubble.width());
        assertEquals(47, shortBubble.height());
        assertEquals(73, longBubble.height());
        assertTrue(longBubble.width() > shortBubble.width());
        assertTrue(longBubble.height() > shortBubble.height());
    }

    @Test public void headingWidthAndMinimumWidthAreIncluded() {
        assertEquals(108, ChatBubbleLayout.measure(300, 88, List.of(20)).width());
        assertEquals(ChatBubbleLayout.MIN_WIDTH, ChatBubbleLayout.measure(300, 0, List.of(12)).width());
    }

    @Test public void narrowAndInvalidAvailableWidthsAreSafelyClamped() {
        for (int available : new int[]{-10, 0, 1, 32, 63}) {
            var bubble = ChatBubbleLayout.measure(available, 500, List.of(900));
            assertTrue(bubble.width() >= 0);
            assertTrue(bubble.width() <= Math.max(0, available));
        }
        assertEquals(64, ChatBubbleLayout.measure(64, 0, List.of()).width());
    }

    @Test public void hugeContentWidthIsBoundedByAvailableWidth() {
        assertEquals(140, ChatBubbleLayout.measure(140, Integer.MAX_VALUE, List.of(Integer.MAX_VALUE)).width());
        assertEquals(300, ChatBubbleLayout.measure(300, 0, List.of(500)).width());
    }

    @Test public void manyLinesIncreaseHeightWithoutChangingWidthPastLimit() {
        var bubble = ChatBubbleLayout.measure(100, 50, List.of(20, 40, 60, 80, 90, 95));
        assertEquals(100, bubble.width());
        assertEquals(34 + 6 * ChatBubbleLayout.LINE_HEIGHT, bubble.height());
    }

    @Test public void stackedBubblesHaveExplicitNonOverlappingSpacing() {
        var first = new ChatBubbleLayout.Bubble(80, 47);
        var second = new ChatBubbleLayout.Bubble(100, 73);
        var third = new ChatBubbleLayout.Bubble(64, 34);
        int extent = ChatBubbleLayout.contentHeight(List.of(first, second, third));
        assertEquals(first.height() + ChatBubbleLayout.BUBBLE_SPACING + second.height()
                + ChatBubbleLayout.BUBBLE_SPACING + third.height(), extent);
        int secondY = first.height() + ChatBubbleLayout.BUBBLE_SPACING;
        int thirdY = secondY + second.height() + ChatBubbleLayout.BUBBLE_SPACING;
        assertTrue(secondY >= first.height());
        assertTrue(thirdY >= secondY + second.height());
        assertEquals(0, ChatBubbleLayout.contentHeight(List.of()));
    }

    @Test public void scrollOffsetIsClampedToViewportRange() {
        assertEquals(0, ChatBubbleLayout.clampScroll(-4, 100, 30));
        assertEquals(35, ChatBubbleLayout.clampScroll(35, 100, 65));
        assertEquals(70, ChatBubbleLayout.clampScroll(999, 100, 30));
        assertEquals(0, ChatBubbleLayout.clampScroll(10, 20, 40));
    }
}
