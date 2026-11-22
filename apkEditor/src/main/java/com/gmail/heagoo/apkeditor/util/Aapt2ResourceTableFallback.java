package com.gmail.heagoo.apkeditor.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Restores the original table when AAPT2 emitted an empty table for a resource-less decode. */
public final class Aapt2ResourceTableFallback {
    private Aapt2ResourceTableFallback() { }

    public static boolean restoreOriginalIfEmpty(String builtApkPath, String originalApkPath) throws IOException {
        File builtApk = new File(builtApkPath);
        byte[] replacement = null;
        try (ZipFile built = new ZipFile(builtApk)) {
            ZipEntry table = built.getEntry("resources.arsc");
            if (table == null) return false;
            try (InputStream in = built.getInputStream(table)) {
                byte[] header = new byte[12];
                int read = 0;
                while (read < header.length) {
                    int n = in.read(header, read, header.length - read);
                    if (n < 0) break;
                    read += n;
                }
                if (read < header.length) return false;
                ByteBuffer data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                if ((data.getShort(0) & 0xffff) != 0x0002 || data.getInt(8) != 0) return false;
            }
        }
        try (ZipFile original = new ZipFile(originalApkPath)) {
            ZipEntry table = original.getEntry("resources.arsc");
            if (table == null) throw new IOException("Original APK has no resources.arsc to restore");
            try (InputStream in = original.getInputStream(table)) {
                replacement = readFully(in);
            }
            if (replacement.length < 12 || (ByteBuffer.wrap(replacement).order(ByteOrder.LITTLE_ENDIAN).getShort(0) & 0xffff) != 0x0002
                    || ByteBuffer.wrap(replacement).order(ByteOrder.LITTLE_ENDIAN).getInt(8) < 1) {
                throw new IOException("Original APK also has an empty or invalid resources.arsc");
            }
        }
        File temp = File.createTempFile("aapt2-resource-table-", ".apk", builtApk.getAbsoluteFile().getParentFile());
        boolean complete = false;
        try (ZipFile built = new ZipFile(builtApk);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temp))) {
            Enumeration<? extends ZipEntry> entries = built.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if ("resources.arsc".equals(entry.getName())) {
                    out.write(replacement);
                } else {
                    try (InputStream in = built.getInputStream(entry)) {
                        copy(in, out);
                    }
                }
                out.closeEntry();
            }
            complete = true;
        } finally {
            if (!complete) Files.deleteIfExists(temp.toPath());
        }
        if (!builtApk.delete() || !temp.renameTo(builtApk)) {
            Files.deleteIfExists(temp.toPath());
            throw new IOException("Cannot replace empty AAPT2 resource table");
        }
        return true;
    }

    private static byte[] readFully(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }

    private static void copy(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
}
