package com.gmail.heagoo.apkeditor.translate;

import static org.junit.Assert.*;
import org.junit.Test;

public class ResourceLanguageTagTest {
    @Test public void convertsExplicitLocaleWithoutInventingDefault() {
        assertEquals("en", ResourceLanguageTag.fromQualifier("-en"));
        assertEquals("pt-BR", ResourceLanguageTag.fromQualifier("-pt-rBR"));
        assertEquals("en-XC", ResourceLanguageTag.fromQualifier("-en-rXC"));
        assertEquals("en", ResourceLanguageTag.languageOnlyFromQualifier("-en-rXC"));
        assertEquals("ru", ResourceLanguageTag.languageOnlyFromQualifier("-ru"));
        assertNull(ResourceLanguageTag.languageOnlyFromQualifier("values"));
        assertNull(ResourceLanguageTag.fromQualifier(""));
        assertNull(ResourceLanguageTag.fromQualifier(null));
        assertNull(ResourceLanguageTag.fromQualifier("-night"));
        assertNull(ResourceLanguageTag.fromQualifier("-en-land"));
    }
}
