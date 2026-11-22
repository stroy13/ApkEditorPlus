package com.gmail.heagoo.apkeditor.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ManifestResourceReferenceNormalizerTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void replacesProtectedNumericReferencesFromPublicXml() throws Exception {
        File res = folder.newFolder("res");
        File values = new File(res, "values");
        assertTrue(values.mkdirs());
        Files.write(new File(values, "public.xml").toPath(), (
                "<resources>\n"
                        + "<public type=\"drawable\" name=\"noname_0x7f100000\" id=\"0x7f100000\" />\n"
                        + "<public type=\"style\" name=\"noname_0x7f1501d5\" id=\"0x7f1501d5\" />\n"
                        + "<public type=\"bool\" name=\"back_enabled\" id=\"0x7f050005\" />\n"
                        + "<public type=\"xml\" name=\"data_rules\" id=\"0x7f170008\" />\n"
                        + "</resources>\n").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(values, "bools.xml").toPath(), (
                "<resources><bool name=\"back_enabled\">true</bool></resources>\n")
                .getBytes(StandardCharsets.UTF_8));
        File manifest = folder.newFile("AndroidManifest.xml");
        Files.write(manifest.toPath(), (
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                        + "<application android:icon=\"@0x7f100000\" android:theme=\"@0x7f1501d5\" "
                        + "android:enableOnBackInvokedCallback=\"@0x7f050005\" "
                        + "android:dataExtractionRules=\"@0x7f170008\"/></manifest>")
                .getBytes(StandardCharsets.UTF_8));

        assertTrue(ManifestResourceReferenceNormalizer.normalize(manifest, res, null));
        String normalized = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
        assertTrue(normalized.contains("android:icon=\"@drawable/noname_0x7f100000\""));
        assertTrue(normalized.contains("android:theme=\"@style/noname_0x7f1501d5\""));
        assertTrue(normalized.contains("android:enableOnBackInvokedCallback=\"true\""));
        assertTrue(normalized.contains("android:dataExtractionRules=\"@xml/data_rules\""));
    }

    @Test
    public void leavesUnknownReferencesUntouched() throws Exception {
        File res = folder.newFolder("res");
        File values = new File(res, "values");
        assertTrue(values.mkdirs());
        Files.write(new File(values, "public.xml").toPath(),
                "<resources/>\n".getBytes(StandardCharsets.UTF_8));
        File manifest = folder.newFile("AndroidManifest.xml");
        Files.write(manifest.toPath(), "<manifest android:icon=\"@0x7f100000\"/>"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(!ManifestResourceReferenceNormalizer.normalize(manifest, res, null));
        assertEquals("<manifest android:icon=\"@0x7f100000\"/>",
                new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
    }
}
