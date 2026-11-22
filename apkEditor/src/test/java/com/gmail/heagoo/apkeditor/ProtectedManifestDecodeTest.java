package com.gmail.heagoo.apkeditor;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** Regression coverage for protected APK manifests with a framework-free decode. */
public class ProtectedManifestDecodeTest {
    @Test
    public void keepsRequiredAndroidAttributeNamesWhenFrameworkIsUnavailable() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/fixtures/firefox-160-protected-manifest.axml");
        assertTrue("fixture is missing", input != null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XmlDecoder decoder = new XmlDecoder(null, null);
        decoder.setApkProtected(true);
        decoder.decodeManifest(input, output);

        String manifest = output.toString(StandardCharsets.UTF_8.name());
        assertTrue(manifest, manifest.contains(
                "<uses-feature android:name=\"android.hardware.camera\""));
        assertTrue(manifest, manifest.contains(
                "<action android:name=\"android.intent.action.SEND\""));
        assertTrue(manifest, manifest.contains(
                "android:name=\"org.mozilla.fenix.startupCrash.StartupCrashActivity\""));
        assertTrue(manifest, manifest.contains(
                "<activity-alias") && manifest.contains(
                "android:name=\"org.mozilla.firefox.AppCool\""));
        assertTrue(manifest, !manifest.contains("<uses-feature android:required="));
        assertTrue(manifest, !manifest.contains("<action />"));
    }
}
