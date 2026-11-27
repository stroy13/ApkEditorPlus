package com.gmail.heagoo.apkeditor;

import java.util.Locale;

/**
 * Decides when the comparison entry viewer must use the byte index instead of
 * treating the entry as UTF-8 text.
 *
 * <p>The UTF-8 reader remains the source of truth for files with no known
 * binary suffix. Its explicit binary errors also cover extensionless binary
 * entries. Known formats are selected before reading so a valid-looking byte
 * sequence in a DEX or resource table can never accidentally become a text
 * diff.</p>
 */
public final class ComparisonBinaryMode {
    private ComparisonBinaryMode() {
    }

    public static boolean knownBinaryName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".dex")
                || lower.endsWith(".odex")
                || lower.endsWith(".vdex")
                || lower.endsWith(".oat")
                || lower.endsWith(".art")
                || lower.endsWith(".arsc")
                || lower.endsWith(".apk")
                || lower.endsWith(".apks")
                || lower.endsWith(".zip")
                || lower.endsWith(".jar")
                || lower.endsWith(".class")
                || lower.endsWith(".so")
                || lower.endsWith(".bin");
    }

    public static boolean isBinaryTextFailure(String message) {
        return message != null
                && (message.startsWith("Не UTF-8:")
                || message.startsWith("Бинарный файл:"));
    }

    public static boolean shouldUseBinary(String name, boolean binaryXml, String message) {
        return knownBinaryName(name) || binaryXml || isBinaryTextFailure(message);
    }
}
