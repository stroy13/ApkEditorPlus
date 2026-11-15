package com.gmail.heagoo.common;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZipUtilTraversalTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void unzipRejectsFileEntryOutsideDestination() throws Exception {
        File zip = archive("assets/../../outside.txt", false);
        File destination = new File(folder.getRoot(), "project/assets");
        try {
            ZipUtil.unzipDirectory(zip.getPath(), "assets", destination.getPath());
            fail("Expected traversal entry to be rejected");
        } catch (java.io.IOException expected) { }
        assertEquals(false, new File(folder.getRoot(), "outside.txt").exists());
    }

    @Test public void unzipRejectsDirectoryEntryOutsideDestination() throws Exception {
        File zip = archive("assets/../../outside/", true);
        try {
            ZipUtil.unzipDirectory(zip.getPath(), "assets", new File(folder.getRoot(), "project/assets").getPath());
            fail("Expected traversal directory to be rejected");
        } catch (java.io.IOException expected) { }
        assertEquals(false, new File(folder.getRoot(), "outside").exists());
    }

    private File archive(String name, boolean directory) throws Exception {
        File zip = new File(folder.getRoot(), "input.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(name));
            if (!directory) out.write("bad".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }
}
