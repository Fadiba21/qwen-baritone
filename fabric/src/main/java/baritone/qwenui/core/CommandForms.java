package baritone.qwenui.core;

import java.util.*;
import java.util.regex.Pattern;

/** Declarative, localized argument forms for Baritone's built-in commands. */
public final class CommandForms {
    private CommandForms() {}

    public record Field(String key, String en, String id, String hint, List<String> choices, boolean required) {
        public Field { Objects.requireNonNull(key); Objects.requireNonNull(en); Objects.requireNonNull(id); Objects.requireNonNull(hint); choices = List.copyOf(choices); }
    }
    public record Variant(String en, String id, List<String> prefix, List<Field> fields) {
        public Variant { Objects.requireNonNull(en); Objects.requireNonNull(id); prefix = List.copyOf(prefix); fields = List.copyOf(fields); }
    }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z0-9_-]+)?");
    private static final Map<String,List<Variant>> FORMS = buildForms();
    private static Field f(String k, String en, String id, String hint, boolean req) { return new Field(k,en,id,hint,List.of(),req); }
    private static Field f(String k, String en, String id, String hint, List<String> choices, boolean req) { return new Field(k,en,id,hint,choices,req); }
    private static Variant v(String en,String id,List<String> pre,Field... fs) { return new Variant(en,id,pre,List.of(fs)); }
    private static Variant v(String en,String id,Field... fs) { return v(en,id,List.of(),fs); }
    private static Field coord(String key, boolean req) { return f(key,key.toUpperCase(Locale.ROOT),key.toUpperCase(Locale.ROOT),"Number or ~-relative coordinate",req); }
    private static Field text(String key, String en, String id, String hint, boolean req) { return f(key,en,id,hint,req); }
    private static Map<String,List<Variant>> buildForms() {
        Map<String,List<Variant>> m = new LinkedHashMap<>();
        put(m,"goto", v("Coordinates (X Y Z)","Koordinat (X Y Z)",coord("x",true),coord("y",true),coord("z",true)), v("Coordinates (X Z)","Koordinat (X Z)",coord("x",true),coord("z",true)), v("Y level","Level Y",coord("y",true)), v("Block","Blok",text("block","Block","Blok","Block name, e.g. minecraft:diamond_ore",true)));
        put(m,"goal",v("Here","Di sini"),v("Clear goal","Hapus tujuan",List.of("clear")),v("Reset goal","Reset tujuan",List.of("reset")),v("No goal","Tanpa tujuan",List.of("none")),v("Coordinates (X Y Z)","Koordinat (X Y Z)",coord("x",true),coord("y",true),coord("z",true)),v("Coordinates (X Z)","Koordinat (X Z)",coord("x",true),coord("z",true)),v("Y level","Level Y",coord("y",true)));
        put(m,"mine",v("Blocks","Blok",text("blocks","Blocks","Blok","One or more block IDs",true)),v("Count + blocks","Jumlah + blok",text("count","Count","Jumlah","Maximum quantity (integer)",true),text("blocks","Blocks","Blok","One or more block IDs",true)));
        put(m,"follow",v("All players","Semua pemain",List.of("players")),v("All entities","Semua entitas",List.of("entities")),v("Players by name","Pemain berdasarkan nama",List.of("player"),text("players","Player names","Nama pemain","One or more player names",true)),v("Entities by type","Entitas berdasarkan tipe",List.of("entity"),text("entities","Entity types","Tipe entitas","One or more entity types",true)));
        put(m,"build",v("Schematic file","File skematik",text("filename","Filename","Nama file","Schematic filename",true)),v("File + position","File + posisi",text("filename","Filename","Nama file","Schematic filename",true),coord("x",false),coord("y",false),coord("z",false)));
        put(m,"litematica",v("Current schematic","Skematik saat ini"),v("Schematic index","Indeks skematik",text("index","Index","Indeks","Litematica schematic index",true)));
        put(m,"farm",v("All nearby crops","Semua tanaman sekitar"),v("Radius","Radius",text("radius","Radius","Radius","Search radius",true)),v("Radius + waypoint","Radius + waypoint",text("radius","Radius","Radius","Search radius",true),text("waypoint","Waypoint","Waypoint","Waypoint tag or name",true)));
        put(m,"tunnel",v("Default (1×2)","Default (1×2)"),v("Custom dimensions","Dimensi kustom",text("height","Height","Tinggi","Tunnel height",true),text("width","Width","Lebar","Tunnel width",true),text("depth","Depth","Kedalaman","Tunnel depth",true)));
        put(m,"explore",v("From current position","Dari posisi saat ini"),v("From X Z","Dari X Z",coord("x",true),coord("z",true)));
        put(m,"explorefilter",v("Apply file","Terapkan file",text("file","JSON file","File JSON","Path to explored-chunks JSON",true)),v("Apply inverted file","Terapkan file terbalik",text("file","JSON file","File JSON","Path to explored-chunks JSON",true),f("invert","Invert","Balik","Literal invert option",List.of("invert"),false)));
        put(m,"elytra",v("Fly to goal","Terbang ke tujuan"),v("Reset","Reset",List.of("reset")),v("Repack","Repack",List.of("repack")),v("Supported?","Didukung?",List.of("supported")));
        put(m,"waypoints",v("List waypoints","Daftar waypoint"),v("List by tag","Daftar berdasarkan tag",List.of("list"),f("tag","Tag","Tag","Optional waypoint tag",List.of("user","bed","death","home"),false)),v("Save current","Simpan posisi saat ini",List.of("save")),v("Save waypoint","Simpan waypoint",List.of("save"),f("tag","Tag","Tag","Optional waypoint tag",List.of("user","bed","death","home"),false),text("name","Name","Nama","Optional waypoint name",false),coord("x",false),coord("y",false),coord("z",false)),v("Info","Info",List.of("info"),text("waypoint","Tag/name","Tag/nama","Waypoint tag or name",true)),v("Delete","Hapus",List.of("delete"),text("waypoint","Tag/name","Tag/nama","Waypoint tag or name",true)),v("Restore count","Pulihkan jumlah",List.of("restore"),text("count","Count","Jumlah","Number of recently deleted waypoints to restore",true)),v("Restore selected","Pulihkan pilihan",List.of("restore"),text("selector","Selector","Pemilih","@ followed by waypoint timestamps",true)),v("Clear tag","Hapus tag",List.of("clear"),text("tag","Tag","Tag","Waypoint tag",true)),v("Set goal","Tetapkan tujuan",List.of("goal"),text("waypoint","Tag/name","Tag/nama","Waypoint tag or name",true)),v("Go to waypoint","Menuju waypoint",List.of("goto"),text("waypoint","Tag/name","Tag/nama","Waypoint tag or name",true)));
        put(m,"sel",v("Position 1 (here)","Posisi 1 (sini)",List.of("pos1")),v("Position 1 (coordinates)","Posisi 1 (koordinat)",List.of("pos1"),coord("x",false),coord("y",false),coord("z",false)),v("Position 2 (here)","Posisi 2 (sini)",List.of("pos2")),v("Position 2 (coordinates)","Posisi 2 (koordinat)",List.of("pos2"),coord("x",false),coord("y",false),coord("z",false)),v("Clear","Hapus",List.of("clear")),v("Undo","Urungkan",List.of("undo")),v("Set/fill","Isi",List.of("set"),text("block","Block","Blok","Block name or block-state specification",true)),v("Walls","Dinding",List.of("walls"),text("block","Block","Blok","Block name or block-state specification",true)),v("Shell","Cangkang",List.of("shell"),text("block","Block","Blok","Block name or block-state specification",true)),v("Sphere","Bola",List.of("sphere"),text("block","Block","Blok","Block name or block-state specification",true)),v("Hollow sphere","Bola berongga",List.of("hsphere"),text("block","Block","Blok","Block name or block-state specification",true)),v("Cylinder","Silinder",List.of("cylinder"),text("block","Block","Blok","Block name or block-state specification",true),f("axis","Axis","Sumbu","Optional axis (defaults to y)",List.of("x","y","z"),false)),v("Hollow cylinder","Silinder berongga",List.of("hcylinder"),text("block","Block","Blok","Block name or block-state specification",true),f("axis","Axis","Sumbu","Optional axis (defaults to y)",List.of("x","y","z"),false)),v("Clear area","Bersihkan area",List.of("cleararea")),v("Replace","Ganti blok",List.of("replace"),text("blocks","Blocks to replace","Blok yang diganti","One or more block IDs",true),text("with","Replacement block","Blok pengganti","Replacement block ID",true)),v("Copy","Salin",List.of("copy")),v("Paste at current position","Tempel di posisi saat ini",List.of("paste")),v("Paste at coordinates","Tempel di koordinat",List.of("paste"),coord("x",false),coord("y",false),coord("z",false)),v("Expand","Perluas",List.of("expand"),f("target","Target","Target","Selection target",List.of("a","all","n","newest","o","oldest"),true),f("direction","Direction","Arah","Direction",List.of("up","down","north","south","east","west"),true),text("blocks","Blocks","Blok","Number of blocks",true)),v("Contract","Kontraksi",List.of("contract"),f("target","Target","Target","Selection target",List.of("a","all","n","newest","o","oldest"),true),f("direction","Direction","Arah","Direction",List.of("up","down","north","south","east","west"),true),text("blocks","Blocks","Blok","Number of blocks",true)),v("Shift","Geser",List.of("shift"),f("target","Target","Target","Selection target",List.of("a","all","n","newest","o","oldest"),true),f("direction","Direction","Arah","Direction",List.of("up","down","north","south","east","west"),true),text("blocks","Blocks","Blok","Number of blocks",true)));
        put(m,"reset",v("Reset all settings","Reset semua pengaturan"),v("Reset one setting","Reset satu pengaturan",text("setting","Setting","Pengaturan","Setting name",true)));
        put(m,"pickup",v("Pick up anything","Ambil apa saja"),v("Pick up selected items","Ambil item tertentu",text("items","Items","Item","One or more item IDs",true)));
        put(m,"find",v("Find blocks","Cari blok",text("blocks","Blocks","Blok","One or more block IDs",true)));
        put(m,"thisway",v("Set distance ahead","Atur jarak ke depan",text("distance","Distance","Jarak","Number of blocks ahead",true)));
        put(m,"set",v("List settings","Daftar pengaturan",List.of("list")),v("List modified settings","Daftar pengaturan berubah",List.of("modified")),v("Show setting","Lihat pengaturan",text("setting","Setting","Pengaturan","Setting name",true)),v("Set setting value","Atur nilai pengaturan",text("setting","Setting","Pengaturan","Setting name",true),text("value","Value","Nilai","Value to set",true)),v("Toggle boolean setting","Alihkan pengaturan boolean",List.of("toggle"),text("setting","Setting","Pengaturan","Boolean setting name",true)),v("Reset all settings","Reset semua pengaturan",List.of("reset","all")),v("Reset one setting","Reset satu pengaturan",List.of("reset"),text("setting","Setting","Pengaturan","Setting name",true)),v("Save settings","Simpan pengaturan",List.of("save")),v("Load settings","Muat pengaturan",List.of("load")),v("Load named settings file","Muat file pengaturan",List.of("load"),text("filename","Filename","Nama file","Optional settings filename",true)));
        // Remaining registered commands have no arguments in their usage; expose a no-argument form.
        for (String n : List.of("help","set","modified","mod","baritone","modifiedsettings","reset","path","proc","eta","version","repack","come","axis","cancel","forcecancel","gc","invert","render","pickup","reloadall","saveall","blacklist","find","click","surface","thisway","pause","resume","paused","sethome","home")) {
            if (!m.containsKey(n)) put(m,n,v("Run command","Jalankan perintah"));
        }
        // The registry exposes aliases alongside these canonical names.
        for (String alias : List.of("setting","settings")) m.put(alias, m.get("set"));
        for (String alias : List.of("selection","s")) m.put(alias, m.get("sel"));
        for (String alias : List.of("waypoint","wp")) m.put(alias, m.get("waypoints"));
        for (String alias : List.of("g")) m.put(alias, m.get("goto"));
        for (String alias : List.of("stop","c")) m.put(alias, m.get("cancel"));
        for (String alias : List.of("r","unpause","unpaws")) m.put(alias, m.get("resume"));
        for (String alias : List.of("rescan")) m.put(alias, m.get("repack"));
        for (String alias : List.of("top")) m.put(alias, m.get("surface"));
        for (String alias : List.of("forward")) m.put(alias, m.get("thisway"));
        for (String alias : List.of("highway")) m.put(alias, m.get("axis"));
        for (String alias : List.of("modified","mod","baritone","modifiedsettings")) m.put(alias, m.get("modified"));
        return Collections.unmodifiableMap(m);
    }
    private static void put(Map<String,List<Variant>> map,String name,Variant... variants) { map.put(name,List.of(variants)); }

    /** Returns predefined variants; unrecognized (including addon) commands get one generic parameter form. */
    public static List<Variant> forCommand(String name, String usage) {
        if (name == null || !TOKEN.matcher(name).matches()) return List.of();
        List<Variant> known = FORMS.get(name.toLowerCase(Locale.ROOT));
        if (known != null) return known;
        String hint = usage == null || usage.isBlank() ? "Command parameters" : usage.trim();
        return List.of(v("Parameters","Parameter",text("arguments","Parameters","Parameter",hint,false)));
    }

    private static boolean sameForm(Variant a, Variant b) {
        if (!a.prefix().equals(b.prefix()) || a.fields().size() != b.fields().size()) return false;
        for (int i = 0; i < a.fields().size(); i++) {
            Field x = a.fields().get(i), y = b.fields().get(i);
            if (!x.key().equals(y.key()) || x.required() != y.required() || !x.choices().equals(y.choices())) return false;
        }
        return true;
    }

    /** Compose a command line from a registered command token and one of its forms. */
    public static String compose(String command, Variant variant, Map<String,String> values) {
        if (command == null || !TOKEN.matcher(command).matches()) throw new IllegalArgumentException("Invalid command token");
        Objects.requireNonNull(variant,"variant"); Objects.requireNonNull(values,"values");
        List<Variant> allowed = forCommand(command, null);
        if (allowed.stream().noneMatch(v -> sameForm(v, variant))) throw new IllegalArgumentException("Variant does not belong to command");
        List<String> tokens = new ArrayList<>(); tokens.add(command); tokens.addAll(variant.prefix());
        Map<String,String> cleaned = new LinkedHashMap<>();
        for (Field field : variant.fields()) {
            String raw = values.get(field.key());
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty() && field.required()) throw new IllegalArgumentException("Missing required field: " + field.key());
            if (!value.isEmpty()) {
                if (value.matches(".*[\\r\\n\\t;#].*")) throw new IllegalArgumentException("Unsafe value for " + field.key());
                cleaned.put(field.key(), value.replaceAll(" +", " "));
            }
        }
        // Coordinates are positional; never serialize a later coordinate across a missing earlier one.
        boolean gap = false;
        for (String key : List.of("x","y","z")) {
            if (variant.fields().stream().noneMatch(f -> f.key().equals(key))) continue;
            boolean present = cleaned.containsKey(key);
            if (!present) gap = true;
            else if (gap) throw new IllegalArgumentException("Missing coordinate before " + key);
        }
        boolean missingOptional = false;
        for (Field field : variant.fields()) {
            boolean present = cleaned.containsKey(field.key());
            // Mine's count is an optional leading numeric argument; all other form fields are positional.
            if (field.key().equals("count") && command.equalsIgnoreCase("mine")) continue;
            if (!present) missingOptional = true;
            else if (missingOptional) throw new IllegalArgumentException("Missing optional field before " + field.key());
        }
        for (Field field : variant.fields()) {
            String value = cleaned.get(field.key());
            if (value != null && !value.isEmpty()) tokens.add(value);
        }
        return String.join(" ", tokens);
    }
}
