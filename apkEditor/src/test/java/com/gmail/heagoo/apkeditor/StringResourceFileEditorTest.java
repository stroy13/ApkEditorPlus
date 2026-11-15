package com.gmail.heagoo.apkeditor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StringResourceFileEditorTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void editingPreservesOtherStringsAndResourcesCloseTag() throws Exception {
        File file = new File(folder.getRoot(), "strings.xml");
        Files.write(file.toPath(), ("<resources>\n"
                + "<string name=\"first\">old</string>\n"
                + "<string name=\"second\">keep</string>\n"
                + "</resources>\n").getBytes(StandardCharsets.UTF_8));
        StringResourceFileEditor.save(file, Collections.singletonMap("first", "changed"));
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("<string name=\"first\">changed</string>"));
        assertTrue(saved.contains("<string name=\"second\">keep</string>"));
        assertTrue(saved.contains("</resources>"));
    }

    @Test public void addingStringPreservesClosingResourcesTag() throws Exception {
        File file = new File(folder.getRoot(), "strings.xml");
        Files.write(file.toPath(), "<resources>\n</resources>\n".getBytes(StandardCharsets.UTF_8));
        StringResourceFileEditor.save(file, Collections.singletonMap("new_item", "value"));
        String saved = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(saved.contains("<string name=\"new_item\">value</string>"));
        assertTrue(saved.contains("</resources>"));
    }
}
