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
        assertTrue("swap must explain why it cannot run without both sides",
                src.contains("Выберите оба объекта, чтобы поменять их местами."));
        assertTrue("swap must be disabled until both sides are selected",
                src.contains("this.swap.setEnabled(!this.busy && this.left != null && this.right != null);"));
        assertTrue("control changes must preserve the same disabled state",
                src.contains("this.swap.setEnabled(enable && this.left != null && this.right != null);"));
        assertTrue("swap state must be refreshed when labels change",
                src.contains("updateSwapState();"));
        assertTrue("action buttons need a gap so their rounded lower borders are not overdrawn",
                src.contains("int actionGap = dp(4.0f);"));
        assertTrue(src.contains("compareParams.setMargins(0, 0, actionGap, 0);"));
        assertTrue(src.contains("swapParams.setMargins(0, 0, actionGap, 0);"));
        assertTrue(src.contains("comparison_action_background"));
        assertTrue(src.contains("actions.setClipChildren(false);"));
        assertTrue(src.contains("setBackgroundTintList(null)"));
        String actionBackground = read("apkEditor/src/main/res/drawable/comparison_action_background.xml");
        assertTrue(actionBackground.contains("android:state_enabled=\"false\""));
        assertTrue(actionBackground.contains("android:radius=\"10dp\""));
    }

    @Test
    public void largeSmaliEditorDoesNotSplitOrHighlightOnTheUiThread() throws Exception {
        String helper = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/TextEditBigListHelper.java");
        assertTrue(helper.contains("public static List<String> splitLines(String text)"));
        assertTrue(helper.contains("text.charAt(i) == '\\n'"));
        String loader = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/TextEditBigActivity.java");
        assertTrue(loader.contains("TextEditBigListHelper.splitLines(document.getText())"));
        assertTrue(loader.contains("textEditor.setText(this.loadedLines)"));
        String adapter = read("apkEditor/src/main/java/com/gmail/heagoo/apkeditor/TextEditBigListAdapter.java");
        assertTrue(adapter.contains("this.lines = text == null ? new ArrayList<>() : text;"));
        assertTrue(adapter.contains("newSingleThreadExecutor"));
        assertTrue(adapter.contains("doc.syntaxSnapshot(source"));
        assertTrue(adapter.contains("document.applySyntaxSnapshot"));
        assertTrue(!adapter.contains("document.syntaxHighlight(viewHolder.et)"));
        String document = read("_AndroidEditor/src/main/java/com/gmail/heagoo/neweditor/Document.java");
        assertTrue(document.contains("highlightSmaliSpannable(spannable)"));
        assertTrue(document.contains("public void applySyntaxSnapshot"));
        String controller = read("_AndroidEditor/src/main/java/com/gmail/heagoo/neweditor/AsyncSyntaxController.java");
        assertTrue(controller.contains("final CharSequence source = this.editor.getText();"));
        assertTrue(controller.contains("final String text = source == null ? \"\" : source.toString();"));
        assertTrue(!controller.contains("final String text = this.editor.getText().toString();"));
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
        String document = read("_AndroidEditor/src/main/java/com/gmail/heagoo/neweditor/Document.java");
        assertTrue(document.contains("SmaliSyntaxFallback.parse(text, colors, cancelled)"));
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
