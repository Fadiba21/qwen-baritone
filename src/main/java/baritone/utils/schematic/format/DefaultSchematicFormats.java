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

package baritone.utils.schematic.format;

import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import baritone.utils.schematic.format.defaults.MCEditSchematic;
import baritone.utils.schematic.format.defaults.SpongeSchematic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Default implementations of {@link ISchematicFormat}
 *
 * @author Brady
 * @since 12/13/2019
 */
public enum DefaultSchematicFormats implements ISchematicFormat {

    /**
     * The MCEdit schematic specification. Commonly denoted by the ".schematic" file extension.
     */
    MCEDIT("schematic") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            return new MCEditSchematic(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
        }
    },

    /**
     * The SpongePowered Schematic Specification. Commonly denoted by the ".schem" file extension.
     *
     * @see <a href="https://github.com/SpongePowered/Schematic-Specification">Sponge Schematic Specification</a>
     */
    SPONGE("schem") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            int version = nbt.getInt("Version").orElse(-1);
            switch (version) {
                case 1:
                case 2:
                    return new SpongeSchematic(nbt);
                default:
                    throw new UnsupportedOperationException("Unsupported Version of a Sponge Schematic");
            }
        }
    },

    /**
     * The Litematica schematic specification. Commonly denoted by the ".litematic" file extension.
     */
    LITEMATICA("litematic") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            CompoundTag nbt;
            try {
                nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            } catch (java.util.zip.ZipException | java.io.EOFException e) {
                throw new IOException("Not a valid .litematic (it must be a gzip-compressed NBT file; the file is cut off or was renamed from another format): " + e.getMessage(), e);
            }
            int version = nbt.getInt("Version").orElse(-1);
            if (version < 0) {
                throw new IOException("Not a Litematica file: no 'Version' tag found. Re-export it from Litematica.");
            }
            if (version < 5) {
                throw new IOException("This litematic is format version " + version + " (Minecraft 1.12 era). Open it in Litematica and save it again to upgrade.");
            }
            LitematicaSchematic schematic;
            try {
                schematic = new LitematicaSchematic(nbt);
            } catch (IllegalArgumentException | ClassCastException | IndexOutOfBoundsException | NegativeArraySizeException | OutOfMemoryError e) {
                throw new IOException("Litematic (format v" + version + ") could not be read: " + e.getMessage(), e);
            }
            if (version < 6) {
                schematic.addWarning("Format v" + version + " is old (1.13-1.17); blocks that changed since then may become air.");
            } else if (version > 7) {
                schematic.addWarning("Format v" + version + " is newer than the tested v7; loaded anyway, check the result.");
            }
            return schematic;
        }
    };


    private final String extension;

    DefaultSchematicFormats(String extension) {
        this.extension = extension;
    }

    @Override
    public boolean isFileType(File file) {
        return this.extension.equalsIgnoreCase(FilenameUtils.getExtension(file.getAbsolutePath()));
    }

    @Override
    public List<String> getFileExtensions() {
        return Collections.singletonList(this.extension);
    }
}
