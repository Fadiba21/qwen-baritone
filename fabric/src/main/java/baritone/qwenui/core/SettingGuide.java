package baritone.qwenui.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure helper that turns a Baritone setting (name, type string, default) into a human guide:
 * which area it belongs to, what to type, examples and one-click presets. No Minecraft dependency.
 */
public final class SettingGuide {
    public enum Kind { BOOL, INT, DECIMAL, TEXT, BLOCK, ITEM, BLOCK_LIST, ITEM_LIST, TEXT_LIST, MAP, COLOR, VEC3I, ENUM, OTHER }

    /** level: 0 basic, 1 advanced, 2 experimental / risky. */
    public record Meta(String name, String area, int level, Double min, Double max, Double step, String labelId, String descId) { }
    public record Preset(String label, String value) { }
    public record Guide(String name, Kind kind, String area, String areaEn, String areaId, int level,
                        boolean hasRange, double min, double max, double step,
                        String labelEn, String labelId, String descId,
                        List<Preset> presets, List<String> examples, boolean appendMode) { }

    private static final Map<String, String[]> AREAS = new LinkedHashMap<>();
    static {
        AREAS.put("move", new String[] { "Movement", "Pergerakan" });
        AREAS.put("break", new String[] { "Breaking blocks", "Hancurkan blok" });
        AREAS.put("place", new String[] { "Placing blocks", "Pasang blok" });
        AREAS.put("inv", new String[] { "Inventory & items", "Inventori & item" });
        AREAS.put("build", new String[] { "Building / schematics", "Bangun / skematik" });
        AREAS.put("mine", new String[] { "Mining", "Menambang" });
        AREAS.put("path", new String[] { "Pathfinding", "Pencarian jalur" });
        AREAS.put("perf", new String[] { "Performance & cache", "Performa & cache" });
        AREAS.put("safe", new String[] { "Safety", "Keamanan" });
        AREAS.put("look", new String[] { "Aiming & camera", "Bidikan & kamera" });
        AREAS.put("render", new String[] { "Render / visuals", "Tampilan / render" });
        AREAS.put("color", new String[] { "Colors", "Warna" });
        AREAS.put("chat", new String[] { "Chat & commands", "Chat & perintah" });
        AREAS.put("elytra", new String[] { "Elytra", "Elytra" });
        AREAS.put("follow", new String[] { "Follow / explore", "Ikuti / jelajah" });
        AREAS.put("other", new String[] { "Other", "Lainnya" });
    }

    private static volatile Map<String, Meta> metaCache;

    private SettingGuide() { }

    public static List<String> areaKeys() { return List.copyOf(AREAS.keySet()); }
    public static String areaLabel(String key, boolean indonesian) {
        String[] v = AREAS.getOrDefault(key, AREAS.get("other"));
        return indonesian ? v[1] : v[0];
    }

    public static Map<String, Meta> metas() {
        Map<String, Meta> cached = metaCache;
        if (cached != null) return cached;
        Map<String, Meta> result = new LinkedHashMap<>();
        try (InputStream in = SettingGuide.class.getResourceAsStream("/assets/qwenui/settings_guide.txt")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    Meta meta = parseLine(line);
                    if (meta != null) result.put(meta.name().toLowerCase(Locale.ROOT), meta);
                }
            }
        } catch (java.io.IOException ignored) {
            // The guide is optional; the generic type-based guide still works.
        }
        metaCache = Map.copyOf(result);
        return metaCache;
    }

    static Meta parseLine(String raw) {
        String line = raw.strip();
        if (line.isEmpty() || line.startsWith("#")) return null;
        String[] p = line.split("\\|", -1);
        if (p.length < 8 || p[0].isBlank()) return null;
        int level = 0;
        try { level = Integer.parseInt(p[2].strip()); } catch (NumberFormatException ignored) { }
        return new Meta(p[0].strip(), p[1].strip(), Math.max(0, Math.min(2, level)),
                num(p[3]), num(p[4]), num(p[5]), p[6].strip(), p[7].strip());
    }

    private static Double num(String s) {
        try { return s.isBlank() ? null : Double.valueOf(s.strip()); } catch (NumberFormatException e) { return null; }
    }

    public static Meta meta(String name) {
        return name == null ? null : metas().get(name.toLowerCase(Locale.ROOT));
    }

    public static Kind kindOf(String type) {
        String t = type == null ? "" : type.replace(" ", "");
        return switch (t) {
            case "Boolean", "boolean" -> Kind.BOOL;
            case "Integer", "int", "Long", "long" -> Kind.INT;
            case "Double", "double", "Float", "float" -> Kind.DECIMAL;
            case "String" -> Kind.TEXT;
            case "Block" -> Kind.BLOCK;
            case "Item" -> Kind.ITEM;
            case "List<Block>" -> Kind.BLOCK_LIST;
            case "List<Item>" -> Kind.ITEM_LIST;
            case "List<String>" -> Kind.TEXT_LIST;
            case "Color" -> Kind.COLOR;
            case "Vec3i" -> Kind.VEC3I;
            case "Mirror", "Rotation" -> Kind.ENUM;
            default -> t.startsWith("Map<") ? Kind.MAP : Kind.OTHER;
        };
    }

    private static final String[] COMMON_BLOCKS = { "cobblestone", "dirt", "netherrack", "stone", "sand", "gravel", "magma_block", "cactus", "obsidian" };
    private static final String[] COMMON_ITEMS = { "cobblestone", "dirt", "netherrack", "stone", "gravel", "andesite", "diorite", "granite" };

    /** Builds the guide. {@code defaultValue} and {@code currentValue} are Baritone-serialized strings (may be null). */
    public static Guide of(String name, String type, String defaultValue, String currentValue) {
        Kind kind = kindOf(type);
        Meta meta = meta(name);
        String area = meta == null ? "other" : meta.area();
        String[] areaNames = AREAS.getOrDefault(area, AREAS.get("other"));
        List<Preset> presets = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        boolean append = false;
        boolean hasRange = false;
        double min = 0, max = 0, step = 1;
        switch (kind) {
            case BOOL -> {
                presets.add(new Preset("ON", "true"));
                presets.add(new Preset("OFF", "false"));
                examples.add("true");
                examples.add("false");
            }
            case INT, DECIMAL -> {
                boolean integer = kind == Kind.INT;
                Double def = parse(defaultValue);
                if (meta != null && meta.min() != null && meta.max() != null) {
                    hasRange = true;
                    min = meta.min();
                    max = meta.max();
                }
                if (meta != null && meta.step() != null && meta.step() > 0) step = meta.step();
                else step = autoStep(def, integer);
                if (integer) step = Math.max(1, Math.round(step));
                if (hasRange) {
                    presets.add(new Preset("min " + formatNumber(min, integer), formatNumber(min, integer)));
                }
                if (def != null) presets.add(new Preset("default " + formatNumber(def, integer), formatNumber(def, integer)));
                if (hasRange) {
                    double mid = integer ? Math.round((min + max) / 2) : (min + max) / 2;
                    presets.add(new Preset("max " + formatNumber(max, integer), formatNumber(max, integer)));
                    examples.add(formatNumber(mid, integer));
                }
                if (def != null) examples.add(formatNumber(def, integer));
                if (examples.isEmpty()) examples.add(integer ? "10" : "1.5");
            }
            case ENUM -> {
                boolean mirror = type != null && type.contains("Mirror");
                for (String v : mirror ? new String[] { "NONE", "LEFT_RIGHT", "FRONT_BACK" }
                        : new String[] { "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90" }) {
                    presets.add(new Preset(v, v));
                    examples.add(v);
                }
            }
            case COLOR -> {
                String[][] swatches = { { "Red", "255,0,0" }, { "Orange", "255,165,0" }, { "Yellow", "255,255,0" }, { "Green", "0,255,0" },
                        { "Cyan", "0,255,255" }, { "Blue", "0,0,255" }, { "Magenta", "255,0,255" }, { "White", "255,255,255" } };
                for (String[] s : swatches) presets.add(new Preset(s[0], s[1]));
                examples.add("255,0,0");
                examples.add("0,128,255");
            }
            case VEC3I -> {
                presets.add(new Preset("0,0,0", "0,0,0"));
                presets.add(new Preset("1,0,0", "1,0,0"));
                presets.add(new Preset("0,1,0", "0,1,0"));
                examples.add("0,64,0");
            }
            case BLOCK, ITEM -> {
                for (String v : kind == Kind.BLOCK ? COMMON_BLOCKS : COMMON_ITEMS) presets.add(new Preset(v, v));
                examples.add("stone");
                examples.add("minecraft:cobblestone");
            }
            case BLOCK_LIST, ITEM_LIST -> {
                append = true;
                for (String v : kind == Kind.BLOCK_LIST ? COMMON_BLOCKS : COMMON_ITEMS) presets.add(new Preset("+ " + v, v));
                presets.add(new Preset("clear", ""));
                examples.add("cobblestone,dirt,netherrack");
                examples.add("minecraft:stone,minecraft:sand");
            }
            case TEXT_LIST -> {
                append = true;
                examples.add("waterlogged,lit,powered");
            }
            case MAP -> {
                examples.add("stone->cobblestone,dirt");
                examples.add("grass_block->dirt,coarse_dirt");
                presets.add(new Preset("clear", ""));
            }
            case TEXT -> {
                String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                if (lower.equals("prefix")) { for (String v : new String[] { "#", "!", ">", ".", "$" }) { presets.add(new Preset(v, v)); examples.add(v); } }
                else if (lower.equals("schematicfallbackextension")) { for (String v : new String[] { "schematic", "schem", "litematic" }) { presets.add(new Preset(v, v)); examples.add(v); } }
                else examples.add("text");
            }
            default -> examples.add(defaultValue == null ? "" : defaultValue);
        }
        if (defaultValue != null && kind != Kind.INT && kind != Kind.DECIMAL && kind != Kind.BOOL
                && presets.stream().noneMatch(p -> p.value().equals(defaultValue)) && kind != Kind.BLOCK_LIST
                && kind != Kind.ITEM_LIST && kind != Kind.TEXT_LIST && kind != Kind.MAP && !defaultValue.isEmpty() && defaultValue.length() <= 24) {
            presets.add(0, new Preset("default", defaultValue));
        }
        return new Guide(name, kind, area, areaNames[0], areaNames[1], meta == null ? 1 : meta.level(),
                hasRange, min, max, step, humanize(name), meta == null ? humanize(name) : meta.labelId(),
                meta == null ? "" : meta.descId(), List.copyOf(presets), List.copyOf(examples), append);
    }

    /** One short instruction line: what the player has to type for this kind of value. */
    public static String howTo(Guide g, boolean indonesian) {
        return switch (g.kind()) {
            case BOOL -> indonesian ? "Pilih ON (aktif) atau OFF (mati)." : "Choose ON (enabled) or OFF (disabled).";
            case INT -> (indonesian ? "Angka bulat, contoh " : "Whole number, e.g. ") + g.examples().get(g.examples().size() - 1)
                    + (g.hasRange() ? rangeText(g, indonesian) : "");
            case DECIMAL -> (indonesian ? "Angka desimal pakai titik, contoh " : "Decimal number with a dot, e.g. ") + g.examples().get(g.examples().size() - 1)
                    + (g.hasRange() ? rangeText(g, indonesian) : "");
            case ENUM -> indonesian ? "Pilih salah satu nilai dari tombol (huruf besar persis)." : "Pick one of the values from the buttons (exact capitals).";
            case COLOR -> indonesian ? "Tiga angka 0-255 dipisah koma: merah,hijau,biru. Contoh 255,0,0 = merah." : "Three numbers 0-255 separated by commas: red,green,blue. Example 255,0,0 = red.";
            case VEC3I -> indonesian ? "Tiga angka bulat dipisah koma: x,y,z. Contoh 0,64,0." : "Three whole numbers separated by commas: x,y,z. Example 0,64,0.";
            case BLOCK -> indonesian ? "Satu nama blok, boleh tanpa 'minecraft:'. Contoh stone." : "One block name, 'minecraft:' optional. Example: stone.";
            case ITEM -> indonesian ? "Satu nama item, boleh tanpa 'minecraft:'. Contoh cobblestone." : "One item name, 'minecraft:' optional. Example: cobblestone.";
            case BLOCK_LIST -> indonesian ? "Daftar blok dipisah koma tanpa spasi. Klik '+' untuk menambah, 'clear' untuk mengosongkan." : "Comma-separated block names, no spaces. Click '+' to add, 'clear' to empty.";
            case ITEM_LIST -> indonesian ? "Daftar item dipisah koma tanpa spasi. Klik '+' untuk menambah, 'clear' untuk mengosongkan." : "Comma-separated item names, no spaces. Click '+' to add, 'clear' to empty.";
            case TEXT_LIST -> indonesian ? "Daftar teks dipisah koma tanpa spasi." : "Comma-separated words, no spaces.";
            case MAP -> indonesian ? "Format blok->pengganti1,pengganti2 ; pisahkan beberapa aturan dengan koma. Contoh stone->cobblestone,dirt" : "Format block->substitute1,substitute2 ; separate rules with commas. Example: stone->cobblestone,dirt";
            case TEXT -> indonesian ? "Teks bebas. Pakai tombol contoh bila ada." : "Free text. Use the example buttons when present.";
            default -> indonesian ? "Ikuti format bawaan Baritone (lihat nilai saat ini)." : "Follow Baritone's native format (see the current value).";
        };
    }

    private static String rangeText(Guide g, boolean indonesian) {
        boolean integer = g.kind() == Kind.INT;
        return (indonesian ? ". Rentang disarankan " : ". Suggested range ") + formatNumber(g.min(), integer) + " - " + formatNumber(g.max(), integer) + ".";
    }

    public static String levelLabel(int level, boolean indonesian) {
        return switch (level) {
            case 0 -> indonesian ? "Dasar" : "Basic";
            case 2 -> indonesian ? "Berisiko / eksperimental" : "Risky / experimental";
            default -> indonesian ? "Lanjutan" : "Advanced";
        };
    }

    /** The equivalent chat command, using an example value. */
    public static String chatExample(Guide g) {
        String value = g.examples().isEmpty() ? "<value>" : g.examples().get(0);
        if (g.kind() == Kind.BOOL) value = "true";
        return "#set " + g.name() + " " + (value.isEmpty() ? "<value>" : value);
    }

    /** Applies a numeric step / scale to a serialized number; returns null if not a number. */
    public static String adjust(Guide g, String current, double delta, double factor) {
        Double v = parse(current);
        if (v == null) return null;
        boolean integer = g.kind() == Kind.INT;
        double next = factor != 1 ? v * factor : v + delta;
        if (g.hasRange() && factor == 1) next = Math.max(g.min(), Math.min(g.max(), next));
        if (integer) next = Math.round(next);
        else next = Math.round(next * 1_000_000d) / 1_000_000d;
        return formatNumber(next, integer);
    }

    /** Appends or toggles an element of a comma-separated list value. */
    public static String listAdd(String current, String element) {
        if (element.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        if (current != null && !current.isBlank()) for (String s : current.split(",")) if (!s.isBlank()) parts.add(s.strip());
        if (!parts.contains(element)) parts.add(element);
        return String.join(",", parts);
    }

    public static boolean isValidNumber(Kind kind, String text) {
        if (text == null) return false;
        String s = text.strip();
        try {
            if (kind == Kind.INT) { Long.parseLong(s); return true; }
            if (kind == Kind.DECIMAL) { double v = Double.parseDouble(s); return Double.isFinite(v); }
        } catch (NumberFormatException e) { return false; }
        return true;
    }

    private static double autoStep(Double def, boolean integer) {
        if (def == null || def == 0) return integer ? 1 : 0.1;
        double mag = Math.pow(10, Math.floor(Math.log10(Math.abs(def))));
        return integer ? Math.max(1, mag / 10) : Math.max(0.01, mag / 10);
    }

    private static Double parse(String s) {
        try { return s == null ? null : Double.valueOf(s.strip()); } catch (NumberFormatException e) { return null; }
    }

    public static String formatNumber(double v, boolean integer) {
        if (integer) return Long.toString(Math.round(v));
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return (long) v + ".0";
        String s = Double.toString(v);
        return s.contains("E") ? new java.math.BigDecimal(v).stripTrailingZeros().toPlainString() : s;
    }

    public static String humanizeName(String name) { return humanize(name); }
    static String humanize(String name) {
        if (name == null || name.isEmpty()) return "";
        String words = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
