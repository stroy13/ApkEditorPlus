package com.gmail.heagoo.apkeditor.ce;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Framework resource-map names must remain readable when aapt omits string names. */
public class ManifestParserAttrNameTest {
    @Test
    public void resolvesSdkAttributesFromFrameworkResourceIds() {
        assertEquals("minSdkVersion", ManifestParser.getNameFromAttr(0x0101020c));
        assertEquals("targetSdkVersion", ManifestParser.getNameFromAttr(0x01010270));
        assertEquals("maxSdkVersion", ManifestParser.getNameFromAttr(0x01010271));
    }
}
