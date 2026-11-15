package com.gmail.heagoo.apkeditor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class ZipPathSanitizerTest {
    @Test
    public void rewritesAbsoluteNamesWithoutChangingEntryPayloads() throws Exception {
        File dir = Files.createTempDirectory("zip-path-sanitizer").toFile();
        File source = new File(dir, "source.apk");
        File safe = null;
        try {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source))) {
                put(out, "/AndroidManifest.xml/  .xml", new byte[]{1, 2, 3});
                put(out, "AndroidManifest.xml", new byte[]{4, 5});
            }
            safe = ZipPathSanitizer.sanitize(source, dir);
            assertNotSame(source, safe);
            try (ZipFile zip = new ZipFile(safe)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                boolean patched = false;
                boolean original = false;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().equals("_AndroidManifest.xml/  .xml")) {
                        patched = true;
                        assertEquals(3, zip.getInputStream(entry).readAllBytes().length);
                    }
                    original |= entry.getName().equals("AndroidManifest.xml");
                    assertTrue("no entry should remain absolute", !entry.getName().startsWith("/"));
                }
                assertTrue(patched);
                assertTrue(original);
            }
        } finally {
            if (safe != null && !safe.equals(source)) safe.delete();
            source.delete();
            dir.delete();
        }
    }

    private static void put(ZipOutputStream out, String name, byte[] value) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(value);
        out.closeEntry();
    }
}
