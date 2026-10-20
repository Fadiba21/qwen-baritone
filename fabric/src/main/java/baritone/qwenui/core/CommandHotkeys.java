package baritone.qwenui.core;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Model and input state for custom Baritone command shortcuts. Independent of Minecraft client state. */
public final class CommandHotkeys {
    public static final int MODIFIERS = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT;
    public static final int MAX_COMMAND_LENGTH = 2048;
    public static final int MAX_BINDINGS = 128;

    private CommandHotkeys() { }

    /** Mutable public DTO: UI/config editors may update command, key, and modifiers directly. */
    public static final class Binding {
        public String command;
        public int key;
        public int modifiers;

        public Binding(String command, int key, int modifiers) {
            this.command = command;
            this.key = key;
            this.modifiers = modifiers;
        }

        /** Empty constructor is useful to JSON/UI tooling; call {@link #validationError()} before use. */
        public Binding() { this("", GLFW.GLFW_KEY_UNKNOWN, 0); }

        public boolean isValid() { return validationError() == null; }

        /** Returns a short validation error, or null if this binding is safe to execute. */
        public String validationError() {
            if (command == null) return "Command is required";
            if (command.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) return "Command must be a single line without control characters";
            String text = command.trim();
            if (text.isEmpty()) return "Command is required";
            if (text.length() > MAX_COMMAND_LENGTH) return "Command is too long";
            String nativeCommand = text.startsWith("#") ? text.substring(1).trim() : text;
            if (nativeCommand.isEmpty()) return "Command is required";
            if (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST || isModifierKey(key))
                return "Key is not a bindable GLFW keyboard key";
            if ((modifiers & ~MODIFIERS) != 0)
                return "Use Ctrl, Shift, or Alt modifiers only";
            if (key == GLFW.GLFW_KEY_F11)
                return "F11 is reserved for opening QwenUI";
            if (key == GLFW.GLFW_KEY_END)
                return "End is reserved for the emergency stop";
            return null;
        }

        public String chordDisplay() {
            List<String> parts = new ArrayList<>(4);
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) parts.add("Ctrl");
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) parts.add("Shift");
            if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) parts.add("Alt");
            parts.add(keyName(key));
            return String.join("+", parts);
        }

        public String display() {
            return chordDisplay() + " → " + (command == null ? "" : command.trim());
        }

        public boolean sameChord(Binding other) {
            return other != null && key == other.key && modifiers == other.modifiers;
        }
    }

    /** A callback over physical GLFW key state; intended for tests and the client window adapter. */
    @FunctionalInterface
    public interface KeyState {
        boolean isDown(int glfwKey);
    }

    /** Rejects invalid entries and duplicate chords; callers can use this before committing UI edits. */
    public static void validateBindings(List<Binding> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("At most " + MAX_BINDINGS + " hotkeys are allowed");
        Set<Chord> seen = new HashSet<>();
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            if (binding == null) throw new IllegalArgumentException("Hotkey " + (i + 1) + " is missing");
            String problem = binding.validationError();
            if (problem != null) throw new IllegalArgumentException("Hotkey " + (i + 1) + ": " + problem);
            if (!seen.add(new Chord(binding.key, binding.modifiers)))
                throw new IllegalArgumentException("Duplicate hotkey chord: " + binding.chordDisplay());
        }
    }

    /** Validate one proposed edit against the rest of a mutable UI list; ignoreIndex skips its prior value. */
    public static void validateForSave(Binding binding, List<Binding> bindings, int ignoreIndex) {
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("At most " + MAX_BINDINGS + " hotkeys are allowed");
        if (ignoreIndex < -1 || ignoreIndex >= bindings.size()) throw new IllegalArgumentException("Invalid hotkey edit index");
        if (binding == null) throw new IllegalArgumentException("Hotkey is missing");
        String problem = binding.validationError();
        if (problem != null) throw new IllegalArgumentException(problem);
        Chord proposed = new Chord(binding.key, binding.modifiers);
        Set<Chord> seen = new HashSet<>();
        for (int i = 0; i < bindings.size(); i++) {
            if (i == ignoreIndex) continue;
            Binding existing = bindings.get(i);
            if (existing == null) throw new IllegalArgumentException("Hotkey " + (i + 1) + " is missing");
            problem = existing.validationError();
            if (problem != null) throw new IllegalArgumentException("Hotkey " + (i + 1) + ": " + problem);
            Chord chord = new Chord(existing.key, existing.modifiers);
            if (!seen.add(chord)) throw new IllegalArgumentException("Duplicate hotkey chord: " + existing.chordDisplay());
        }
        if (seen.contains(proposed)) throw new IllegalArgumentException("Duplicate hotkey chord: " + binding.chordDisplay());
        if (ignoreIndex == -1 && bindings.size() == MAX_BINDINGS)
            throw new IllegalArgumentException("At most " + MAX_BINDINGS + " hotkeys are allowed");
    }

    /** Normalize any GLFW modifier flags to the only three supported modifier bits. */
    public static int normalizeModifiers(int modifiers) { return modifiers & MODIFIERS; }

    /** Poll once per client tick. Returned commands are dispatched only on fresh chord edges. */
    public static final class State {
        private final Map<Chord, Boolean> wasDown = new HashMap<>();
        private boolean initialized;
        private boolean previousWorld;
        private boolean previousScreen;

        public List<String> poll(List<Binding> bindings, boolean inWorld, boolean screenOpen, KeyState keys) {
            Objects.requireNonNull(keys, "keys");
            Map<Chord, Binding> candidates = uniqueValidBindings(bindings);
            Map<Chord, Boolean> now = new LinkedHashMap<>();
            for (Map.Entry<Chord, Binding> entry : candidates.entrySet()) {
                Binding binding = entry.getValue();
                now.put(entry.getKey(), isChordDown(binding, keys));
            }

            boolean changedContext = !initialized || previousWorld != inWorld || previousScreen != screenOpen;
            boolean blocked = !inWorld || screenOpen;
            List<String> fired = new ArrayList<>();
            if (!blocked && !changedContext) {
                for (Map.Entry<Chord, Binding> entry : candidates.entrySet()) {
                    boolean down = now.getOrDefault(entry.getKey(), false);
                    // A binding added while its key is held is seeded, rather than spuriously fired.
                    boolean before = wasDown.getOrDefault(entry.getKey(), down);
                    if (down && !before) fired.add(entry.getValue().command.trim());
                }
            }
            wasDown.clear();
            wasDown.putAll(now);
            initialized = true;
            previousWorld = inWorld;
            previousScreen = screenOpen;
            return List.copyOf(fired);
        }

        /** Clear all latches, e.g. when the Minecraft window loses its input context. */
        public void reset() {
            wasDown.clear();
            initialized = false;
        }

        private static boolean isChordDown(Binding binding, KeyState keys) {
            if (binding.validationError() != null || !keys.isDown(binding.key)) return false;
            int down = 0;
            if (keys.isDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keys.isDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) down |= GLFW.GLFW_MOD_CONTROL;
            if (keys.isDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keys.isDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) down |= GLFW.GLFW_MOD_SHIFT;
            if (keys.isDown(GLFW.GLFW_KEY_LEFT_ALT) || keys.isDown(GLFW.GLFW_KEY_RIGHT_ALT)) down |= GLFW.GLFW_MOD_ALT;
            return normalizeModifiers(down) == binding.modifiers;
        }

        private static Map<Chord, Binding> uniqueValidBindings(List<Binding> bindings) {
            Map<Chord, Binding> unique = new LinkedHashMap<>();
            Set<Chord> duplicates = new HashSet<>();
            if (bindings == null) return unique;
            for (Binding binding : bindings) {
                if (binding == null || binding.validationError() != null) continue;
                Chord chord = new Chord(binding.key, binding.modifiers);
                if (unique.containsKey(chord)) duplicates.add(chord);
                else unique.put(chord, binding);
            }
            duplicates.forEach(unique::remove);
            return unique;
        }
    }

    private record Chord(int key, int modifiers) { }

    private static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static String keyName(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return Character.toString((char) ('A' + key - GLFW.GLFW_KEY_A));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return Character.toString((char) ('0' + key - GLFW.GLFW_KEY_0));
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End (reserved)";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_KP_0, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3,
                    GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6, GLFW.GLFW_KEY_KP_7,
                    GLFW.GLFW_KEY_KP_8, GLFW.GLFW_KEY_KP_9 -> "Numpad " + (key - GLFW.GLFW_KEY_KP_0);
            default -> "Key " + key;
        };
    }
}
