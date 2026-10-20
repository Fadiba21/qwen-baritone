package baritone.qwenui.core;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.command.ICommand;
import baritone.api.command.manager.ICommandManager;
import baritone.api.utils.SettingsUtil;
import net.fabricmc.loader.api.FabricLoader;
import java.util.*;

/** Adapter over Baritone's public live command registry and settings parser. */
public final class BaritoneBridge {
    public record CommandInfo(String name, List<String> aliases, String description, String usage, String category) {
        public CommandInfo { aliases = List.copyOf(aliases); }
    }
    public record SettingInfo(String name, String value, String type, String description, String category,
                              boolean editable, Boolean booleanValue) { }
    public record Catalog(List<CommandInfo> commands, List<SettingInfo> settings,
                          boolean litematicaModLoaded, boolean litematicaCommandRegistered,
                          String litematicaDescription, List<LitematicaBridge.Feature> litematicaFeatures,
                          List<LitematicaBridge.Placement> litematicaPlacements) {
        public Catalog {
            commands = List.copyOf(commands);
            settings = List.copyOf(settings);
            litematicaFeatures = List.copyOf(litematicaFeatures);
            litematicaPlacements = List.copyOf(litematicaPlacements);
        }
        /** Compatibility overload for callers that already pass feature descriptors. */
        public Catalog(List<CommandInfo> commands, List<SettingInfo> settings,
                       boolean litematicaModLoaded, boolean litematicaCommandRegistered,
                       String litematicaDescription, List<LitematicaBridge.Feature> litematicaFeatures) {
            this(commands, settings, litematicaModLoaded, litematicaCommandRegistered,
                    litematicaDescription, litematicaFeatures, LitematicaBridge.placements());
        }
        /** Backward-compatible constructor retained for existing integrations and tests. */
        public Catalog(List<CommandInfo> commands, List<SettingInfo> settings,
                       boolean litematicaModLoaded, boolean litematicaCommandRegistered,
                       String litematicaDescription) {
            this(commands, settings, litematicaModLoaded, litematicaCommandRegistered,
                    litematicaDescription, LitematicaBridge.features(), LitematicaBridge.placements());
        }
    }

    /** Each current registry entry is included, so commands registered by addons/extensions appear automatically. */
    public List<CommandInfo> allCommands() {
        return allCommands("en_us");
    }
    public List<CommandInfo> allCommands(String language) {
        Map<String, String> descriptions = localizedMap(language, "commands");
        List<CommandInfo> result = new ArrayList<>();
        for (ICommand command : manager().getRegistry().entries) {
            List<String> names = command.getNames();
            if (names == null || names.isEmpty()) continue;
            result.add(new CommandInfo(names.get(0), names.size() < 2 ? List.of() : names.subList(1, names.size()),
                    descriptions.getOrDefault(names.get(0).toLowerCase(Locale.ROOT), safe(command::getShortDesc, "Baritone command")),
                    usage(command.getLongDesc()), commandCategory(names.get(0))));
        }
        result.sort(Comparator.comparing(CommandInfo::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    /** All settings formatted by SettingsUtil (including generic/list/registry values), never Java casts. */
    public List<SettingInfo> allSettings() {
        return allSettings("en_us");
    }
    public List<SettingInfo> allSettings(String language) {
        Map<String, String> descriptions = localizedMap(language, "settings");
        List<SettingInfo> result = new ArrayList<>();
        for (Settings.Setting<?> setting : BaritoneAPI.getSettings().allSettings) {
            String value;
            boolean serializable;
            try { value = SettingsUtil.settingValueToString(setting); serializable = true; }
            catch (RuntimeException unsupported) { value = "<unsupported>"; serializable = false; }
            Boolean bool = setting.getValueClass() == Boolean.class && setting.value instanceof Boolean b ? b : null;
            result.add(new SettingInfo(setting.getName(), value, SettingsUtil.settingTypeToString(setting),
                    setting.isJavaOnly() ? localized(language, "readOnly", "Internal Baritone setting (read-only).")
                            : descriptions.getOrDefault(setting.getName().toLowerCase(Locale.ROOT),
                                    localized(language, "settingFallback", "Baritone setting " + setting.getName() + ".")),
                    settingCategory(setting.getName()), !setting.isJavaOnly() && serializable, bool));
        }
        result.sort(Comparator.comparing(SettingInfo::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    /** Serializable AI/UI catalogue; QwenConfig and API-key fields are deliberately not part of this DTO. */
    public Catalog catalog() {
        return catalog("en_us");
    }
    public Catalog catalog(String language) {
        List<CommandInfo> commands = allCommands(language);
        boolean mod = FabricLoader.getInstance().isModLoaded("litematica");
        boolean cmd = commands.stream().anyMatch(c -> c.name().equalsIgnoreCase("litematica")
                || c.aliases().stream().anyMatch(a -> a.equalsIgnoreCase("litematica")));
        boolean indonesian = language != null && language.startsWith("id");
        String status = mod && cmd
                ? (indonesian ? "Litematica terdeteksi; gunakan placement yang sedang dimuat." : "Litematica detected; use a loaded placement.")
                : (indonesian ? "Integrasi placement Litematica tidak tersedia." : "Litematica placement integration is unavailable.");
        return new Catalog(commands, allSettings(language), mod, cmd, status,
                LitematicaBridge.features(), LitematicaBridge.placements());
    }

    /** Dispatches using the same native manager as Baritone # chat commands. Accepts an optional leading #. */
    public boolean execute(String raw) {
        if (raw == null) throw new IllegalArgumentException("Command tidak boleh null.");
        String text = raw.trim();
        if (text.startsWith("#")) text = text.substring(1).trim();
        return !text.isEmpty() && manager().execute(text);
    }
    /** Native Baritone completion path; result strings retain Baritone's normal formatting. */
    /** Same command implementation and argument parser, but surfaces syntax failures to the macro runner. */
    public boolean executeStrict(String raw) {
        String text = Objects.requireNonNull(raw).trim();
        if (text.startsWith("#")) text = text.substring(1).trim();
        if (text.isEmpty() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return false;
        var expanded = baritone.command.manager.CommandManager.expand(text);
        var command = manager().getCommand(expanded.first());
        if (command == null) return false;
        try { command.execute(expanded.first(), new baritone.command.argument.ArgConsumer(manager(), expanded.second())); return true; }
        catch (baritone.api.command.exception.CommandException invalid) { throw new IllegalArgumentException("Invalid macro command arguments / Argumen perintah makro tidak valid", invalid); }
    }

    public List<String> suggestions(String prefix) {
        String text = prefix == null ? "" : prefix.stripLeading();
        if (text.startsWith("#")) text = text.substring(1).stripLeading();
        return manager().tabComplete(text).distinct().toList();
    }

    static String settingKey(String name) { return Objects.requireNonNull(name).toLowerCase(Locale.ROOT); }

    public boolean setSetting(String name, String value) {
        Objects.requireNonNull(name); Objects.requireNonNull(value);
        Settings settings = BaritoneAPI.getSettings();
        Settings.Setting<?> setting = settings.byLowerName.get(settingKey(name));
        if (setting == null) throw new IllegalArgumentException("Setting tidak ditemukan: " + name);
        if (setting.isJavaOnly()) throw new IllegalArgumentException("Setting read-only: " + name);
        if (setting.getValueClass() == Boolean.class && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Nilai boolean harus true atau false.");
        if (value.isBlank() && (List.class.isAssignableFrom(setting.getValueClass()) || Map.class.isAssignableFrom(setting.getValueClass()))) {
            @SuppressWarnings("unchecked") Settings.Setting<Object> raw = (Settings.Setting<Object>) setting;
            raw.value = List.class.isAssignableFrom(setting.getValueClass()) ? new ArrayList<>() : new HashMap<>();
            return true;
        }
        try { SettingsUtil.parseAndApply(settings, settingKey(setting.getName()), value.strip()); return true; }
        catch (RuntimeException e) {
            String why = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("Nilai tidak valid untuk " + setting.getName() + " (" + SettingsUtil.settingTypeToString(setting) + "): '" + value + "' - " + why, e);
        }
    }
    /** Baritone-serialized default value of a setting, or null when it cannot be serialized. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String defaultValue(String name) {
        Settings.Setting<?> setting = BaritoneAPI.getSettings().byLowerName.get(settingKey(name));
        if (setting == null || setting.isJavaOnly()) return null;
        try { return SettingsUtil.settingValueToString((Settings.Setting) setting, setting.defaultValue); }
        catch (RuntimeException unsupported) { return null; }
    }
    public boolean resetSetting(String name) {
        Objects.requireNonNull(name);
        Settings.Setting<?> setting = BaritoneAPI.getSettings().byLowerName.get(settingKey(name));
        if (setting == null) throw new IllegalArgumentException("Setting tidak ditemukan: " + name);
        if (setting.isJavaOnly()) throw new IllegalArgumentException("Setting read-only: " + name);
        setting.reset(); return true;
    }
    /**
     * Loads a schematic file (.litematic/.schem/.schematic) at the player's feet and starts building it.
     * @return non-fatal warnings (may be empty); throws IllegalStateException with the real reason when loading fails.
     */
    public List<String> buildSchematic(java.io.File file) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone.getPlayerContext().player() == null) throw new IllegalStateException("Enter a world first.");
        var feet = baritone.getPlayerContext().playerFeet();
        var builder = baritone.getBuilderProcess();
        if (!builder.build(file.getName(), file, feet)) {
            String why = builder.getLastLoadError();
            throw new IllegalStateException("Couldn't load " + file.getName() + ": " + (why == null ? "unknown error (see the game log)" : why));
        }
        return builder.getLastLoadWarnings();
    }
    /** Builds a Litematica placement from its file (index < 0 = the selected placement). Returns warnings. */
    public List<String> buildLitematica(int index) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone.getPlayerContext().player() == null) throw new IllegalStateException("Enter a world first.");
        var builder = baritone.getBuilderProcess();
        builder.buildOpenLitematic(index);
        String why = builder.getLastLoadError();
        if (why != null) throw new IllegalStateException(why);
        return builder.getLastLoadWarnings();
    }
    /** Persist settings using Baritone's own settings writer. */
    public void saveSettings() { SettingsUtil.save(BaritoneAPI.getSettings()); }

    private static ICommandManager manager() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) throw new IllegalStateException("Baritone client belum tersedia.");
        return baritone.getCommandManager();
    }
    private static String usage(List<String> desc) {
        if (desc == null) return "";
        List<String> result = new ArrayList<>(); boolean section = false;
        for (String line : desc) {
            if (line == null) continue;
            if (line.toLowerCase(Locale.ROOT).startsWith("usage:")) { section = true; continue; }
            if (section && line.startsWith(">")) result.add(line.substring(1).trim());
            else if (section && !line.isBlank()) break;
        }
        return String.join("\n", result);
    }
    private static String commandCategory(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (has(n,"set","toggle","reset","modified","config")) return "settings";
        if (has(n,"mine","build","litematica","schematica","proc","farm","tunnel")) return "automation";
        if (has(n,"goto","path","thisway","surface","follow","explore","invert")) return "navigation";
        if (has(n,"sel","waypoint","goal")) return "world";
        if (has(n,"help","version","save","reload","repack")) return "utility";
        if (has(n,"render")) return "visual";
        return "other";
    }
    private static String settingCategory(String name) {
        SettingGuide.Meta meta = SettingGuide.meta(name);
        if (meta != null) return meta.area();
        String n = name.toLowerCase(Locale.ROOT);
        if (has(n,"render","color","draw","opacity","shape","line")) return "visual";
        if (has(n,"mine","block","item","place","throwaway","avoid","tool")) return "mining-building";
        if (has(n,"path","goal","cost","favor","diagonal","parkour","sprint","walk","jump","elytra")) return "navigation";
        if (has(n,"chat","prefix","command","notification","log")) return "commands";
        if (has(n,"cache","world","chunk","dimension","save","load")) return "world-cache";
        return "general";
    }
    private static boolean has(String text, String... parts) { for (String part : parts) if (text.contains(part)) return true; return false; }
    private static String safe(java.util.function.Supplier<String> supplier, String fallback) {
        try { String value = supplier.get(); return value == null ? fallback : value; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String localized(String language, String key, String fallback) {
        return localizedMap(language, "_meta").getOrDefault(key, fallback);
    }
    private static Map<String, String> localizedMap(String language, String section) {
        String code = language == null || language.isBlank() ? "en_us" : language.toLowerCase(Locale.ROOT);
        String path = "/assets/qwenui/descriptions." + code + ".json";
        try (java.io.InputStream stream = BaritoneBridge.class.getResourceAsStream(path)) {
            if (stream == null) return Map.of();
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has(section) || !root.get(section).isJsonObject()) return Map.of();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject(section).entrySet())
                if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            return values;
        } catch (Exception ignored) { return Map.of(); }
    }
}
