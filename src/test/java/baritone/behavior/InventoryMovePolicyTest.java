package baritone.behavior;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class InventoryMovePolicyTest {
    @Test public void onlyOneOfAll32ContextsAllowsAutomation() {
        int allowed = 0;
        for (int bits = 0; bits < 32; bits++) {
            boolean enabled = (bits & 1) != 0;
            boolean inWorld = (bits & 2) != 0;
            boolean screenOpen = (bits & 4) != 0;
            boolean playerMenu = (bits & 8) != 0;
            boolean cursorEmpty = (bits & 16) != 0;
            boolean result = InventoryMovePolicy.canAutomate(enabled, inWorld, screenOpen, playerMenu, cursorEmpty);
            assertEquals("context " + bits, bits == 27, result);
            if (result) allowed++;
        }
        assertEquals(1, allowed);
    }

    @Test public void hotbarAndMainInventoryMapTo36UniqueMenuSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < 36; i++) {
            int mapped = InventoryMovePolicy.menuSlot(i);
            assertTrue(mapped >= 9 && mapped <= 44);
            assertTrue(slots.add(mapped));
            assertEquals(i < 9 ? i + 36 : i, mapped);
        }
        assertEquals(36, slots.size());
    }

    @Test public void everyValidSourceAndDestinationExcludesSelfSwaps() {
        for (int source = 0; source < 36; source++) {
            for (int target = 0; target < 9; target++) {
                assertEquals(source >= 9, InventoryMovePolicy.validSwap(source, target));
            }
        }
    }

    @Test public void equipmentOffhandAndInvalidIndicesAreNeverSwapSources() {
        for (int source : new int[]{Integer.MIN_VALUE, -1, 36, 40, 45, Integer.MAX_VALUE}) {
            assertFalse(InventoryMovePolicy.validSwap(source, 0));
            try {
                InventoryMovePolicy.menuSlot(source);
                fail("accepted source " + source);
            } catch (IllegalArgumentException expected) { }
        }
        for (int target : new int[]{Integer.MIN_VALUE, -1, 9, 36, 40, Integer.MAX_VALUE}) {
            assertFalse(InventoryMovePolicy.validSwap(9, target));
        }
    }
}
