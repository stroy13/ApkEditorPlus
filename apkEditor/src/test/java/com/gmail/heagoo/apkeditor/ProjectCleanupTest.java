package com.gmail.heagoo.apkeditor;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectCleanupTest {
    @Test public void retriesTransientDirectoryDeleteFailure() throws Exception {
        File root = Files.createTempDirectory("project-cleanup-test").toFile();
        File nested = new File(root, "smali_classes3/org/mozilla/fenix");
        assertTrue(nested.mkdirs());
        assertTrue(new File(nested, "Example.smali").createNewFile());
        AtomicInteger directoryAttempts = new AtomicInteger();

        assertTrue(ProjectCleanup.deleteAll(root, () -> false, file -> {
            if (file.equals(nested) && directoryAttempts.getAndIncrement() == 0) return false;
            return file.delete();
        }));

        assertTrue(directoryAttempts.get() >= 2);
        assertFalse(root.exists());
    }
}
