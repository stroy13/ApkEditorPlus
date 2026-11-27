package com.gmail.heagoo.apkeditor;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Source-level guards for the Android-only comparison screen wiring. */
public class ComparisonScreenRegressionTest {
    private static Path root() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        assertNotNull("repository root not found", dir);
        return dir;
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(root().resolve(relative)), StandardCharsets.UTF_8);
    }

    @Test
    public void initialComparisonStateIsRenderedWithoutAControlChange() throws Exception {
        String src = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/ComparisonActivity.java");
        int listener = src.indexOf("this.mode.setOnItemSelectedListener");
        int initialRefresh = src.indexOf(
                "m158lambda$new$0$comgmailheagooapkeditorComparisonActivity();", listener);
        assertTrue("the mode listener must exist", listener >= 0);
        assertTrue("the initial status/filter state must not depend on Spinner callbacks",
                initialRefresh > listener);
        assertTrue("swap must be disabled until both sides are selected",
                src.contains("this.swap.setEnabled(this.left != null && this.right != null);"));
    }

    @Test
    public void diffDialogUsesManifestSyntaxAndPaletteForXmlEntries() throws Exception {
        String src = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/ComparisonDiffDialog.java");
        assertTrue(src.contains("isXmlEntry(entry.name)"));
        assertTrue(src.contains("ManifestSyntax.tokenize(line, state)"));
        assertTrue(src.contains("ManifestEditorPalette.forDark(dark)"));
        assertTrue(src.contains("new ForegroundColorSpan(prefixColor)"));
        assertTrue(src.contains("new ForegroundColorSpan(localColor)"));
        assertTrue(src.contains("new StyleSpan(Typeface.ITALIC)"));
        assertTrue(src.contains("SpannableStringBuilder display"));
        assertTrue(src.contains("isSmaliEntry(entry.name)"));
        assertTrue(src.contains("new ColorTheme(this.activity, \"smali.xml\")"));
        assertTrue(src.contains("SmaliSyntax.tokenize(line)"));
    }

    @Test
    public void failedTextReadCanReachPagedBinaryViewer() throws Exception {
        String src = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/ComparisonActivity.java");
        assertTrue(src.contains("ComparisonBinaryMode.shouldUseBinary(entry.name, decodedXml, textMessage)"));
        assertTrue(src.contains("BinaryDiffIndex.build(first, second, binary"));
        assertTrue(src.contains("emptyIfMissing("));
        assertTrue(src.contains("ComparisonCore.openInput(selected, entry, true)"));
        assertTrue(src.contains("new BinaryDiffDialog(this, entry.name, binary, changes).dialog()"));
        String binary = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/BinaryDiffDialog.java");
        assertTrue(binary.contains("BinaryDiffHex.format(bytes[i])"));
    }
}
