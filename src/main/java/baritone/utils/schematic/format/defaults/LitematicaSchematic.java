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

package baritone.utils.schematic.format.defaults;

import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Based on EmersonDove's work
 * <a href="https://github.com/cabaletta/baritone/pull/2544">...</a>
 *
 * @author rycbar
 * @since 22.09.2022
 */
public final class LitematicaSchematic extends CompositeSchematic implements IStaticSchematic {

    /**
     * @param nbtTagCompound a decompressed file stream aka nbt data.
     * @param rotated        if the schematic is rotated by 90°.
     */
    public LitematicaSchematic(CompoundTag nbt) {
        super(0, 0, 0);
        fillInSchematic(nbt);
    }

    /** Block ids that were renamed between Minecraft versions; old litematics still contain the old ids. */
    private static final Map<String, String> RENAMES = Map.of(
        "minecraft:grass", "minecraft:short_grass",
        "minecraft:grass_path", "minecraft:dirt_path",
        "minecraft:sign", "minecraft:oak_sign",
        "minecraft:wall_sign", "minecraft:oak_wall_sign"
    );
    private static final long MAX_BLOCKS = 1_000_000_000L;

    private final List<String> warnings = new ArrayList<>();
    private Vec3i minCorner = Vec3i.ZERO;
    private final Map<String, Vec3i> regionPositions = new LinkedHashMap<>();
    private final Map<String, Integer> unknownBlocks = new LinkedHashMap<>();
    private int skippedProperties;
    private int outOfRangeIndexes;

    /** Non-fatal problems found while loading (unknown blocks, skipped properties, newer format version...). */
    public List<String> getWarnings() {
        List<String> all = new ArrayList<>(warnings);
        if (!unknownBlocks.isEmpty()) {
            StringBuilder sb = new StringBuilder("Unknown blocks replaced by air (mod/version mismatch): ");
            int n = 0;
            for (Map.Entry<String, Integer> e : unknownBlocks.entrySet()) {
                if (n++ > 0) sb.append(", ");
                if (n > 8) { sb.append("... +").append(unknownBlocks.size() - 8).append(" more"); break; }
                sb.append(e.getKey());
            }
            all.add(sb.toString());
        }
        if (skippedProperties > 0) all.add(skippedProperties + " block properties were not valid for this Minecraft version and were skipped.");
        if (outOfRangeIndexes > 0) all.add(outOfRangeIndexes + " blocks pointed outside the palette and were replaced by air.");
        return all;
    }

    /** Lowest corner of all regions, relative to the litematic's own origin (where a placement origin applies). */
    public Vec3i getMinCorner() {
        return minCorner;
    }

    /** Raw saved "Position" of each region by name; used to detect whether a Litematica placement moved a region. */
    public Map<String, Vec3i> getRegionPositions() {
        return Collections.unmodifiableMap(regionPositions);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * @return Array of subregion tags.
     */
    private static CompoundTag[] getRegions(CompoundTag nbt) {
        return nbt.getCompound("Regions")
            .map(CompoundTag::values)
            .map(r -> r.stream()
                .filter(v -> v instanceof CompoundTag)
                .map(CompoundTag.class::cast)
                .toArray(CompoundTag[]::new)
            ).orElse(new CompoundTag[0]);
    }

    /**
     * Gets both ends from a region box for a given axis and returns the lower one.
     *
     * @param s axis that should be read.
     * @return the lower coord of the requested axis.
     */
    private static int getMinOfSubregion(CompoundTag subReg, String s) {
        int a = subReg.getCompound("Position").flatMap(position -> position.getInt(s)).orElse(0);
        int b = subReg.getCompound("Size").flatMap(size -> size.getInt(s)).orElse(0);
        return Math.min(a, a + b + 1);
    }

    /**
     * @param blockStatePalette List of all different block types used in the schematic.
     * @return Array of BlockStates.
     */
    private BlockState[] getBlockList(ListTag blockStatePalette) {
        BlockState[] blockList = new BlockState[blockStatePalette.size()];

        for (int i = 0; i < blockStatePalette.size(); i++) {
            if (!(blockStatePalette.get(i) instanceof CompoundTag tag)) {
                blockList[i] = Blocks.AIR.defaultBlockState();
                continue;
            }
            String name = tag.getString("Name").orElse("");
            String renamed = RENAMES.getOrDefault(name, name);
            Identifier blockKey = Identifier.tryParse(renamed);
            Block block = blockKey == null
                ? null
                : BuiltInRegistries.BLOCK.get(blockKey).map(Holder.Reference::value).orElse(null);
            if (block == null) {
                if (!name.isEmpty()) unknownBlocks.merge(name, 1, Integer::sum);
                blockList[i] = Blocks.AIR.defaultBlockState();
                continue;
            }
            CompoundTag properties = tag.getCompound("Properties").orElse(new CompoundTag());
            blockList[i] = getBlockState(block, properties);
        }
        return blockList;
    }

    /**
     * @param block      block.
     * @param properties List of Properties the block has.
     * @return A blockState.
     */
    private BlockState getBlockState(Block block, CompoundTag properties) {
        BlockState blockState = block.defaultBlockState();

        for (String key : properties.keySet()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            String propertyValue = properties.getString(key).orElse(null);
            if (property != null && propertyValue != null) {
                BlockState next = setPropertyValue(blockState, property, propertyValue);
                if (next == null) skippedProperties++; else blockState = next;
            } else {
                skippedProperties++;
            }
        }
        return blockState;
    }

    /**
     * @author Emerson
     */
    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : null;
    }

    /**
     * @param amountOfBlockTypes amount of block types in the schematic.
     * @return amount of bits used to encode a block.
     */
    private static int getBitsPerBlock(int amountOfBlockTypes) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, amountOfBlockTypes) - 1));
    }

    /**
     * Calculates the volume of the subregion. As size can be a negative value we take the absolute value of the
     * multiplication as the volume still holds a positive amount of blocks.
     *
     * @return the volume of the subregion.
     */
    private static long getVolume(CompoundTag subReg) {
        CompoundTag size = subReg.getCompound("Size").orElse(new CompoundTag());
        return Math.abs((long) size.getInt("x").orElse(0) * (long) size.getInt("y").orElse(0) * (long) size.getInt("z").orElse(0));
    }

    /**
     * @param s axis.
     * @return the lowest coordinate of that axis of the schematic.
     */
    private static int getMinOfSchematic(CompoundTag nbt, String s) {
        int n = Integer.MAX_VALUE;
        for (CompoundTag subReg : getRegions(nbt)) {
            n = Math.min(n, getMinOfSubregion(subReg, s));
        }
        return n;
    }

    /**
     * reads the file data.
     */
    private void fillInSchematic(CompoundTag nbt) {
        CompoundTag[] regions = getRegions(nbt);
        if (regions.length == 0) {
            throw new IllegalArgumentException("This litematic has no regions (empty schematic, or not a real .litematic file).");
        }
        Vec3i offsetMinCorner = new Vec3i(getMinOfSchematic(nbt, "x"), getMinOfSchematic(nbt, "y"), getMinOfSchematic(nbt, "z"));
        this.minCorner = offsetMinCorner;
        nbt.getCompound("Regions").ifPresent(all -> {
            for (String name : all.keySet()) {
                all.getCompound(name).ifPresent(region -> {
                    CompoundTag pos = region.getCompound("Position").orElse(new CompoundTag());
                    regionPositions.put(name, new Vec3i(pos.getInt("x").orElse(0), pos.getInt("y").orElse(0), pos.getInt("z").orElse(0)));
                });
            }
        });
        for (CompoundTag subReg : regions) {
            long regionVolume = getVolume(subReg);
            if (regionVolume == 0) {
                continue;
            }
            if (regionVolume > MAX_BLOCKS) {
                throw new IllegalArgumentException("A region is too large (" + regionVolume + " blocks).");
            }
            ListTag usedBlockTypes = subReg.getListOrEmpty("BlockStatePalette");
            if (usedBlockTypes.isEmpty()) {
                throw new IllegalArgumentException("A region has an empty block palette: the file is damaged or incomplete.");
            }
            BlockState[] blockList = getBlockList(usedBlockTypes);

            int bitsPerBlock = getBitsPerBlock(usedBlockTypes.size());
            long[] blockStateArray = subReg.getLongArray("BlockStates").orElse(new long[0]);
            long neededLongs = (regionVolume * bitsPerBlock + 63L) / 64L;
            if (blockStateArray.length < neededLongs) {
                throw new IllegalArgumentException("Block data is truncated (" + blockStateArray.length + " of " + neededLongs
                    + " data blocks present): the file is damaged or was saved incompletely.");
            }

            LitematicaBitArray bitArray = new LitematicaBitArray(bitsPerBlock, regionVolume, blockStateArray);
            writeSubregionIntoSchematic(subReg, offsetMinCorner, blockList, bitArray);
        }
    }

    /**
     * Writes the file data in to the IBlockstate array.
     *
     * @param blockList list with the different block types used in the schematic.
     * @param bitArray  bit array that holds the placement pattern.
     */
    private void writeSubregionIntoSchematic(CompoundTag subReg, Vec3i offsetMinCorner, BlockState[] blockList, LitematicaBitArray bitArray) {
        int offsetX = getMinOfSubregion(subReg, "x") - offsetMinCorner.getX();
        int offsetY = getMinOfSubregion(subReg, "y") - offsetMinCorner.getY();
        int offsetZ = getMinOfSubregion(subReg, "z") - offsetMinCorner.getZ();
        CompoundTag size = subReg.getCompound("Size").orElse(new CompoundTag());
        int sizeX = Math.abs(size.getInt("x").orElse(0));
        int sizeY = Math.abs(size.getInt("y").orElse(0));
        int sizeZ = Math.abs(size.getInt("z").orElse(0));
        BlockState[][][] states = new BlockState[sizeX][sizeZ][sizeY];
        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int id = bitArray.getAt(index);
                    if (id >= blockList.length) {
                        outOfRangeIndexes++;
                        states[x][z][y] = Blocks.AIR.defaultBlockState();
                    } else {
                        states[x][z][y] = blockList[id];
                    }
                    index++;
                }
            }
        }
        this.put(new StaticSchematic(states), offsetX, offsetY, offsetZ);
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return desiredState(x, y, z, null, Collections.emptyList());
    }

    /**
     * @author maruohon
     * Class from the Litematica mod by maruohon
     * Usage under LGPLv3 with the permission of the author.
     * <a href="https://github.com/maruohon/litematica">...</a>
     */
    private static class LitematicaBitArray {
        /**
         * The long array that is used to store the data for this BitArray.
         */
        private final long[] longArray;
        /**
         * Number of bits a single entry takes up
         */
        private final int bitsPerEntry;
        /**
         * The maximum value for a single entry. This also works as a bitmask for a single entry.
         * For instance, if bitsPerEntry were 5, this value would be 31 (ie, {@code 0b00011111}).
         */
        private final long maxEntryValue;
        /**
         * Number of entries in this array (<b>not</b> the length of the long array that internally backs this array)
         */
        private final long arraySize;

        public LitematicaBitArray(int bitsPerEntryIn, long arraySizeIn, @Nullable long[] longArrayIn) {
            Validate.inclusiveBetween(1L, 32L, bitsPerEntryIn);
            this.arraySize = arraySizeIn;
            this.bitsPerEntry = bitsPerEntryIn;
            this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;

            if (longArrayIn != null) {
                this.longArray = longArrayIn;
            } else {
                this.longArray = new long[(int) (roundUp(arraySizeIn * (long) bitsPerEntryIn, 64L) / 64L)];
            }
        }

        public static long roundUp(long number, long interval) {
            int sign = 1;
            if (interval == 0) {
                return 0;
            } else if (number == 0) {
                return interval;
            } else {
                if (number < 0) {
                    sign = -1;
                }

                long i = number % (interval * sign);
                return i == 0 ? number : number + (interval * sign) - i;
            }
        }

        public int getAt(long index) {
            Validate.inclusiveBetween(0L, this.arraySize - 1L, index);
            long startOffset = index * (long) this.bitsPerEntry;
            int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
            int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
            int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

            if (startArrIndex == endArrIndex) {
                return (int) (this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
            } else {
                int endOffset = 64 - startBitOffset;
                return (int) ((this.longArray[startArrIndex] >>> startBitOffset | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
            }
        }

        public long size() {
            return this.arraySize;
        }
    }
}
