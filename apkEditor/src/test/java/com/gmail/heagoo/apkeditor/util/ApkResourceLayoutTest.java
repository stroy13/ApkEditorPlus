package com.gmail.heagoo.apkeditor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApkResourceLayoutTest {
    @Test
    public void acceptsNormalAndroidResourceEntries() {
        assertTrue(ApkResourceLayout.isStandardResourcePath("res/values/strings.xml"));
        assertTrue(ApkResourceLayout.isStandardResourcePath("res/drawable-hdpi/icon.9.png"));
        assertTrue(ApkResourceLayout.isStandardResourcePath("res/raw/payload.bin"));
    }

    @Test
    public void rejectsProtectorDecoyResourceTrees() {
        assertFalse(ApkResourceLayout.isStandardResourcePath("assets/resources.xml"));
        assertFalse(ApkResourceLayout.isStandardResourcePath("res/values/arrays.xml/AndroidManifest.xml"));
        assertFalse(ApkResourceLayout.isStandardResourcePath("res/values/attrs.xml/\\\\/.xml"));
        assertFalse(ApkResourceLayout.isStandardResourcePath("res/not-a-resource-dir/file.xml"));
    }
}
