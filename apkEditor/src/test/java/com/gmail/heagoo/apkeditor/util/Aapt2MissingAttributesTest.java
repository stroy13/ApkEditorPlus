package com.gmail.heagoo.apkeditor.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Aapt2MissingAttributesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void extractsOnlyUndeclaredAttributesFromTheAppPackage() {
        String errors = "layout/a.xml: error: attribute glance_isTopLevelLayout (aka org.mozilla.firefox:glance_isTopLevelLayout) not found.\n"
                + "layout/b.xml: error: attribute customFlag (aka org.mozilla.firefox:customFlag) not found.\n"
                + "layout/c.xml: error: attribute label (aka android:label) not found.\n"
                + "layout/d.xml: error: attribute other (aka com.other:other) not found.";
        Set<String> expected = new LinkedHashSet<>(Arrays.asList("glance_isTopLevelLayout", "customFlag"));
        assertEquals(expected, Aapt2MissingAttributes.find(errors,
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"org.mozilla.firefox\"/>"));
    }

    @Test public void writesTemporaryDeclarationsAsResourceXml() throws Exception {
        Path res = folder.newFolder("res").toPath();
        Path file = Aapt2MissingAttributes.writeDeclarations(res,
                new LinkedHashSet<>(Arrays.asList("glance_isTopLevelLayout", "customFlag")));
        String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<attr name=\"glance_isTopLevelLayout\"/>"));
        assertTrue(xml.contains("<attr name=\"customFlag\"/>"));
    }

    @Test public void pipelineRetriesOnlyAfterLinkReportsMissingAppAttributes() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name", "").startsWith("Windows"));
        Path root = folder.newFolder("pipeline").toPath();
        Path res = Files.createDirectory(root.resolve("res"));
        Path values = Files.createDirectory(res.resolve("values"));
        Files.write(values.resolve("empty.xml"), "<resources/>".getBytes(StandardCharsets.UTF_8));
        Path manifest = root.resolve("AndroidManifest.xml");
        Files.write(manifest, "<manifest package=\"org.mozilla.firefox\"><application/></manifest>".getBytes(StandardCharsets.UTF_8));
        Path aapt = root.resolve("fake-aapt2.sh");
        String script = "#!/bin/sh\n"
                + "base=$(dirname \"$0\")\n"
                + "if [ \"$1\" = compile ]; then shift; for arg in \"$@\"; do case \"$arg\" in *aapt2-attrs-*) [ -f \"$arg/values/ae_aapt2_missing_attrs.xml\" ] && touch \"$base/attrs-seen\";; esac; done; touch \"$base/fake-flat\"; exit 0; fi\n"
                + "if [ ! -f \"$base/first-link\" ]; then echo 'layout/a.xml: error: attribute glance_isTopLevelLayout (aka org.mozilla.firefox:glance_isTopLevelLayout) not found.' >&2; touch \"$base/first-link\"; exit 1; fi\n"
                + "echo fake > \"$base/out.apk\"; exit 0\n";
        Files.write(aapt, script.getBytes(StandardCharsets.UTF_8));
        assertTrue(aapt.toFile().setExecutable(true));
        Path framework = Files.createFile(root.resolve("android.jar"));
        Path output = root.resolve("out.apk");

        Aapt2Pipeline.build(aapt.toString(), framework.toString(), res.toString(), manifest.toString(), output.toString());

        assertTrue(Files.isRegularFile(output));
        assertTrue("retry compile must include generated declarations", Files.exists(root.resolve("attrs-seen")));
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            assertTrue("temporary staged resources are cleaned up",
                    paths.noneMatch(p -> p.getFileName().toString().startsWith("aapt2-attrs-")));
        }
    }
}
