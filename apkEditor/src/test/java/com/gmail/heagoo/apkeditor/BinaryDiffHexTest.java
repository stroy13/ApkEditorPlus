package com.gmail.heagoo.apkeditor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BinaryDiffHexTest {
    @Test
    public void formatsAllSignedByteValuesWithoutUsingTokenMasks() {
        assertEquals("00", BinaryDiffHex.format((byte) 0x00));
        assertEquals("7F", BinaryDiffHex.format((byte) 0x7F));
        assertEquals("80", BinaryDiffHex.format((byte) 0x80));
        assertEquals("FF", BinaryDiffHex.format((byte) 0xFF));
    }
}
