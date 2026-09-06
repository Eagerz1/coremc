package net.coremc.skyblock.core.schematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loads Sponge v2 / v3 (.schem) schematic files and pastes them into the world.
 *
 * <p>This is a minimal, correct reader for the Schematic spec used by WorldEdit /
 * MCEdit2 / Sponge: a GZIP'd NBT compound with {@code Version}, {@code Width},
 * {@code Height}, {@code Length}, {@code Palette} (string -> index) and a
 * {@code BlockData} byte array of varint palette indices.</p>
 *
 * <p>The old loader read the Int dimensions with {@code asShort()} (yielding 0)
 * and treated the BlockData as a flat byte-per-block array, so it silently
 * failed and every island fell back to a tiny grass platform. This version reads
 * the spec properly.</p>
 */
public final class SchematicLoader {

    public static void paste(final JavaPlugin plugin, final File file, final Location base) throws Exception {
        final Schematic schem = load(plugin, file);
        if (schem == null) {
            throw new IllegalArgumentException("Failed to load schematic: " + file.getName());
        }
        final World world = base.getWorld();
        final int bx = base.getBlockX();
        final int by = base.getBlockY();
        final int bz = base.getBlockZ();

        final int[] indices = schem.indices();
        int i = 0;
        for (int y = 0; y < schem.height; y++) {
            for (int z = 0; z < schem.length; z++) {
                for (int x = 0; x < schem.width; x++, i++) {
                    if (i >= indices.length) break;
                    final int paletteIndex = indices[i];
                    final String key = schem.palette.get(paletteIndex);
                    if (key == null) continue; // air / unknown
                    final Material mat = schem.materialFor(key);
                    if (mat == null || mat == Material.AIR) continue;
                    try {
                        final BlockData bd = org.bukkit.Bukkit.createBlockData(key);
                        world.getBlockAt(bx + x, by + y, bz + z).setBlockData(bd, false);
                    } catch (final IllegalArgumentException ignored) {
                        world.getBlockAt(bx + x, by + y, bz + z).setType(mat, false);
                    }
                }
            }
        }
        plugin.getLogger().info("Pasted schematic " + file.getName() + " (" + schem.width + "x"
                + schem.height + "x" + schem.length + ") at " + bx + "," + by + "," + bz);
    }

    /** Lightweight schematic dimensions (used to centre a paste on the spawn). */
    public static final class SchematicDims {
        public final int width, height, length;
        public SchematicDims(final int width, final int height, final int length) {
            this.width = width; this.height = height; this.length = length;
        }
        public int width() { return width; }
        public int height() { return height; }
        public int length() { return length; }
    }

    /** Read just the Width/Height/Length of a schematic (null on any failure). */
    public static SchematicDims dimensions(final JavaPlugin plugin, final File file) {
        try {
            final byte[] data;
            try (FileInputStream fis = new FileInputStream(file)) {
                data = fis.readAllBytes();
            }
            final byte[] raw = isGzip(data) ? gunzip(data) : data;
            final NbtReader reader = new NbtReader(new ByteArrayInputStream(raw));
            final Tag root = reader.readRoot();
            if (root == null || root.type != 10) return null;
            final Integer width = root.intOf("Width");
            final Integer height = root.intOf("Height");
            final Integer length = root.intOf("Length");
            if (width == null || height == null || length == null) return null;
            return new SchematicDims(width, height, length);
        } catch (final Exception e) {
            return null;
        }
    }

    public static Schematic load(final JavaPlugin plugin, final File file) {
        try {
            final byte[] data;
            try (FileInputStream fis = new FileInputStream(file)) {
                data = fis.readAllBytes();
            }
            final byte[] raw = isGzip(data) ? gunzip(data) : data;
            final NbtReader reader = new NbtReader(new ByteArrayInputStream(raw));
            final Tag root = reader.readRoot();
            if (root == null || root.type != 10) { // TAG_Compound
                plugin.getLogger().warning("Schematic " + file.getName() + ": root is not a compound");
                return null;
            }
            final Integer version = root.intOf("Version");
            if (version == null || (version != 1 && version != 2 && version != 3)) {
                plugin.getLogger().warning("Schematic " + file.getName()
                        + ": unsupported/missing Version (got " + version + ")");
                return null;
            }
            final Integer width = root.intOf("Width");
            final Integer height = root.intOf("Height");
            final Integer length = root.intOf("Length");
            if (width == null || height == null || length == null) {
                plugin.getLogger().warning("Schematic " + file.getName() + ": missing dimensions");
                return null;
            }
            final Tag paletteTag = root.child("Palette");
            if (paletteTag == null || paletteTag.type != 10) {
                plugin.getLogger().warning("Schematic " + file.getName() + ": missing Palette");
                return null;
            }
            final Map<Integer, String> palette = new HashMap<>();
            for (final Map.Entry<String, Tag> e : paletteTag.compoundVal.entrySet()) {
                palette.put(e.getValue().asInt(), e.getKey());
            }
            final Tag blockData = root.child("BlockData");
            if (blockData == null || blockData.type != 7) { // TAG_Byte_Array
                plugin.getLogger().warning("Schematic " + file.getName() + ": missing BlockData");
                return null;
            }
            final Schematic schem = new Schematic();
            schem.width = width;
            schem.height = height;
            schem.length = length;
            schem.palette = palette;
            schem.blockData = blockData.byteArrayVal;
            return schem;
        } catch (final Exception e) {
            plugin.getLogger().warning("Schematic load error for " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean isGzip(final byte[] data) {
        return data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B;
    }

    private static byte[] gunzip(final byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buf = new byte[8192];
            int n;
            while ((n = gis.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    /** Decoded schematic model. */
    public static final class Schematic {
        public Map<Integer, String> palette = new HashMap<>();
        public byte[] blockData;
        public int width, height, length;

        /** Decode the varint-packed palette indices into a flat index array. */
        public int[] indices() {
            final int total = width * height * length;
            final int[] out = new int[total];
            int p = 0, idx = 0;
            while (idx < total && p < blockData.length) {
                int value = 0, shift = 0;
                byte b;
                do {
                    b = blockData[p++];
                    value |= (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0 && p < blockData.length);
                out[idx++] = value;
            }
            return out;
        }

        public Material materialFor(final String key) {
            if (key == null) return null;
            final String base = key.contains("[") ? key.substring(0, key.indexOf('[')) : key;
            try {
                return Material.valueOf(base.toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Minimal NBT reader (GZIP already decompressed by caller). */
    public static final class NbtReader {
        private final DataInputStream in;

        public NbtReader(final InputStream raw) {
            this.in = new DataInputStream(raw);
        }

        public Tag readRoot() throws IOException {
            final int type = in.readUnsignedByte();
            if (type == 0) return null;
            readString(); // root name (ignored)
            return readCompoundPayload(type);
        }

        private Tag readCompoundPayload(final int type) throws IOException {
            final Tag tag = new Tag();
            tag.type = type;
            if (type != 10) return tag;
            while (true) {
                final int nt = in.readUnsignedByte();
                if (nt == 0) break;
                final String name = readString();
                final Tag child = readValue(nt);
                tag.compoundVal.put(name, child);
            }
            return tag;
        }

        private Tag readValue(final int type) throws IOException {
            final Tag t = new Tag();
            t.type = type;
            switch (type) {
                case 1 -> t.byteVal = in.readByte();
                case 2 -> t.shortVal = in.readShort();
                case 3 -> t.intVal = in.readInt();
                case 4 -> t.longVal = in.readLong();
                case 5 -> t.floatVal = in.readFloat();
                case 6 -> t.doubleVal = in.readDouble();
                case 7 -> {
                    final int len = in.readInt();
                    t.byteArrayVal = new byte[len];
                    in.readFully(t.byteArrayVal);
                }
                case 8 -> t.strVal = readString();
                case 9 -> {
                    final int et = in.readUnsignedByte();
                    final int n = in.readInt();
                    t.listType = et;
                    t.listVal = new ArrayList<>();
                    for (int i = 0; i < n; i++) t.listVal.add(readValue(et));
                }
                case 10 -> {
                    while (true) {
                        final int nt = in.readUnsignedByte();
                        if (nt == 0) break;
                        final String name = readString();
                        final Tag child = readValue(nt);
                        t.compoundVal.put(name, child);
                    }
                }
                case 11 -> {
                    final int n = in.readInt();
                    in.skipBytes(n * 4);
                }
                case 12 -> {
                    final int n = in.readInt();
                    in.skipBytes(n * 8);
                }
                default -> throw new IOException("Unsupported NBT tag type " + type);
            }
            return t;
        }

        private String readString() throws IOException {
            final int len = in.readShort() & 0xFFFF;
            final byte[] b = new byte[len];
            in.readFully(b);
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** NBT node. */
    public static final class Tag {
        public int type;
        public byte byteVal;
        public short shortVal;
        public int intVal;
        public long longVal;
        public float floatVal;
        public double doubleVal;
        public String strVal = "";
        public byte[] byteArrayVal;
        public int listType;
        public List<Tag> listVal = new ArrayList<>();
        public Map<String, Tag> compoundVal = new HashMap<>();

        public int asInt() {
            return switch (type) {
                case 1 -> byteVal;
                case 2 -> shortVal;
                case 3 -> intVal;
                default -> 0;
            };
        }

        public Tag child(final String name) { return compoundVal.get(name); }
        public Integer intOf(final String name) {
            final Tag t = compoundVal.get(name);
            return t == null ? null : t.asInt();
        }
    }
}
