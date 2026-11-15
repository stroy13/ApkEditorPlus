package com.gmail.heagoo.apkeditor.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects undeclared app attributes reported by AAPT2 and creates temporary declarations for retry. */
final class Aapt2MissingAttributes {
    private static final Pattern DIAGNOSTIC = Pattern.compile(
            "attribute\\s+[^\\r\\n]+?\\s+\\(aka\\s+([A-Za-z0-9_.]+):([A-Za-z_][A-Za-z0-9_.]*)\\)\\s+not found\\.");
    private static final Pattern PACKAGE = Pattern.compile(
            "<manifest\\b[^>]*\\bpackage\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");

    private Aapt2MissingAttributes() { }

    static Set<String> find(String diagnostic, String manifestXml) {
        String appPackage = packageName(manifestXml);
        Set<String> names = new LinkedHashSet<>();
        if (appPackage.isEmpty() || diagnostic == null) return names;
        Matcher matcher = DIAGNOSTIC.matcher(diagnostic);
        while (matcher.find()) {
            if (appPackage.equals(matcher.group(1))) names.add(matcher.group(2));
        }
        return names;
    }

    static Path writeDeclarations(Path stagedResources, Set<String> names) throws IOException {
        if (names == null || names.isEmpty()) throw new IOException("No missing app attributes to declare");
        Path values = stagedResources.resolve("values");
        Files.createDirectories(values);
        Path file = values.resolve("ae_aapt2_missing_attrs.xml");
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (String name : names) {
            if (!name.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
                throw new IOException("Unsafe AAPT2 attribute name: " + name);
            }
            xml.append("    <attr name=\"").append(name).append("\"/>\n");
        }
        xml.append("</resources>\n");
        Files.write(file, xml.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String packageName(String manifestXml) {
        if (manifestXml == null) return "";
        Matcher matcher = PACKAGE.matcher(manifestXml);
        return matcher.find() ? matcher.group(1) : "";
    }
}
