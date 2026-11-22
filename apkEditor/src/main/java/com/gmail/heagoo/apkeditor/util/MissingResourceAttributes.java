package com.gmail.heagoo.apkeditor.util;

import com.gmail.heagoo.common.CommandRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects undeclared app attributes reported by AAPT/AAPT2 and creates temporary declarations for retry. */
public final class MissingResourceAttributes {
    private static final Pattern AAPT2_DIAGNOSTIC = Pattern.compile(
            "attribute\\s+[^\\r\\n]+?\\s+\\(aka\\s+([A-Za-z0-9_.]+):([A-Za-z_][A-Za-z0-9_.]*)\\)\\s+not found\\.");
    private static final Pattern AAPT1_DIAGNOSTIC = Pattern.compile(
            "No resource identifier found for attribute ['\\\"]([^'\\\"]+)['\\\"] in package ['\\\"]([A-Za-z0-9_.]+)['\\\"]");
    private static final Pattern PACKAGE = Pattern.compile(
            "<manifest\\b[^>]*\\bpackage\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]");

    private MissingResourceAttributes() { }

    public static Set<String> find(String diagnostic, String manifestXml) {
        String appPackage = packageName(manifestXml);
        Set<String> names = new LinkedHashSet<>();
        if (diagnostic == null) return names;
        Set<String> diagnosticPackages = new LinkedHashSet<>();
        Matcher aapt2 = AAPT2_DIAGNOSTIC.matcher(diagnostic);
        while (aapt2.find()) {
            String pkg = aapt2.group(1);
            if (appPackage.equals(pkg)) names.add(aapt2.group(2));
            if (!"android".equals(pkg)) diagnosticPackages.add(pkg);
        }
        Matcher aapt1 = AAPT1_DIAGNOSTIC.matcher(diagnostic);
        while (aapt1.find()) {
            String pkg = aapt1.group(2);
            if (appPackage.equals(pkg)) names.add(aapt1.group(1));
            if (!"android".equals(pkg)) diagnosticPackages.add(pkg);
        }
        // Some protected/legacy decodes produce a manifest without a readable
        // package attribute. In that case only infer the app namespace when the
        // linker reports exactly one non-framework package; never guess among
        // multiple package namespaces.
        if (names.isEmpty() && appPackage.isEmpty() && diagnosticPackages.size() == 1) {
            String onlyPackage = diagnosticPackages.iterator().next();
            aapt2.reset();
            while (aapt2.find()) {
                if (onlyPackage.equals(aapt2.group(1))) names.add(aapt2.group(2));
            }
            aapt1.reset();
            while (aapt1.find()) {
                if (onlyPackage.equals(aapt1.group(2))) names.add(aapt1.group(1));
            }
        }
        return names;
    }

    public static Path stageForRetry(Path sourceResources, String diagnostic, String manifestXml) throws IOException {
        Set<String> names = find(diagnostic, manifestXml);
        if (names.isEmpty()) return null;
        Path stage = Files.createTempDirectory(sourceResources.toAbsolutePath().getParent(), "aapt-missing-attrs-");
        try {
            Aapt2DummyResources.stage(sourceResources, stage, java.util.Collections.<Path, byte[]>emptyMap());
            writeDeclarations(stage, names);
            return stage;
        } catch (IOException | RuntimeException failure) {
            try { Aapt2DummyResources.deleteTree(stage); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static void deleteStage(Path stage) throws IOException {
        if (stage != null) Aapt2DummyResources.deleteTree(stage);
    }

    /** Run legacy aapt, then retry with inferred app attributes only for that specific linker diagnostic. */
    public static boolean runLegacyAaptWithRetry(CommandRunner runner, String[] command,
                                                  String resourceDirectory, String manifestPath,
                                                  ApkBuildPipeline.Logger logger) throws IOException {
        boolean success = runner.runCommand(command, null, null, 300000, true);
        if (success) return true;
        String diagnostics = String.valueOf(runner.getStdError()) + "\n" + String.valueOf(runner.getStdOut());
        String manifestXml = new String(Files.readAllBytes(new java.io.File(manifestPath).toPath()), StandardCharsets.UTF_8);
        Path stage = stageForRetry(new java.io.File(resourceDirectory).toPath(), diagnostics, manifestXml);
        if (stage == null) return false;
        try {
            String[] retry = command.clone();
            boolean replaced = false;
            for (int i = 0; i + 1 < retry.length; i++) {
                if ("-S".equals(retry[i])) {
                    retry[i + 1] = stage.toString();
                    replaced = true;
                    break;
                }
            }
            if (!replaced) throw new IOException("Legacy AAPT command has no resource-directory argument");
            if (logger != null) logger.log("AAPT: undeclared app attributes " + find(diagnostics, manifestXml)
                    + "; retrying with temporary declarations");
            return runner.runCommand(retry, null, null, 300000, true);
        } finally {
            deleteStage(stage);
        }
    }

    public static Path writeDeclarations(Path stagedResources, Set<String> names) throws IOException {
        if (names == null || names.isEmpty()) throw new IOException("No missing app attributes to declare");
        Path values = stagedResources.resolve("values");
        Files.createDirectories(values);
        Path file = values.resolve("ae_missing_attrs.xml");
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
