/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.litematica;

import baritone.api.schematic.IStaticSchematic;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.StaticSchematic;
import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads Litematica placements through reflection (method names differ between Litematica builds) and turns the
 * selected / loaded placement into a Baritone schematic straight from its .litematic file. Unlike reading the
 * rendered schematic world, this works wherever the player stands and needs no chunks to be loaded.
 */
public final class LitematicaPlacements {

    public record Sub(String name, Vec3i pos, Rotation rotation, Mirror mirror) { }

    public record Info(int index, String name, Path file, Vec3i origin, Rotation rotation, Mirror mirror,
                       boolean enabled, boolean selected, List<Sub> subs) { }

    /** A schematic ready to build, plus its minimum corner in the world and non-fatal notes for the player. */
    public record Loaded(IStaticSchematic schematic, Vec3i origin, List<String> notes) { }

    private LitematicaPlacements() { }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, LitematicaPlacements.class.getClassLoader());
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        if (target == null) return null;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(target, args);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException re) throw re;
                    throw e;
                }
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Object callAny(Object target, String... names) throws ReflectiveOperationException {
        NoSuchMethodException last = null;
        for (String n : names) {
            try {
                return call(target, n);
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        throw last == null ? new NoSuchMethodException() : last;
    }

    private static Vec3i vec(Object blockPos) throws ReflectiveOperationException {
        return new Vec3i((int) call(blockPos, "getX"), (int) call(blockPos, "getY"), (int) call(blockPos, "getZ"));
    }

    public static boolean isPresent() {
        try {
            load("fi.dy.masa.litematica.Litematica");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Every loaded placement in Litematica's list order (index is 0-based). */
    public static List<Info> list() throws ReflectiveOperationException {
        Object data = load("fi.dy.masa.litematica.data.DataManager").getMethod("getSchematicPlacementManager").invoke(null);
        Object all = callAny(data, "getAllSchematicsPlacements", "getAllSchematicPlacements");
        Object selected = null;
        try {
            selected = call(data, "getSelectedSchematicPlacement");
        } catch (NoSuchMethodException ignored) {
            // older/newer builds: no selection concept exposed
        }
        List<Info> out = new ArrayList<>();
        int i = 0;
        for (Object p : (Iterable<?>) all) {
            if (p == null) continue;
            Object file = null;
            try {
                file = call(p, "getSchematicFile");
            } catch (NoSuchMethodException ignored) {
                // in-memory placement or API difference
            }
            List<Sub> subs = new ArrayList<>();
            try {
                Object map = call(p, "getEnabledRelativeSubRegionPlacements");
                for (Map.Entry<?, ?> e : ((Map<?, ?>) map).entrySet()) {
                    Object sp = e.getValue();
                    subs.add(new Sub(String.valueOf(e.getKey()), vec(call(sp, "getPos")),
                            Rotation.valueOf(((Enum<?>) call(sp, "getRotation")).name()),
                            Mirror.valueOf(((Enum<?>) call(sp, "getMirror")).name())));
                }
            } catch (NoSuchMethodException ignored) {
                subs = null;
            }
            boolean enabled = true;
            try {
                enabled = Boolean.TRUE.equals(call(p, "isEnabled"));
            } catch (NoSuchMethodException ignored) {
                // assume enabled
            }
            out.add(new Info(i++, String.valueOf(call(p, "getName")), file instanceof Path path ? path : null,
                    vec(call(p, "getOrigin")),
                    Rotation.valueOf(((Enum<?>) call(p, "getRotation")).name()),
                    Mirror.valueOf(((Enum<?>) call(p, "getMirror")).name()),
                    enabled, p == selected, subs));
        }
        return out;
    }

    /** Index to build when the player did not choose one: the selected placement, else the first enabled one. */
    public static int pickDefault(List<Info> infos) {
        for (Info info : infos) if (info.selected()) return info.index();
        for (Info info : infos) if (info.enabled()) return info.index();
        return infos.isEmpty() ? -1 : 0;
    }

    public static Vec3i transform(Vec3i in, Mirror mirror, Rotation rotation) {
        int x = in.getX();
        int z = in.getZ();
        if (mirror == Mirror.LEFT_RIGHT) {
            z = -z;
        } else if (mirror == Mirror.FRONT_BACK) {
            x = -x;
        }
        switch (rotation) {
            case CLOCKWISE_90:
                return new Vec3i(-z, in.getY(), x);
            case CLOCKWISE_180:
                return new Vec3i(-x, in.getY(), -z);
            case COUNTERCLOCKWISE_90:
                return new Vec3i(z, in.getY(), -x);
            default:
                return new Vec3i(x, in.getY(), z);
        }
    }

    /**
     * Loads the placement's own file. Returns null when this shortcut does not apply (no file on disk, not a
     * .litematic, or the player moved/rotated/disabled individual sub-regions); the caller then falls back.
     */
    public static Loaded fromFile(Info info) throws java.io.IOException {
        if (info.file() == null || !Files.isRegularFile(info.file())) return null;
        File file = info.file().toFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")) return null;
        var format = SchematicSystem.INSTANCE.getByFile(file);
        if (format.isEmpty()) return null;
        IStaticSchematic parsed;
        try (java.io.InputStream in = Files.newInputStream(info.file())) {
            parsed = format.get().parse(in);
        }
        if (!(parsed instanceof LitematicaSchematic lit)) return null;
        if (info.subs() == null || info.subs().size() != lit.getRegionPositions().size()) return null;
        for (Sub sub : info.subs()) {
            Vec3i saved = lit.getRegionPositions().get(sub.name());
            if (saved == null || !saved.equals(sub.pos()) || sub.rotation() != Rotation.NONE || sub.mirror() != Mirror.NONE) return null;
        }
        List<String> notes = new ArrayList<>(lit.getWarnings());
        Vec3i min = lit.getMinCorner();
        Mirror m = info.mirror();
        Rotation r = info.rotation();
        if (m == Mirror.NONE && r == Rotation.NONE) {
            return new Loaded(lit, info.origin().offset(min), notes);
        }
        int sx = lit.widthX(), sy = lit.heightY(), sz = lit.lengthZ();
        Vec3i a = transform(min, m, r);
        Vec3i b = transform(min.offset(sx - 1, sy - 1, sz - 1), m, r);
        int nx = Math.min(a.getX(), b.getX()), ny = Math.min(a.getY(), b.getY()), nz = Math.min(a.getZ(), b.getZ());
        int nsx = Math.abs(a.getX() - b.getX()) + 1, nsy = Math.abs(a.getY() - b.getY()) + 1, nsz = Math.abs(a.getZ() - b.getZ()) + 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[][][] states = new BlockState[nsx][nsz][nsy];
        for (BlockState[][] plane : states) for (BlockState[] column : plane) java.util.Arrays.fill(column, air);
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockState state = lit.getDirect(x, y, z);
                    if (state == null) continue;
                    Vec3i t = transform(min.offset(x, y, z), m, r);
                    states[t.getX() - nx][t.getZ() - nz][t.getY() - ny] = state.mirror(m).rotate(r);
                }
            }
        }
        return new Loaded(new StaticSchematic(states), info.origin().offset(nx, ny, nz), notes);
    }
}
