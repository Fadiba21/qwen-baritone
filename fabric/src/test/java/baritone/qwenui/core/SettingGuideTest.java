package baritone.qwenui.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class SettingGuideTest {
    @Test public void classifiesBaritoneTypeStrings() {
        assertEquals(SettingGuide.Kind.BOOL, SettingGuide.kindOf("Boolean"));
        assertEquals(SettingGuide.Kind.INT, SettingGuide.kindOf("Integer"));
        assertEquals(SettingGuide.Kind.INT, SettingGuide.kindOf("Long"));
        assertEquals(SettingGuide.Kind.DECIMAL, SettingGuide.kindOf("Double"));
        assertEquals(SettingGuide.Kind.BLOCK_LIST, SettingGuide.kindOf("List<Block>"));
        assertEquals(SettingGuide.Kind.MAP, SettingGuide.kindOf("Map<Block, List<Block>>"));
        assertEquals(SettingGuide.Kind.COLOR, SettingGuide.kindOf("Color"));
        assertEquals(SettingGuide.Kind.ENUM, SettingGuide.kindOf("Mirror"));
    }

    @Test public void bundledMetadataLoadsAndKnowsAreasAndRanges() {
        assertTrue(SettingGuide.metas().size() > 200);
        var meta = SettingGuide.meta("maxFallHeightNoWater");
        assertNotNull(meta);
        assertEquals("move", meta.area());
        assertEquals(50.0, meta.max(), 0);
        for (var m : SettingGuide.metas().values()) assertTrue(m.name(), SettingGuide.areaKeys().contains(m.area()));
    }

    @Test public void numericGuideOffersRangePresetsAndStepping() {
        var g = SettingGuide.of("maxFallHeightNoWater", "Integer", "3", "3");
        assertTrue(g.hasRange());
        assertEquals("4", SettingGuide.adjust(g, "3", g.step(), 1));
        assertEquals("50", SettingGuide.adjust(g, "49", 5, 1));
        assertEquals("0", SettingGuide.adjust(g, "1", -5, 1));
        assertEquals("6", SettingGuide.adjust(g, "3", 0, 2));
        assertNull(SettingGuide.adjust(g, "abc", 1, 1));
        assertTrue(g.presets().stream().anyMatch(p -> p.label().startsWith("max")));
    }

    @Test public void decimalStepsDoNotAccumulateFloatNoise() {
        var g = SettingGuide.of("jumpPenalty", "Double", "2.0", "2.0");
        assertEquals("2.5", SettingGuide.adjust(g, "2.0", 0.5, 1));
        assertEquals("0.3", SettingGuide.adjust(g, "0.1", 0.2, 1));
    }

    @Test public void listsAppendWithoutDuplicatesAndClear() {
        assertEquals("dirt,stone", SettingGuide.listAdd("dirt", "stone"));
        assertEquals("dirt,stone", SettingGuide.listAdd("dirt,stone", "stone"));
        assertEquals("dirt", SettingGuide.listAdd("", "dirt"));
        assertEquals("", SettingGuide.listAdd("dirt", ""));
        var g = SettingGuide.of("blocksToAvoid", "List<Block>", "cactus", "cactus");
        assertTrue(g.appendMode());
        assertTrue(g.presets().stream().anyMatch(p -> p.value().isEmpty()));
    }

    @Test public void everyKindHasHowToAndExampleAndChatLine() {
        for (String type : new String[] { "Boolean", "Integer", "Double", "String", "Block", "Item", "List<Block>", "List<Item>", "List<String>", "Color", "Vec3i", "Mirror", "Rotation", "Map<Block, List<Block>>", "Weird" }) {
            var g = SettingGuide.of("someSetting", type, null, null);
            assertFalse(type, SettingGuide.howTo(g, false).isBlank());
            assertFalse(type, SettingGuide.howTo(g, true).isBlank());
            assertTrue(type, SettingGuide.chatExample(g).startsWith("#set someSetting"));
        }
        assertEquals("#set allowBreak true", SettingGuide.chatExample(SettingGuide.of("allowBreak", "Boolean", "true", "true")));
    }

    @Test public void numberValidation() {
        assertTrue(SettingGuide.isValidNumber(SettingGuide.Kind.INT, " 12 "));
        assertFalse(SettingGuide.isValidNumber(SettingGuide.Kind.INT, "1.5"));
        assertTrue(SettingGuide.isValidNumber(SettingGuide.Kind.DECIMAL, "1.5"));
        assertFalse(SettingGuide.isValidNumber(SettingGuide.Kind.DECIMAL, "NaN"));
    }
}
