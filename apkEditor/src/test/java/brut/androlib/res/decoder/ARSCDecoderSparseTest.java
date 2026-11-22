package brut.androlib.res.decoder;

import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression tests for bundletool's SDK-32 sparse resource type index. */
public class ARSCDecoderSparseTest {
    @Test
    public void expandsSortedIdAndOffsetPairsIntoDenseEntryOffsets() throws Exception {
        int[] encoded = {
                (0 << 16) | 1,
                (2 << 16) | 4,
                (7 << 16) | 9
        };

        assertArrayEquals(new int[]{-1, 0, -1, -1, 8, -1, -1, -1, -1, 28},
                ARSCDecoder.decodeSparseEntryOffsets(encoded, 10));
    }

    @Test
    public void rejectsUnsortedSparseEntries() throws Exception {
        try {
            ARSCDecoder.decodeSparseEntryOffsets(new int[]{(1 << 16) | 4, (2 << 16) | 3}, 8);
            fail("Expected unsorted sparse index to be rejected");
        } catch (AndrolibException expected) {
            assertTrue(expected.getMessage().contains("not strictly sorted"));
        }
    }

    @Test
    public void rejectsSparseEntryOutsideTypeSpecRange() throws Exception {
        try {
            ARSCDecoder.decodeSparseEntryOffsets(new int[]{(1 << 16) | 8}, 8);
            fail("Expected out-of-range sparse index to be rejected");
        } catch (AndrolibException expected) {
            assertTrue(expected.getMessage().contains("out of bounds"));
        }
    }

    @Test
    public void decodesACompleteSparseResourceTableWithNonContiguousIds() throws Exception {
        ARSCDecoder.ARSCData decoded = ARSCDecoder.decode(
                new ByteArrayInputStream(sparseResourceTable()), false, false);
        ResPackage pkg = decoded.getOnePackage();

        ResResSpec first = pkg.getResSpec(new ResID(0x7f010001));
        ResResSpec second = pkg.getResSpec(new ResID(0x7f010004));
        assertEquals("hello", first.getName());
        assertEquals("world", second.getName());
    }

    private static byte[] sparseResourceTable() {
        byte[] values = stringPool(new String[]{"Hello", "World"});
        byte[] typeNames = stringPool(new String[]{"string"});
        byte[] keyNames = stringPool(new String[]{"hello", "world"});

        byte[] typeSpec = new byte[16 + 5 * 4];
        ByteBuffer spec = ByteBuffer.wrap(typeSpec).order(ByteOrder.LITTLE_ENDIAN);
        spec.putShort((short) 0x0202);
        spec.putShort((short) 16);
        spec.putInt(typeSpec.length);
        spec.put((byte) 1); // type id: string
        spec.put((byte) 0);
        spec.putShort((short) 0);
        spec.putInt(5);     // full ID range; sparse type has only two values

        byte[] type = new byte[56 + 32];
        ByteBuffer config = ByteBuffer.wrap(type).order(ByteOrder.LITTLE_ENDIAN);
        config.putShort((short) 0x0201);
        config.putShort((short) 48); // common header + fields + 28-byte config
        config.putInt(type.length);
        config.put((byte) 1);       // type id
        config.put((byte) 0x01);    // FLAG_SPARSE
        config.putShort((short) 0);
        config.putInt(2);            // present entries, not the full ID range
        config.putInt(56);           // 48-byte header + two 4-byte sparse pairs
        config.putInt(28);           // ResTable_config.size
        config.put(new byte[24]);
        config.putInt(0x00000001);   // id 1, offset 0
        config.putInt(0x00040004);   // id 4, offset 16 / 4
        putSimpleStringEntry(config, 0, 0);
        putSimpleStringEntry(config, 1, 1);

        int packageSize = 288 + typeNames.length + keyNames.length
                + typeSpec.length + type.length;
        byte[] pkg = new byte[packageSize];
        ByteBuffer packageBuffer = ByteBuffer.wrap(pkg).order(ByteOrder.LITTLE_ENDIAN);
        packageBuffer.putShort((short) 0x0200);
        packageBuffer.putShort((short) 288);
        packageBuffer.putInt(packageSize);
        packageBuffer.position(8);
        packageBuffer.putInt(0x7f);
        byte[] packageName = "com.example.sparse".getBytes(StandardCharsets.UTF_16LE);
        packageBuffer.put(packageName);
        packageBuffer.position(8 + 4 + 256);
        packageBuffer.putInt(288); // typeStrings, relative to package start
        packageBuffer.putInt(0);
        packageBuffer.putInt(288 + typeNames.length);
        packageBuffer.putInt(1);
        packageBuffer.putInt(0);   // typeIdOffset
        packageBuffer.position(288);
        packageBuffer.put(typeNames);
        packageBuffer.put(keyNames);
        packageBuffer.put(typeSpec);
        packageBuffer.put(type);

        int tableSize = 12 + values.length + pkg.length;
        ByteBuffer table = ByteBuffer.allocate(tableSize).order(ByteOrder.LITTLE_ENDIAN);
        table.putShort((short) 0x0002);
        table.putShort((short) 12);
        table.putInt(tableSize);
        table.putInt(1);
        table.put(values);
        table.put(pkg);
        return table.array();
    }

    private static void putSimpleStringEntry(ByteBuffer out, int keyIndex, int valueIndex) {
        out.putShort((short) 8);
        out.putShort((short) 0);
        out.putInt(keyIndex);
        out.putShort((short) 8);
        out.put((byte) 0);
        out.put((byte) 0x03); // TYPE_STRING
        out.putInt(valueIndex);
    }

    private static byte[] stringPool(String[] strings) {
        int headerSize = 28 + strings.length * 4;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            offsets[i] = data.size();
            byte[] encoded = strings[i].getBytes(StandardCharsets.UTF_8);
            data.write(strings[i].length());
            data.write(encoded.length);
            data.write(encoded, 0, encoded.length);
            data.write(0);
        }
        byte[] raw = data.toByteArray();
        int padding = (4 - ((headerSize + raw.length) & 3)) & 3;
        byte[] pool = new byte[headerSize + raw.length + padding];
        ByteBuffer result = ByteBuffer.wrap(pool).order(ByteOrder.LITTLE_ENDIAN);
        result.putShort((short) 0x0001);
        result.putShort((short) 28);
        result.putInt(pool.length);
        result.putInt(strings.length);
        result.putInt(0);
        result.putInt(0x100); // UTF-8
        result.putInt(headerSize);
        result.putInt(0);
        for (int offset : offsets) result.putInt(offset);
        result.put(raw);
        return pool;
    }
}
