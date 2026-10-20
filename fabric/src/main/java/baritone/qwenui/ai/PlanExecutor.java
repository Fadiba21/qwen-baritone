package baritone.qwenui.ai;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.qwenui.QwenUI;
import baritone.qwenui.core.BaritoneBridge;
import baritone.qwenui.core.LitematicaBridge;
import baritone.qwenui.core.QwenConfig;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.util.*;

/** Untrusted AI action requests are checked against the live registry before immediate execution. */
public final class PlanExecutor {
    private PlanExecutor() { }
    public static String commandText(GeminiClient.Action action) {
        String name = action.name() == null ? "" : action.name().trim();
        String value = action.value() == null ? "" : action.value().trim();
        return value.isEmpty() ? name : name + " " + value;
    }
    public static void validate(List<GeminiClient.Action> actions, BaritoneBridge.Catalog catalog) {
        if (actions == null || actions.isEmpty() || actions.size() > 100) throw new IllegalArgumentException("Invalid action count / Jumlah tindakan tidak valid");
        Set<String> commands = new HashSet<>();
        for (var command : catalog.commands()) {
            commands.add(command.name().toLowerCase(Locale.ROOT));
            command.aliases().forEach(alias -> commands.add(alias.toLowerCase(Locale.ROOT)));
        }
        Map<String, BaritoneBridge.SettingInfo> settings = new HashMap<>();
        catalog.settings().forEach(setting -> settings.put(setting.name().toLowerCase(Locale.ROOT), setting));
        Map<String, LitematicaBridge.Feature> litematica = new HashMap<>();
        catalog.litematicaFeatures().forEach(feature -> {
            if (feature.available()) litematica.put(feature.id(), feature);
        });
        for (var action : actions) {
            if (action == null || action.type() == null) throw new IllegalArgumentException("Invalid action / Tindakan tidak valid");
            switch (action.type()) {
                case "command" -> validateCommand(commandText(action), commands);
                case "setting" -> {
                    var setting = settings.get(action.name().toLowerCase(Locale.ROOT));
                    if (setting == null || !setting.editable()) throw new IllegalArgumentException("Unknown/read-only setting / Pengaturan tidak tersedia: " + action.name());
                    if (action.value() == null || action.value().length() > 2048 || action.value().indexOf('\n') >= 0 || action.value().indexOf('\r') >= 0)
                        throw new IllegalArgumentException("Invalid setting value / Nilai pengaturan tidak valid");
                }
                case "macro" -> {
                    if (action.name() == null || action.name().isBlank() || action.name().length() > 80 || action.commands().isEmpty() || action.commands().size() > 100)
                        throw new IllegalArgumentException("Invalid macro / Makro tidak valid");
                    for (String command : action.commands()) validateCommand(command, commands);
                }
                case "litematica" -> validateLitematicaAction(action, litematica);
                default -> throw new IllegalArgumentException("Unsupported action / Tindakan tidak didukung");
            }
        }
    }
    static void validateLitematicaAction(GeminiClient.Action action, Map<String, LitematicaBridge.Feature> features) {
        LitematicaBridge.Feature feature = action == null ? null : features.get(action.name());
        if (feature == null || !feature.available())
            throw new IllegalArgumentException("Unknown or unavailable Litematica feature / Fitur Litematica tidak tersedia");
        if (!action.value().isEmpty() || !action.commands().isEmpty())
            throw new IllegalArgumentException("Invalid Litematica action / Tindakan Litematica tidak valid");
    }
    private static void validateCommand(String command, Set<String> registered) {
        if (command == null || command.isBlank() || command.length() > 2048 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf(';') >= 0)
            throw new IllegalArgumentException("Invalid command / Perintah tidak valid");
        String cleaned = command.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1).trim();
        String label = cleaned.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!registered.contains(label)) throw new IllegalArgumentException("Unregistered command / Perintah tidak terdaftar: " + label);
    }
    /** Validate and immediately execute supported AI action requests on the client thread. */
    public static String execute(List<GeminiClient.Action> actions) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) throw new IllegalStateException("Open a world first / Buka dunia terlebih dahulu");
        BaritoneBridge bridge = QwenUI.bridge();
        validate(actions, bridge.catalog());
        List<String> queue = actions.stream().filter(action -> action.type().equals("command")).map(PlanExecutor::commandText).toList();
        if (!queue.isEmpty() && QwenUI.macros().isRunning()) throw new IllegalStateException("Stop the current macro first / Hentikan makro aktif terlebih dahulu");
        int newMacros = (int) actions.stream().filter(action -> action.type().equals("macro")).count();
        if (QwenUI.config().macros.size() + newMacros > 128) throw new IllegalArgumentException("Macro limit reached / Batas makro tercapai");
        Map<Settings.Setting<?>, Object> originals = new LinkedHashMap<>();
        List<QwenConfig.Macro> originalMacros = new ArrayList<>(QwenUI.config().macros);
        try {
            for (var action : actions) {
                if (action.type().equals("setting")) {
                    Settings.Setting<?> setting = BaritoneAPI.getSettings().byLowerName.get(action.name().toLowerCase(Locale.ROOT));
                    originals.putIfAbsent(setting, setting.value);
                    bridge.setSetting(action.name(), action.value());
                } else if (action.type().equals("macro")) {
                    QwenUI.config().macros.add(new QwenConfig.Macro(action.name(), action.commands(), 2, 1));
                }
            }
            QwenUI.config().save();
            if (!originals.isEmpty()) bridge.saveSettings();
        } catch (RuntimeException | IOException failure) {
            originals.forEach(PlanExecutor::restore);
            QwenUI.config().macros.clear();
            QwenUI.config().macros.addAll(originalMacros);
            // The first persistence call may have succeeded before a later one failed. Restore
            // both persisted stores best-effort as well as restoring their in-memory state.
            try { QwenUI.config().save(); }
            catch (RuntimeException | IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            if (!originals.isEmpty()) {
                try { bridge.saveSettings(); }
                catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw new IllegalArgumentException("Action execution failed / Tindakan gagal dijalankan: invalid setting or save failed", failure);
        }
        // Litematica integrations are dispatched only after live-catalog validation above.
        // Recheck availability at execution time because the mod/placement may have changed since the response.
        for (var action : actions) {
            if (!action.type().equals("litematica")) continue;
            try {
                LitematicaBridge.Feature feature = bridge.catalog().litematicaFeatures().stream()
                        .filter(candidate -> candidate.id().equals(action.name()) && candidate.available())
                        .findFirst().orElseThrow(() -> new IllegalStateException("Litematica feature is no longer available / Fitur Litematica tidak lagi tersedia"));
                if (feature.changesWorld() && (client.level == null || client.player == null))
                    throw new IllegalStateException("This Litematica action requires an open world / Tindakan Litematica ini memerlukan dunia terbuka");
                if (!LitematicaBridge.isAvailable(action.name()))
                    throw new IllegalStateException("Litematica feature is no longer available / Fitur Litematica tidak lagi tersedia");
                LitematicaBridge.execute(action.name());
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Litematica action failed. Earlier settings, macros, or Litematica changes may already have executed; inspect the world. No queued commands were started.", failure);
            }
        }
        if (!queue.isEmpty()) QwenUI.macros().start(new QwenConfig.Macro("AI action plan", queue, 2, 1));
        if (queue.isEmpty()) return QwenUI.config().language.startsWith("id")
            ? "Tindakan diterapkan. Periksa hasil dan pengaturan aktif."
            : "Actions applied. Check results and active settings.";
        return QwenUI.config().language.startsWith("id")
            ? "Tindakan dikirim; perintah mulai dijalankan berurutan. Periksa hasil dan log Baritone."
            : "Actions dispatched; commands have started in order. Check results and the Baritone log.";
    }
    /** Compatibility entry point; this also validates and immediately executes without an approval gate. */
    @Deprecated
    public static String apply(List<GeminiClient.Action> actions) { return execute(actions); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void restore(Settings.Setting setting, Object value) { setting.value = value; }
}
