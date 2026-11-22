package com.gmail.heagoo.apkeditor.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Aapt2ResourceTableFallbackTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void restoresOriginalTableWhenAapt2OutputHasZeroPackages() throws Exception {
        File original = folder.newFile("original.apk");
        File built = folder.newFile("built.apk");
        byte[] originalTable = resourceTable(1);
        writeApk(original, originalTable, "original-manifest");
        writeApk(built, resourceTable(0), "compiled-manifest");

        assertTrue(Aapt2ResourceTableFallback.restoreOriginalIfEmpty(built.getAbsolutePath(), original.getAbsolutePath()));
        try (ZipFile result = new ZipFile(built)) {
            assertArrayEquals(originalTable, read(result, "resources.arsc"));
            assertArrayEquals("compiled-manifest".getBytes(StandardCharsets.UTF_8), read(result, "AndroidManifest.xml"));
        }
        assertFalse(Aapt2ResourceTableFallback.restoreOriginalIfEmpty(built.getAbsolutePath(), original.getAbsolutePath()));
    }

    private static byte[] resourceTable(int packageCount) {
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0x0002).putShort((short) 12).putInt(12).putInt(packageCount);
        return b.array();
    }

    private static void writeApk(File file, byte[] arsc, String manifest) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry("resources.arsc")); out.write(arsc); out.closeEntry();
            out.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            out.write(manifest.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
        }
    }

    private static byte[] read(ZipFile zip, String name) throws Exception {
        try (java.io.InputStream in = zip.getInputStream(zip.getEntry(name));
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[128]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
