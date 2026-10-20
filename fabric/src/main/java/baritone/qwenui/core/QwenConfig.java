package baritone.qwenui.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** User preferences stored in config/qwenui.json. API keys are omitted unless explicitly remembered. */
public final class QwenConfig {
    public static final String DEFAULT_LANGUAGE = "id_id";
    public static final int MIN_DIMENSION = 240;
    public static final int MAX_DIMENSION = 4096;
    private static final int MAX_MACROS = 128;
    private static final int MAX_COMMANDS_PER_MACRO = 256;

    public String language = DEFAULT_LANGUAGE;
    /** Always "minecraft"; kept for old config files. */
    public String fontFamily = "minecraft";
    public int uiWidth = 900;
    public int uiHeight = 560;
    public int uiScale = 1;
    /** Legacy preferred dimensions are ignored by automatic layout unless explicitly enabled. */
    public boolean customWindowSize = false;
    public boolean animation = true;
    public boolean blurBackground = true;
    /** True once the one-time move of vanilla Fullscreen from F11 to F10 was attempted. */
    public boolean fullscreenKeyMoved = false;
    /** UI scale in whole pixels per unit; 0 = automatic. */
    public int uiScalePx = 0;
    public boolean rememberApiKey = false;
    /** This value is never included in toString, catalogues, diagnostics, or serialized JSON unless opted in. */
    public String apiKey = "";
    public String model = "";
    public List<Macro> macros = new ArrayList<>();
    /** Custom Baritone command bindings; intentionally public for UI editors. */
    public List<CommandHotkeys.Binding> commandHotkeys = new ArrayList<>();

    public static final class Macro {
        public String name;
        public List<String> commands;
        public int delayTicks;
        public int repeatCount;

        public Macro(String name, List<String> commands, int delayTicks, int repeatCount) {
            this.name = cleanName(name);
            this.commands = sanitizeCommands(commands);
            this.delayTicks = clamp(delayTicks, 0, 72_000);
            this.repeatCount = clamp(repeatCount, 1, 100);
        }

        /** For JSON libraries. */
        public Macro() { this("", List.of(), 0, 1); }

        private static String cleanName(String value) {
            if (value == null) return "";
            return value.trim().substring(0, Math.min(value.trim().length(), 80));
        }
        private static List<String> sanitizeCommands(List<String> values) {
            List<String> safe = new ArrayList<>();
            if (values != null) for (String command : values) {
                if (safe.size() == MAX_COMMANDS_PER_MACRO) break;
                if (command == null) continue;
                String trimmed = command.trim();
                if (!trimmed.isEmpty() && trimmed.length() <= 2048) safe.add(trimmed);
            }
            return safe;
        }
        public String name() { return name; }
        public List<String> commands() { return List.copyOf(commands); }
        public int delayTicks() { return delayTicks; }
        public int repeatCount() { return repeatCount; }
    }

    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("qwenui.json"); }

    public static QwenConfig load() { return load(configFile()); }

    static QwenConfig load(Path file) {
        QwenConfig config = new QwenConfig();
        if (!Files.exists(file)) return config;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw new IllegalArgumentException("Root is not an object");
            JsonObject object = root.getAsJsonObject();
            config.language = string(object, "language", DEFAULT_LANGUAGE);
            if (config.language.isBlank() || config.language.length() > 32) config.language = DEFAULT_LANGUAGE;
            config.fontFamily = baritone.qwenui.ui.UiPreferences.normalizeFont(string(object, "fontFamily", "minecraft"));
            config.uiWidth = clamp(integer(object, "uiWidth", 900), MIN_DIMENSION, MAX_DIMENSION);
            config.uiHeight = clamp(integer(object, "uiHeight", 560), MIN_DIMENSION, MAX_DIMENSION);
            config.uiScale = clamp(integer(object, "uiScale", 1), 1, 3);
            config.customWindowSize = bool(object, "customWindowSize", false);
            config.animation = bool(object, "animation", true);
            config.blurBackground = bool(object, "blurBackground", true);
            config.fullscreenKeyMoved = bool(object, "fullscreenKeyMoved", false);
            config.uiScalePx = baritone.qwenui.ui.UiPreferences.clampScalePx(integer(object,"uiScalePx",0));
            config.rememberApiKey = bool(object, "rememberApiKey", false);
            config.apiKey = config.rememberApiKey ? string(object, "apiKey", "") : "";
            config.model = string(object, "model", "");
            if (config.model.length() > 256) config.model = config.model.substring(0, 256);
            config.macros = readMacros(object.get("macros"));
            config.commandHotkeys = readCommandHotkeys(object.get("commandHotkeys"));
        } catch (Exception corrupt) {
            // Preserve the original for manual recovery, but never copy its content to logs.
            try {
                Path backup = file.resolveSibling("qwenui.json.corrupt-" + Instant.now().toEpochMilli());
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Safe defaults still allow startup if the backup cannot be moved.
            }
            config = new QwenConfig();
        }
        return config;
    }

    /** Atomically replaces the config. The key is blanked from disk unless rememberApiKey is true. */
    public synchronized void save() throws IOException { save(configFile()); }

    synchronized void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("language", language == null || language.isBlank() ? DEFAULT_LANGUAGE : language);
        root.addProperty("fontFamily", baritone.qwenui.ui.UiPreferences.normalizeFont(fontFamily));
        root.addProperty("uiWidth", clamp(uiWidth, MIN_DIMENSION, MAX_DIMENSION));
        root.addProperty("uiHeight", clamp(uiHeight, MIN_DIMENSION, MAX_DIMENSION));
        root.addProperty("uiScale", clamp(uiScale, 1, 3));
        root.addProperty("customWindowSize", customWindowSize);
        root.addProperty("animation", animation);
        root.addProperty("blurBackground", blurBackground);
        root.addProperty("fullscreenKeyMoved", fullscreenKeyMoved);
        root.addProperty("uiScalePx", baritone.qwenui.ui.UiPreferences.clampScalePx(uiScalePx));
        root.addProperty("rememberApiKey", rememberApiKey);
        root.addProperty("apiKey", rememberApiKey && apiKey != null ? apiKey : "");
        root.addProperty("model", model == null ? "" : model.substring(0, Math.min(model.length(), 256)));
        JsonArray macroArray = new JsonArray();
        if (macros != null) {
            for (int i = 0; i < Math.min(macros.size(), MAX_MACROS); i++) {
                Macro macro = macros.get(i);
                if (macro == null) continue;
                JsonObject item = new JsonObject();
                item.addProperty("name", Macro.cleanName(macro.name));
                JsonArray commands = new JsonArray();
                Macro.sanitizeCommands(macro.commands).forEach(commands::add);
                item.add("commands", commands);
                item.addProperty("delayTicks", clamp(macro.delayTicks, 0, 72_000));
                item.addProperty("repeatCount", clamp(macro.repeatCount, 1, 100));
                macroArray.add(item);
            }
        }
        root.add("macros", macroArray);
        root.add("commandHotkeys", writeCommandHotkeys(commandHotkeys));
        Path temp = Files.createTempFile(file.getParent(), "qwenui-", ".tmp");
        try {
            Files.writeString(temp, root.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Safe diagnostic representation; intentionally excludes apiKey. */
    @Override public String toString() {
        return "QwenConfig{language='" + language + "', uiWidth=" + uiWidth + ", uiHeight=" + uiHeight
                + ", customWindowSize=" + customWindowSize + ", animation=" + animation + ", rememberApiKey=" + rememberApiKey
                + ", apiKey=<redacted>, model='" + model + "', macros=" + (macros == null ? 0 : macros.size()) + "}";
    }

    private static List<Macro> readMacros(JsonElement value) {
        List<Macro> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) return result;
        for (JsonElement e : value.getAsJsonArray()) {
            if (result.size() >= MAX_MACROS) break;
            try {
                JsonObject m = e.getAsJsonObject();
                List<String> commands = new ArrayList<>();
                JsonElement commandArray = m.get("commands");
                if (commandArray != null && commandArray.isJsonArray()) {
                    for (JsonElement c : commandArray.getAsJsonArray()) if (c.isJsonPrimitive()) commands.add(c.getAsString());
                }
                result.add(new Macro(string(m, "name", ""), commands,
                        integer(m, "delayTicks", 0), integer(m, "repeatCount", 1)));
            } catch (RuntimeException ignored) { /* Skip malformed macro entries individually. */ }
        }
        return result;
    }

    private static List<CommandHotkeys.Binding> readCommandHotkeys(JsonElement value) {
        List<CommandHotkeys.Binding> result = new ArrayList<>();
        if (value == null || !value.isJsonArray()) return result;
        for (JsonElement element : value.getAsJsonArray()) {
            if (result.size() >= CommandHotkeys.MAX_BINDINGS) break;
            try {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String command = string(item, "command", null);
                int key = strictInteger(item, "key");
                int modifiers = strictInteger(item, "modifiers");
                CommandHotkeys.Binding binding = new CommandHotkeys.Binding(command, key, modifiers);
                if (binding.validationError() != null) continue;
                boolean duplicate = result.stream().anyMatch(existing -> existing.sameChord(binding));
                if (!duplicate) result.add(binding);
            } catch (RuntimeException ignored) { /* Ignore malformed bindings independently. */ }
        }
        return result;
    }

    private static JsonArray writeCommandHotkeys(List<CommandHotkeys.Binding> bindings) {
        JsonArray array = new JsonArray();
        if (bindings == null) return array;
        List<CommandHotkeys.Binding> valid = new ArrayList<>();
        for (CommandHotkeys.Binding binding : bindings) {
            if (valid.size() >= CommandHotkeys.MAX_BINDINGS) break;
            if (binding == null || binding.validationError() != null) continue;
            if (valid.stream().anyMatch(existing -> existing.sameChord(binding))) continue;
            JsonObject item = new JsonObject();
            item.addProperty("command", binding.command.trim());
            item.addProperty("key", binding.key);
            item.addProperty("modifiers", binding.modifiers);
            array.add(item);
            valid.add(binding);
        }
        return array;
    }
    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }
    private static int integer(JsonObject o, String key, int fallback) {
        try { return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int strictInteger(JsonObject o, String key) {
        JsonElement element = o.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing numeric " + key);
        try { return new BigDecimal(element.getAsString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException invalid) { throw new IllegalArgumentException("Invalid integer " + key, invalid); }
    }
    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try { return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
