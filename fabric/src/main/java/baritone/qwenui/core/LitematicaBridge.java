package baritone.qwenui.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Optional adapter for the supported, non-destructive Litematica controls.
 * This class intentionally has no compile-time dependency on Litematica or MaLiLib.
 * Calls that inspect or operate on live client state must be made on Minecraft's client thread.
 */
public final class LitematicaBridge {
    private static final String LITEMATICA = "fi.dy.masa.litematica.";
    private static final String[] CONFIG_GROUPS = {
            LITEMATICA + "config.Configs$Colors",
            LITEMATICA + "config.Configs$Generic",
            LITEMATICA + "config.Configs$InfoOverlays",
            LITEMATICA + "config.Configs$Visuals"
    };
    private static final List<FeatureSpec> FIXED_FEATURES = List.of(
            new FeatureSpec("gui.config", "Litematica configuration", "Open Litematica's native configuration screen.",
                    LITEMATICA + "gui.GuiConfigs", new String[0], false),
            new FeatureSpec("gui.schematic.load", "Load schematic", "Open Litematica's native schematic browser to review and load a schematic.",
                    LITEMATICA + "gui.GuiSchematicLoad", new String[0], false),
            new FeatureSpec("gui.schematic.manager", "Schematic manager", "Open Litematica's native schematic manager.",
                    LITEMATICA + "gui.GuiSchematicManager", new String[0], false),
            new FeatureSpec("gui.placements", "Loaded placements", "Open Litematica's native list of loaded schematic placements.",
                    LITEMATICA + "gui.GuiSchematicPlacementsList", new String[0], false),
            new FeatureSpec("gui.area.selection", "Area selections", "Open Litematica's native area-selection manager.",
                    LITEMATICA + "gui.GuiAreaSelectionManager", new String[0], false),
            new FeatureSpec("gui.material.list", "Material list", "Open the material list for the selected placement, when Litematica has one.",
                    LITEMATICA + "gui.GuiMaterialList", new String[] { "fi.dy.masa.litematica.materials.MaterialListBase" }, false),
            new FeatureSpec("gui.verifier", "Schematic verifier", "Open Litematica's native verifier for the selected placement; this only inspects the world.",
                    LITEMATICA + "gui.GuiSchematicVerifier", new String[] { LITEMATICA + "schematic.placement.SchematicPlacement" }, false),
            new FeatureSpec("gui.placement.config", "Selected placement settings", "Open the native settings screen for the selected placement.",
                    LITEMATICA + "gui.GuiPlacementConfiguration", new String[] { LITEMATICA + "schematic.placement.SchematicPlacement" }, false)
    );

    private LitematicaBridge() { }

    public record Feature(String id, String label, String description, boolean available, boolean changesWorld) { }
    public record Placement(int index, String name, String detail) { }

    /**
     * Returns the fixed allowlisted GUI controls and each discovered boolean option from the four
     * installed Litematica config groups. Boolean feature IDs are config.toggle.<lower-case-name>.
     */
    public static List<Feature> features() {
        if (!isLitematicaPresent()) return List.copyOf(fixedFeatures(false));
        Object client = clientOnMainThread();
        List<Feature> result = new ArrayList<>(fixedFeatures(true));
        for (FeatureSpec spec : FIXED_FEATURES) {
            if (spec.id.equals("gui.material.list")) {
                Object placement = selectedPlacement(client);
                Object list = placement == null ? null : materialListFor(placement);
                if (list == null) list = staticCall(LITEMATICA + "data.DataManager", "getMaterialList");
                setAvailable(result, spec.id, list != null);
            } else if (spec.id.equals("gui.verifier") || spec.id.equals("gui.placement.config")) {
                setAvailable(result, spec.id, selectedPlacement(client) != null);
            }
        }
        for (Object option : booleanOptions()) {
            String name = optionName(option);
            boolean value = booleanValue(option);
            String id = configId(name);
            String note = optionDescription(option);
            if (note.isBlank()) note = "Live boolean in Litematica's installed config groups.";
            result.add(new Feature(id, humanize(name), note + " Current value: " + value + ". Toggle flips it immediately; config changes can affect in-world behavior. AI requests execute directly after validation.", true, true));
        }
        List<Placement> placementCatalog = placementDetails(placementObjects(client));
        boolean canSelectPlacement = placementSelectionSupported(client);
        for (Placement placement : placementCatalog) {
            result.add(new Feature("placement.select." + placement.index(), "Select " + placement.name(),
                    "Select this loaded placement and open its native settings. " + placement.detail(), canSelectPlacement, false));
        }
        result.sort(Comparator.comparingInt((Feature f)->f.id().startsWith("gui.")?0:f.id().startsWith("placement.")?1:2).thenComparing(Feature::label));
        return List.copyOf(result);
    }

    /** Returns the loaded placement catalog in native list order; placement indexes are 1-based. */
    public static List<Placement> placements() {
        if (!isLitematicaPresent()) return List.of();
        Object client = clientOnMainThread();
        return placementDetails(placementObjects(client));
    }

    private static List<Placement> placementDetails(List<Object> objects) {
        List<Placement> result = new ArrayList<>(objects.size());
        for (int i = 0; i < objects.size(); i++) {
            Object placement = objects.get(i);
            String name = stringCall(placement, "getName", "Placement " + (i + 1));
            StringBuilder detail = new StringBuilder("#").append(i + 1).append(" ").append(name);
            Object origin = call(placement, "getOrigin");
            if (origin != null) detail.append(" · origin ").append(origin);
            Object rotation = call(placement, "getRotation");
            if (rotation != null) detail.append(" · rotation ").append(rotation);
            Object mirror = call(placement, "getMirror");
            if (mirror != null) detail.append(" · mirror ").append(mirror);
            Object file = call(placement, "getSchematicFile");
            if (file instanceof Path path) detail.append(" · file ").append(path.getFileName());
            else if (file != null) detail.append(" · file ").append(file);
            Object enabled = call(placement, "isEnabled");
            if (enabled instanceof Boolean flag) detail.append(" · ").append(flag ? "enabled" : "disabled");
            result.add(new Placement(i + 1, name, detail.toString()));
        }
        return List.copyOf(result);
    }

    /** Dispatches only fixed GUI actions, placement.select.<1-based-index>, or discovered boolean toggles. */
    public static void execute(String id) {
        Objects.requireNonNull(id, "feature id");
        String action = id.trim();
        if (action.equals("placement.select")) throw new IllegalArgumentException("Select a placement using its emitted placement.select.<index> feature ID.");
        if (!isLitematicaPresent()) throw new IllegalStateException("Litematica is not loaded.");
        Object client = clientOnMainThread();

        if (action.startsWith("config.toggle.")) {
            toggleConfig(action.substring("config.toggle.".length()));
            return;
        }
        if (action.startsWith("placement.select.")) {
            if (!action.matches("placement\\.select\\.[1-9][0-9]*")) throw new IllegalArgumentException("Invalid placement feature ID.");
            int index;
            try { index = Integer.parseInt(action.substring("placement.select.".length())); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid placement index.", invalid); }
            selectPlacement(client, index);
            return;
        }
        FeatureSpec spec = FIXED_FEATURES.stream().filter(candidate -> candidate.id.equals(action)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Litematica feature: " + action));
        Object placement = null;
        Object materialList = null;
        if (action.equals("gui.material.list")) {
            placement = selectedPlacement(client);
            materialList = placement == null ? null : materialListFor(placement);
            if (materialList == null) materialList = staticCall(LITEMATICA + "data.DataManager", "getMaterialList");
            if (materialList == null) throw new IllegalStateException("No Litematica material list is available; select a placement first.");
        } else if (action.equals("gui.verifier") || action.equals("gui.placement.config")) {
            placement = selectedPlacement(client);
            if (placement == null) throw new IllegalStateException("No Litematica placement is selected.");
        }
        openGui(client, construct(spec.guiClass, spec.constructorTypes, action.equals("gui.material.list")
                ? new Object[] { materialList }
                : (action.equals("gui.verifier") || action.equals("gui.placement.config")) ? new Object[] { placement } : new Object[0]));
    }

    /** True only for an ID emitted by the feature catalog, or a valid indexed placement action. */
    public static boolean isAvailable(String id) {
        if (id == null || !isLitematicaPresent()) return false;
        Object client = clientOnMainThread();
        String action = id.trim();
        if (action.startsWith("config.toggle.")) {
            if (!action.matches("config\\.toggle\\.[a-z0-9_]+")) return false;
            Object option = findBooleanOption(action.substring("config.toggle.".length()));
            return option != null;
        }
        if (action.startsWith("placement.select.")) {
            if (!action.matches("placement\\.select\\.[1-9][0-9]*")) return false;
            try {
                int index = Integer.parseInt(action.substring("placement.select.".length()));
                return index > 0 && index <= placementObjects(client).size() && placementSelectionSupported(client);
            } catch (NumberFormatException ignored) { return false; }
        }
        for (Feature feature : features()) if (feature.id().equals(action)) return feature.available();
        return false;
    }

    private static List<Feature> fixedFeatures(boolean loaded) {
        List<Feature> result = new ArrayList<>(FIXED_FEATURES.size());
        for (FeatureSpec spec : FIXED_FEATURES) {
            boolean available = loaded && classAndConstructorAvailable(spec);
            if (spec.id.equals("gui.verifier") || spec.id.equals("gui.placement.config")
                    || spec.id.equals("gui.material.list")) available = false;
            result.add(new Feature(spec.id, spec.label, spec.description, available, spec.changesWorld));
        }
        return result;
    }

    private static boolean classAndConstructorAvailable(FeatureSpec spec) {
        try {
            Class<?> gui = loadClass(spec.guiClass);
            Class<?>[] parameterTypes = new Class<?>[spec.constructorTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) parameterTypes[i] = loadClass(spec.constructorTypes[i]);
            gui.getConstructor(parameterTypes);
            loadClass("fi.dy.masa.malilib.gui.GuiBase").getMethod("openGui", loadClass("net.minecraft.client.gui.screens.Screen"));
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }

    private static void setAvailable(List<Feature> features, String id, boolean available) {
        for (int i = 0; i < features.size(); i++) {
            Feature feature = features.get(i);
            if (feature.id.equals(id)) features.set(i, new Feature(feature.id, feature.label, feature.description, available, feature.changesWorld));
        }
    }

    private static void openGui(Object client, Object screen) {
        if (screen == null) throw new IllegalStateException("Litematica screen could not be created.");
        Object gui = fieldValue(client, "gui");
        Object parent = call(gui, "screen");
        if (parent != null) {
            for (Method method : screen.getClass().getMethods()) {
                if (method.getName().equals("setParent") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(parent)) {
                    invoke(method, screen, parent);
                    break;
                }
            }
        }
        try {
            Class<?> guiBase = loadClass("fi.dy.masa.malilib.gui.GuiBase");
            Method open = matchingMethod(guiBase, "openGui", 1);
            invoke(open, null, screen);
        } catch (ReflectiveOperationException failure) {
            throw operationFailure("Litematica GUI API is unavailable", failure);
        }
    }

    private static Object construct(String className, String[] parameterNames, Object[] args) {
        try {
            Class<?> type = loadClass(className);
            Class<?>[] parameters = new Class<?>[parameterNames.length];
            for (int i = 0; i < parameters.length; i++) parameters[i] = loadClass(parameterNames[i]);
            Constructor<?> constructor = type.getConstructor(parameters);
            return constructor.newInstance(args);
        } catch (InvocationTargetException failure) {
            throw operationFailure("Could not open Litematica screen", failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw operationFailure("Could not open Litematica screen", failure);
        }
    }

    private static void selectPlacement(Object client, int oneBasedIndex) {
        List<Object> placements = placementObjects(client);
        if (oneBasedIndex < 1 || oneBasedIndex > placements.size()) throw new IllegalArgumentException("Placement index is out of range.");
        if (!placementSelectionSupported(client)) throw new IllegalStateException("Native placement selection API is unavailable.");
        Object placement = placements.get(oneBasedIndex - 1);
        Object manager = placementManager(client);
        Method setter = matchingMethod(manager.getClass(), "setSelectedSchematicPlacement", 1);
        invoke(setter, manager, placement);
        openGui(client, construct(LITEMATICA + "gui.GuiPlacementConfiguration",
                new String[] { LITEMATICA + "schematic.placement.SchematicPlacement" }, new Object[] { placement }));
    }

    private static boolean placementSelectionSupported(Object client) {
        try {
            Object manager = placementManager(client);
            matchingMethod(manager.getClass(), "setSelectedSchematicPlacement", 1);
            return classAndConstructorAvailable(new FeatureSpec("placement.select", "Select loaded placement", "",
                    LITEMATICA + "gui.GuiPlacementConfiguration",
                    new String[] { LITEMATICA + "schematic.placement.SchematicPlacement" }, false));
        } catch (RuntimeException unsupported) { return false; }
    }

    private static void toggleConfig(String suffix) {
        if (!suffix.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Unsupported Litematica config toggle.");
        Object option = findBooleanOption(suffix);
        if (option == null) throw new IllegalArgumentException("Unknown Litematica boolean config: " + suffix);
        boolean oldValue = booleanValue(option);
        Method setter = matchingMethod(option.getClass(), "setBooleanValue", 1);
        if (setter.getParameterTypes()[0] != boolean.class && setter.getParameterTypes()[0] != Boolean.class)
            throw new IllegalStateException("Litematica boolean setter signature is not supported.");
        invoke(setter, option, !oldValue);
        staticCall(LITEMATICA + "config.Configs", "saveToFile");
    }

    private static List<Object> booleanOptions() {
        List<Object> result = new ArrayList<>();
        for (String groupName : CONFIG_GROUPS) {
            try {
                Class<?> group = loadClass(groupName);
                Field optionsField = group.getField("OPTIONS");
                Object options = optionsField.get(null);
                if (!(options instanceof Iterable<?> iterable)) continue;
                for (Object option : iterable) {
                    if (isBooleanOption(option)) result.add(option);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Optional config groups vary across Litematica builds; unsupported groups are omitted.
            }
        }
        result.sort(Comparator.comparing(LitematicaBridge::optionName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static Object findBooleanOption(String suffix) {
        for (Object option : booleanOptions()) if (configId(optionName(option)).equals("config.toggle." + suffix)) return option;
        return null;
    }

    private static boolean isBooleanOption(Object option) {
        if (option == null) return false;
        try {
            Class<?> booleanInterface = loadClass("fi.dy.masa.malilib.config.IConfigBoolean");
            return booleanInterface.isInstance(option)
                    && option.getClass().getMethod("getBooleanValue").getReturnType() == boolean.class
                    && option.getClass().getMethod("setBooleanValue", boolean.class) != null
                    && option.getClass().getMethod("getName").getReturnType() == String.class;
        } catch (ReflectiveOperationException | LinkageError unsupported) { return false; }
    }

    private static String optionName(Object option) { return stringCall(option, "getName", ""); }
    private static String optionDescription(Object option) { return stringCall(option, "getComment", ""); }
    private static boolean booleanValue(Object option) {
        Object result = call(option, "getBooleanValue");
        if (!(result instanceof Boolean value)) throw new IllegalStateException("Litematica config option is not a supported boolean.");
        return value;
    }
    private static String configId(String name) { return "config.toggle." + name.toLowerCase(Locale.ROOT); }
    private static String humanize(String name) {
        String words=name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_',' ').trim();
        return words.isEmpty()?name:Character.toUpperCase(words.charAt(0))+words.substring(1);
    }

    private static Object selectedPlacement(Object client) { return call(placementManager(client), "getSelectedSchematicPlacement"); }
    private static Object materialListFor(Object placement) { return call(placement, "getMaterialList"); }
    private static Object placementManager(Object client) {
        Object data = staticCall(LITEMATICA + "data.DataManager", "getInstance");
        Object manager = call(data, "getSchematicPlacementManager");
        if (manager == null) throw new IllegalStateException("Litematica placement manager is unavailable.");
        return manager;
    }
    private static List<Object> placementObjects(Object client) {
        Object raw = call(placementManager(client), "getAllSchematicsPlacements");
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        List<Object> result = new ArrayList<>();
        for (Object value : iterable) if (value != null) result.add(value);
        return result;
    }

    private static boolean isLitematicaPresent() {
        try { loadClass(LITEMATICA + "Litematica"); return true; }
        catch (ReflectiveOperationException | LinkageError absent) { return false; }
    }

    private static Object clientOnMainThread() {
        try {
            Class<?> minecraft = loadClass("net.minecraft.client.Minecraft");
            Object client = minecraft.getMethod("getInstance").invoke(null);
            if (client == null) throw new IllegalStateException("Minecraft client is unavailable.");
            Method sameThread = matchingMethod(client.getClass(), "isSameThread", 0);
            if (!Boolean.TRUE.equals(invoke(sameThread, client)))
                throw new IllegalStateException("LitematicaBridge must be called on the Minecraft client thread.");
            return client;
        } catch (InvocationTargetException failure) {
            throw operationFailure("Minecraft client is unavailable", failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw operationFailure("Minecraft client is unavailable", failure);
        }
    }

    private static Object staticCall(String className, String methodName, Object... args) {
        try {
            Class<?> type = loadClass(className);
            Method method = matchingMethod(type, methodName, args.length);
            return invoke(method, null, args);
        } catch (ReflectiveOperationException failure) {
            throw operationFailure("Litematica API is unavailable", failure);
        } catch (LinkageError failure) { throw operationFailure("Litematica API is unavailable", failure); }
    }
    private static Object call(Object target, String methodName, Object... args) {
        if (target == null) return null;
        Method method = matchingMethod(target.getClass(), methodName, args.length);
        return invoke(method, target, args);
    }
    private static String stringCall(Object target, String methodName, String fallback) {
        Object value = call(target, methodName);
        return value == null ? fallback : String.valueOf(value);
    }
    private static Object fieldValue(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException failure) { throw operationFailure("Minecraft GUI state is unavailable", failure); }
    }
    private static Method matchingMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        throw new IllegalStateException("Supported Litematica/Minecraft API method not found: " + type.getName() + "." + name);
    }
    private static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw operationFailure("Litematica operation failed", failure.getCause()); }
        catch (ReflectiveOperationException | IllegalArgumentException failure) { throw operationFailure("Litematica operation failed", failure); }
    }
    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, LitematicaBridge.class.getClassLoader());
    }
    private static IllegalStateException operationFailure(String message, Throwable cause) {
        if (cause instanceof IllegalStateException state) return state;
        return new IllegalStateException(message + (cause == null || cause.getMessage() == null ? "." : ": " + cause.getMessage()), cause);
    }

    private record FeatureSpec(String id, String label, String description, String guiClass, String[] constructorTypes, boolean changesWorld) { }
}
