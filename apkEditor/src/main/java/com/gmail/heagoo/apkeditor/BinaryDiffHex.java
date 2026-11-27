package com.gmail.heagoo.apkeditor;

/** Safe hexadecimal rendering for signed Java bytes. */
public final class BinaryDiffHex {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private BinaryDiffHex() {
    }

    public static String format(byte value) {
        int unsigned = value & 0xFF;
        return new String(new char[]{HEX[unsigned >>> 4], HEX[unsigned & 0x0F]});
    }
}
