package baritone.qwenui.core;

import org.junit.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CommandHotkeysTest {
    private static final int CTRL = GLFW.GLFW_MOD_CONTROL;
    private static final int SHIFT = GLFW.GLFW_MOD_SHIFT;

    private static CommandHotkeys.Binding bind(String command, int key, int modifiers) {
        return new CommandHotkeys.Binding(command, key, modifiers);
    }

    private static CommandHotkeys.KeyState keys(int... pressed) {
        Set<Integer> down = new HashSet<>();
        for (int key : pressed) down.add(key);
        return down::contains;
    }

    @Test public void bindingValidatesDisplaysAndPreservesOptionalNativeHashPrefix() {
        var binding = bind("  # goto 1 2 3  ", GLFW.GLFW_KEY_G, CTRL | SHIFT);
        assertTrue(binding.isValid());
        assertNull(binding.validationError());
        assertEquals("Ctrl+Shift+G", binding.chordDisplay());
        assertEquals("Ctrl+Shift+G → # goto 1 2 3", binding.display());
        assertTrue(binding.sameChord(bind("mine diamonds", GLFW.GLFW_KEY_G, CTRL | SHIFT)));
    }

    @Test public void rejectsEmptyUnsafeOrMultilineCommands() {
        for (String command : new String[]{null, "", "  ", "#", "#   ", "goto\nstop", "goto\tstop", "goto" + (char) 1 + "stop"}) {
            var binding = bind(command, GLFW.GLFW_KEY_G, CTRL);
            assertFalse("accepted command " + command, binding.isValid());
        }
        assertFalse(bind("x".repeat(CommandHotkeys.MAX_COMMAND_LENGTH + 1), GLFW.GLFW_KEY_G, CTRL).isValid());
        assertTrue(bind("# goto 1 2 3", GLFW.GLFW_KEY_G, CTRL).isValid());
    }

    @Test public void permitsSingleKeysAndRequiresSupportedModifierBitsAndBindableNonReservedKey() {
        assertTrue(bind("goto 1 2 3", GLFW.GLFW_KEY_G, 0).isValid());
        assertFalse(bind("goto 1 2 3", GLFW.GLFW_KEY_G, GLFW.GLFW_MOD_SUPER).isValid());
        assertFalse(bind("goto 1 2 3", GLFW.GLFW_KEY_LEFT_CONTROL, CTRL).isValid());
        assertFalse(bind("goto 1 2 3", GLFW.GLFW_KEY_UNKNOWN, CTRL).isValid());
        assertFalse(bind("goto 1 2 3", GLFW.GLFW_KEY_F11, CTRL).isValid());
        assertFalse(bind("goto 1 2 3", GLFW.GLFW_KEY_END, CTRL).isValid());
    }

    @Test public void rejectsDuplicateChordsAndValidatesAnEditIgnoringItsOldEntry() {
        List<CommandHotkeys.Binding> bindings = new ArrayList<>();
        var first = bind("goto 1 2 3", GLFW.GLFW_KEY_G, CTRL);
        var duplicate = bind("mine diamond_ore", GLFW.GLFW_KEY_G, CTRL);
        bindings.add(first);
        CommandHotkeys.validateForSave(duplicate, bindings, 0); // editing the existing entry is allowed
        try {
            CommandHotkeys.validateForSave(duplicate, List.of(first, duplicate), -1);
            fail("expected duplicate rejection");
        } catch (IllegalArgumentException expected) { }
        CommandHotkeys.validateForSave(duplicate, List.of(first), 0); // the edited entry is ignored
        try {
            CommandHotkeys.validateBindings(List.of(first, duplicate));
            fail("expected list duplicate rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void duplicateValidationChecksCommandAndListLimit() {
        try {
            CommandHotkeys.validateForSave(bind("", GLFW.GLFW_KEY_G, CTRL), List.of(), -1);
            fail("expected invalid command rejection");
        } catch (IllegalArgumentException expected) { }
        List<CommandHotkeys.Binding> atLimit = new ArrayList<>();
        int[] modifiers = {0, CTRL, SHIFT, GLFW.GLFW_MOD_ALT, CTRL | SHIFT,
                CTRL | GLFW.GLFW_MOD_ALT, SHIFT | GLFW.GLFW_MOD_ALT, CTRL | SHIFT | GLFW.GLFW_MOD_ALT};
        outer: for (int mods : modifiers) for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
            atLimit.add(bind("help", key, mods));
            if (atLimit.size() == CommandHotkeys.MAX_BINDINGS) break outer;
        }
        CommandHotkeys.validateBindings(atLimit);
        try {
            CommandHotkeys.validateForSave(bind("help", GLFW.GLFW_KEY_A, CTRL | GLFW.GLFW_MOD_ALT), atLimit, -1);
            fail("expected max-size rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void firesOnlyOnFreshEdgesAndRequiresExactModifierSet() {
        var state = new CommandHotkeys.State();
        var binding = bind("# goto 1 2 3", GLFW.GLFW_KEY_G, CTRL);
        List<CommandHotkeys.Binding> list = List.of(binding);
        assertTrue(state.poll(list, true, false, keys()).isEmpty()); // seed context
        assertEquals(List.of("# goto 1 2 3"), state.poll(list, true, false, keys(GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_LEFT_CONTROL)));
        assertTrue(state.poll(list, true, false, keys(GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_LEFT_CONTROL)).isEmpty());
        assertTrue(state.poll(list, true, false, keys(GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_LEFT_SHIFT)).isEmpty());
        assertTrue(state.poll(list, true, false, keys(GLFW.GLFW_KEY_G)).isEmpty());
        assertEquals(List.of("# goto 1 2 3"), state.poll(list, true, false, keys(GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_RIGHT_CONTROL)));
    }

    @Test public void blocksAnyOpenScreenAndDoesNotFireHeldKeysWhenScreenCloses() {
        var state = new CommandHotkeys.State();
        var binding = bind("mine diamond_ore", GLFW.GLFW_KEY_M, CTRL);
        List<CommandHotkeys.Binding> list = List.of(binding);
        var down = keys(GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_LEFT_CONTROL);
        assertTrue(state.poll(list, true, false, keys()).isEmpty());
        assertTrue(state.poll(list, true, true, down).isEmpty()); // screen opens while chord is down
        assertTrue(state.poll(list, true, true, down).isEmpty());
        assertTrue(state.poll(list, true, false, down).isEmpty()); // screen closes; held chord is seeded
        assertTrue(state.poll(list, true, false, down).isEmpty());
        assertTrue(state.poll(list, true, false, keys()).isEmpty());
        assertEquals(List.of("mine diamond_ore"), state.poll(list, true, false, down));
    }

    @Test public void worldTransitionsAndNoWorldNeverDispatchHeldKeys() {
        var state = new CommandHotkeys.State();
        var binding = bind("stop", GLFW.GLFW_KEY_S, CTRL);
        List<CommandHotkeys.Binding> list = List.of(binding);
        var down = keys(GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_LEFT_CONTROL);
        assertTrue(state.poll(list, false, false, down).isEmpty());
        assertTrue(state.poll(list, true, false, down).isEmpty()); // entering world while held
        assertTrue(state.poll(list, true, false, down).isEmpty());
        assertTrue(state.poll(list, true, false, keys()).isEmpty()); // release must NOT dispatch
        assertEquals(List.of("stop"), state.poll(list, true, false, down));
        assertTrue(state.poll(list, false, false, down).isEmpty()); // leaving world
        assertTrue(state.poll(list, true, false, down).isEmpty()); // re-entering while held
    }

    @Test public void duplicateRuntimeChordsAreSuppressedAndResetIsSafe() {
        var state = new CommandHotkeys.State();
        List<CommandHotkeys.Binding> duplicates = List.of(
                bind("goto 1 2 3", GLFW.GLFW_KEY_G, CTRL), bind("mine diamond_ore", GLFW.GLFW_KEY_G, CTRL));
        var down = keys(GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_LEFT_CONTROL);
        assertTrue(state.poll(duplicates, true, false, down).isEmpty());
        state.reset();
        assertTrue(state.poll(List.of(duplicates.get(0)), true, false, down).isEmpty());
        assertTrue(state.poll(List.of(duplicates.get(0)), true, false, keys()).isEmpty()); // release must NOT dispatch
        assertEquals(List.of("goto 1 2 3"), state.poll(List.of(duplicates.get(0)), true, false, down));
    }

    @Test public void singleKeyDispatchesOnceAndCanBePressedAgain() {
        var state=new CommandHotkeys.State();var list=List.of(bind("#version",GLFW.GLFW_KEY_F8,0));
        assertTrue(state.poll(list,true,false,keys()).isEmpty());
        assertEquals(List.of("#version"),state.poll(list,true,false,keys(GLFW.GLFW_KEY_F8)));
        for(int i=0;i<100;i++)assertTrue(state.poll(list,true,false,keys(GLFW.GLFW_KEY_F8)).isEmpty());
        assertTrue(state.poll(list,true,false,keys()).isEmpty());
        assertEquals(List.of("#version"),state.poll(list,true,false,keys(GLFW.GLFW_KEY_F8)));
    }
    @Test public void addedBindingWhileHeldIsSeededNotExecuted() {
        var state=new CommandHotkeys.State();var list=List.of(bind("help",GLFW.GLFW_KEY_F8,0));
        state.poll(List.of(),true,false,keys());
        assertTrue(state.poll(list,true,false,keys(GLFW.GLFW_KEY_F8)).isEmpty());
        assertTrue(state.poll(list,true,false,keys()).isEmpty());
        assertEquals(List.of("help"),state.poll(list,true,false,keys(GLFW.GLFW_KEY_F8)));
    }
    @Test public void altAndAllThreeModifiersAreSupported() {
        for(int modifiers:new int[]{GLFW.GLFW_MOD_ALT,CTRL|SHIFT|GLFW.GLFW_MOD_ALT}){
            var state=new CommandHotkeys.State();var list=List.of(bind("version",GLFW.GLFW_KEY_V,modifiers));
            state.poll(list,true,false,keys());
            var pressed=modifiers==GLFW.GLFW_MOD_ALT?keys(GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_RIGHT_ALT):keys(GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_RIGHT_ALT,GLFW.GLFW_KEY_RIGHT_CONTROL,GLFW.GLFW_KEY_LEFT_SHIFT);
            assertEquals(List.of("version"),state.poll(list,true,false,pressed));
            assertTrue(state.poll(list,true,false,pressed).isEmpty());
        }
    }
    @Test public void emptyMalformedAndNullListsDoNotDispatch() {
        var state=new CommandHotkeys.State();
        assertTrue(state.poll(null,true,false,keys()).isEmpty());
        assertTrue(state.poll(java.util.Arrays.asList(null,bind("",GLFW.GLFW_KEY_A,0)),true,false,keys(GLFW.GLFW_KEY_A)).isEmpty());
    }
    @Test public void newDuplicateIsRejectedButDifferentModifiersAreAllowed() {
        var first=bind("version",GLFW.GLFW_KEY_V,CTRL);
        CommandHotkeys.validateForSave(bind("help",GLFW.GLFW_KEY_V,CTRL|SHIFT),List.of(first),-1);
        try{CommandHotkeys.validateForSave(bind("help",GLFW.GLFW_KEY_V,CTRL),List.of(first),-1);fail("duplicate accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void normalizesOnlyControlShiftAltFlags() {
        assertEquals(CTRL | SHIFT, CommandHotkeys.normalizeModifiers(CTRL | SHIFT | GLFW.GLFW_MOD_CAPS_LOCK));
        assertEquals(0, CommandHotkeys.normalizeModifiers(GLFW.GLFW_MOD_SUPER));
    }
}
