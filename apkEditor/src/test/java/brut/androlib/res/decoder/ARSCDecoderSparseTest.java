package brut.androlib.res.decoder;

import brut.androlib.AndrolibException;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
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
}
