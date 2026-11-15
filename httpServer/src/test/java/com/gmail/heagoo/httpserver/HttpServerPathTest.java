package com.gmail.heagoo.httpserver;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpServerPathTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void resolvesPathsInsideProject() throws Exception {
        File project = new File(folder.getRoot(), "project");
        assertEquals(new File(project, "res/values/strings.xml").getCanonicalFile(),
                new HttpServer(folder.getRoot().getPath(), project.getPath()).resolveProjectFile("res/values/strings.xml"));
    }

    @Test public void rejectsParentTraversal() throws Exception {
        HttpServer server = new HttpServer(folder.getRoot().getPath(), new File(folder.getRoot(), "project").getPath());
        try {
            server.resolveProjectFile("../outside.txt");
            fail("Expected path traversal to be rejected");
        } catch (IOException expected) { }
    }

    @Test public void rejectsSymlinkEscapeWhenSupported() throws Exception {
        File project = new File(folder.getRoot(), "project");
        File outside = new File(folder.getRoot(), "outside");
        assertTrue(project.mkdir());
        assertTrue(outside.mkdir());
        File link = new File(project, "linked");
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }
        try {
            new HttpServer(folder.getRoot().getPath(), project.getPath()).resolveProjectFile("linked/secret.txt");
            fail("Expected symlink escape to be rejected");
        } catch (IOException expected) { }
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError(); }
}
