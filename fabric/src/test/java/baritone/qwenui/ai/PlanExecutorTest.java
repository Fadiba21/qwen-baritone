package baritone.qwenui.ai;

import baritone.qwenui.core.BaritoneBridge;
import baritone.qwenui.core.LitematicaBridge;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class PlanExecutorTest {
    private static final BaritoneBridge.Catalog CATALOG = new BaritoneBridge.Catalog(
        List.of(new BaritoneBridge.CommandInfo("goto", List.of("g"), "Navigate", "goto x y z", "navigation"),
                new BaritoneBridge.CommandInfo("stop", List.of(), "Stop", "stop", "navigation")),
        List.of(new BaritoneBridge.SettingInfo("allowBreak", "true", "Boolean", "Breaking", "general", true, true),
                new BaritoneBridge.SettingInfo("logger", "internal", "Consumer", "Internal", "general", false, null)),
        false, true, "Unavailable");
    private static GeminiClient.Action action(String type, String name, String value) { return new GeminiClient.Action(type, name, value, List.of()); }
    private static void reject(GeminiClient.Action action) {
        try { PlanExecutor.validate(List.of(action), CATALOG); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void acceptsRegisteredCommandAndArguments() { PlanExecutor.validate(List.of(action("command", "goto", "1 64 2")), CATALOG); }
    @Test public void acceptsAliasAndPrefix() { PlanExecutor.validate(List.of(action("command", "#g", "1 64 2")), CATALOG); }
    @Test public void joinsCommandArgumentsWithoutDroppingValue() { assertEquals("goto 1 64 2", PlanExecutor.commandText(action("command", "goto", "1 64 2"))); }
    @Test public void rejectsUnknownCommand() { reject(action("command", "exec", "curl example.com")); }
    @Test public void rejectsMinecraftSlashCommand() { reject(action("command", "/op", "player")); }
    @Test public void rejectsNewlineInjection() { reject(action("command", "goto", "1 2 3\nstop")); }
    @Test public void rejectsSemicolonInjection() { reject(action("command", "goto", "1 2 3; stop")); }
    @Test public void acceptsKnownEditableSetting() { PlanExecutor.validate(List.of(action("setting", "allowBreak", "false")), CATALOG); }
    @Test public void rejectsReadOnlySetting() { reject(action("setting", "logger", "x")); }
    @Test public void rejectsUnknownSetting() { reject(action("setting", "apiKey", "secret")); }
    @Test public void rejectsMultilineSetting() { reject(action("setting", "allowBreak", "false\ntrue")); }
    @Test public void rejectsUnsupportedActionType() { reject(action("shell", "goto", "")); }
    @Test public void rejectsEmptyMacro() { reject(action("macro", "empty", "")); }
    @Test public void acceptsRegisteredMacroCommands() { PlanExecutor.validate(List.of(new GeminiClient.Action("macro", "test", "", List.of("goto 1 64 2", "stop"))), CATALOG); }
    @Test public void rejectsUnknownCommandInsideMacro() { reject(new GeminiClient.Action("macro", "test", "", List.of("goto 1 64 2", "shell rm"))); }
    @Test public void acceptsOnlyAvailableCatalogLitematicaFeature() {
        var features = Map.of("placement.load", new LitematicaBridge.Feature("placement.load", "Load", "Load placement", true, true));
        PlanExecutor.validateLitematicaAction(action("litematica", "placement.load", ""), features);
    }
    @Test public void validatesLitematicaAgainstExtendedLiveCatalog() {
        var feature = new LitematicaBridge.Feature("placement.load", "Load", "Load placement", true, true);
        var catalog = new BaritoneBridge.Catalog(CATALOG.commands(), CATALOG.settings(), true, false,
                "Litematica test catalog", List.of(feature));
        PlanExecutor.validate(List.of(action("litematica", "placement.load", "")), catalog);
        try { PlanExecutor.validate(List.of(action("litematica", "not-listed", "")), catalog); fail("Expected feature id rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void rejectsUnavailableAndUnlistedLitematicaFeatures() {
        var features = Map.of("placement.load", new LitematicaBridge.Feature("placement.load", "Load", "Load placement", false, true));
        for (GeminiClient.Action proposal : List.of(action("litematica", "placement.load", ""), action("litematica", "arbitrary.id", ""))) {
            try { PlanExecutor.validateLitematicaAction(proposal, features); fail("Expected unavailable feature rejection"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void rejectsLitematicaActionWithUntrustedPayloadFields() {
        var features = Map.of("placement.load", new LitematicaBridge.Feature("placement.load", "Load", "Load placement", true, true));
        try { PlanExecutor.validateLitematicaAction(new GeminiClient.Action("litematica", "placement.load", "extra", List.of()), features); fail("Expected malformed action rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void rejectsEmptyPlan() {
        try { PlanExecutor.validate(List.of(), CATALOG); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
