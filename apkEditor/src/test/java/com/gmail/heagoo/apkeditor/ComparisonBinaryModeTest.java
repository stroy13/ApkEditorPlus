package com.gmail.heagoo.apkeditor;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComparisonBinaryModeTest {
    @Test
    public void knownBinaryEntriesNeverBecomeTextDiffs() {
        assertTrue(ComparisonBinaryMode.knownBinaryName("classes8.dex"));
        assertTrue(ComparisonBinaryMode.knownBinaryName("res/resources.arsc"));
        assertTrue(ComparisonBinaryMode.knownBinaryName("LIB/armeabi-v7a/libfoo.so"));
        assertTrue(ComparisonBinaryMode.knownBinaryName("payload.APK"));
        assertFalse(ComparisonBinaryMode.knownBinaryName("AndroidManifest.xml"));
        assertFalse(ComparisonBinaryMode.knownBinaryName("res/values/strings.xml"));
        assertFalse(ComparisonBinaryMode.knownBinaryName("smali/Main.smali"));
    }

    @Test
    public void onlyExplicitBinaryTextFailuresTriggerFallbackForUnknownNames() {
        assertTrue(ComparisonBinaryMode.isBinaryTextFailure("Не UTF-8: текстовый diff недоступен"));
        assertTrue(ComparisonBinaryMode.isBinaryTextFailure("Бинарный файл: текстовый diff недоступен"));
        assertFalse(ComparisonBinaryMode.isBinaryTextFailure("Текст больше 4 МБ — построчный просмотр недоступен"));
        assertFalse(ComparisonBinaryMode.isBinaryTextFailure("Слишком много строк для просмотра"));
        assertFalse(ComparisonBinaryMode.shouldUseBinary(
                "notes.txt", false, "Текст больше 4 МБ — построчный просмотр недоступен"));
        assertTrue(ComparisonBinaryMode.shouldUseBinary(
                "data", false, "Не UTF-8: текстовый diff недоступен"));
    }

    @Test
    public void decodedBinaryXmlMayFallBackWhenDecodingFails() {
        assertTrue(ComparisonBinaryMode.shouldUseBinary(
                "AndroidManifest.xml", true, "Не удалось декодировать бинарный XML"));
        assertFalse(ComparisonBinaryMode.shouldUseBinary(
                "AndroidManifest.xml", false, "Не удалось декодировать бинарный XML"));
    }
}
