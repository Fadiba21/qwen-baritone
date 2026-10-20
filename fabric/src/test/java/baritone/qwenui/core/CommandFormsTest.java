package baritone.qwenui.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CommandFormsTest {
    private static CommandForms.Variant variant(String command, String label) {
        return CommandForms.forCommand(command, "usage").stream().filter(v -> v.en().equals(label)).findFirst().orElseThrow();
    }
    @Test public void mineCountIsSerializedBeforeBlocks() {
        var v = variant("mine", "Count + blocks");
        assertEquals("mine 8 minecraft:diamond_ore minecraft:coal_ore", CommandForms.compose("mine", v, Map.of("count", "8", "blocks", "minecraft:diamond_ore   minecraft:coal_ore")));
    }
    @Test public void coordinateOptionalSuffixCanBeOmitted() {
        var v = variant("build", "File + position");
        assertEquals("build house.schematic", CommandForms.compose("build", v, Map.of("filename", "house.schematic")));
    }
    @Test public void coordinateGapsAreRejected() {
        var v = variant("goto", "Coordinates (X Y Z)");
        try { CommandForms.compose("goto", v, Map.of("x", "1", "z", "3")); fail("Expected coordinate gap rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void rejectsCommandAndArgumentInjection() {
        var v = variant("goto", "Coordinates (X Y Z)");
        for (String command : List.of("goto run", "#goto", "goto;stop")) {
            try { CommandForms.compose(command, v, Map.of("x", "1", "y", "2", "z", "3")); fail("accepted " + command); }
            catch (IllegalArgumentException expected) { }
        }
        try { CommandForms.compose("goto", v, Map.of("x", "1", "y", "2;stop", "z", "3")); fail("accepted injection"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void supportsEveryRegisteredCanonicalCommandWithoutGenericFallback() {
        List<String> builtins = List.of("help","set","goal","goto","path","proc","eta","version","repack","build","litematica","come","axis","forcecancel","gc","invert","tunnel","render","farm","follow","pickup","explorefilter","reloadall","saveall","explore","blacklist","find","mine","click","surface","thisway","waypoints","sel","elytra","pause","resume","paused","cancel");
        for (String command : builtins) {
            List<CommandForms.Variant> variants = CommandForms.forCommand(command, "sentinel usage");
            assertFalse("No variants for " + command, variants.isEmpty());
            assertFalse("Unexpected generic fallback for " + command, variants.get(0).en().equals("Parameters"));
        }
    }
    @Test public void unknownVariantValidationIgnoresUsageHint() {
        var fromActualUsage = CommandForms.forCommand("extension", "extension <thing>").get(0);
        assertEquals("extension hello", CommandForms.compose("extension", fromActualUsage, Map.of("arguments", "hello")));
    }
    @Test public void allowsNamespacedAddonCommandTokensButRejectsUnsafeTokens() {
        var form = CommandForms.forCommand("addon:command", "addon:command <arg>").get(0);
        assertEquals("addon:command value", CommandForms.compose("addon:command", form, Map.of("arguments", "value")));
        for (String invalid : List.of("addon:bad name", "addon:bad#name", "addon:bad;name")) {
            try { CommandForms.compose(invalid, form, Map.of()); fail("accepted " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void exposesSelectionTransformAndDirectionChoices() {
        var expand = variant("sel", "Expand");
        assertEquals(List.of("a", "all", "n", "newest", "o", "oldest"), expand.fields().get(0).choices());
        assertTrue(expand.fields().get(1).choices().contains("north"));
    }
    @Test public void unknownCommandGetsOneGenericField() {
        var forms = CommandForms.forCommand("extension", "extension <thing>");
        assertEquals(1, forms.size());
        assertEquals("arguments", forms.get(0).fields().get(0).key());
    }
}
