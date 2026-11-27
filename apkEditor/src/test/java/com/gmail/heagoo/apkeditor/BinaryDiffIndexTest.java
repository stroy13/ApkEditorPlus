package com.gmail.heagoo.apkeditor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BinaryDiffIndexTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void indexesDifferentBinaryStreams() throws Exception {
        File index = folder.newFile("different.idx");
        long count = BinaryDiffIndex.build(
                new ByteArrayInputStream(new byte[]{0x00, 0x01, 0x7f}),
                new ByteArrayInputStream(new byte[]{0x00, 0x02, 0x7f}),
                index,
                () -> false);

        assertEquals(1L, count);
        java.io.RandomAccessFile input = new java.io.RandomAccessFile(index, "r");
        try {
            List<BinaryDiffIndex.Row> rows = BinaryDiffIndex.readPage(
                    input, 0, BinaryDiffIndex.PAGE_ROWS);
            assertEquals(1, rows.size());
            assertEquals(0L, rows.get(0).offset);
            assertArrayEquals(new byte[]{0x00, 0x01, 0x7f}, rows.get(0).left);
            assertArrayEquals(new byte[]{0x00, 0x02, 0x7f}, rows.get(0).right);
        } finally {
            input.close();
        }
    }

    @Test
    public void indexesAnAddedOrRemovedEntryAgainstAnEmptySide() throws Exception {
        File index = folder.newFile("missing-side.idx");
        long count = BinaryDiffIndex.build(
                new ByteArrayInputStream("DEX payload".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayInputStream(new byte[0]),
                index,
                () -> false);

        assertEquals(1L, count);
        java.io.RandomAccessFile input = new java.io.RandomAccessFile(index, "r");
        try {
            List<BinaryDiffIndex.Row> rows = BinaryDiffIndex.readPage(input, 0, 1);
            assertEquals(1, rows.size());
            assertEquals("DEX payload", new String(rows.get(0).left, StandardCharsets.US_ASCII));
            assertEquals(0, rows.get(0).right.length);
        } finally {
            input.close();
        }
    }
}
