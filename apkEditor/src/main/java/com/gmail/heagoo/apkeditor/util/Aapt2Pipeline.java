package com.gmail.heagoo.apkeditor.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brut.androlib.res.decoder.AndroidManifestAttributeNames;

/* loaded from: /tmp/apkx/classes9.dex */
public final class Aapt2Pipeline {
    private Aapt2Pipeline() {
    }

    public static void build(String aapt2, String framework, String resDir, String manifest, String output) throws IOException, InterruptedException {
        build(aapt2, framework, resDir, manifest, output, null);
    }

    public static void build(String aapt2, String framework, String resDir, String manifest, String output,
                             ApkBuildPipeline.Logger logger) throws IOException, InterruptedException {
        File out = new File(output);
        File flat = new File(output + ".aapt2-flats.zip");
        if (flat.exists() && !flat.delete()) {
            throw new IOException("Cannot remove old AAPT2 intermediate");
        }
        if (out.exists() && !out.delete()) {
            throw new IOException("Cannot remove old AAPT2 output");
        }
        Path staged = null;
        Path missingAttributeStage = null;
        Path stagedManifest = null;
        try {
            Path original = new File(resDir).toPath().toAbsolutePath();
            long repairStart = System.nanoTime();
            Map<Path, byte[]> repairs = Aapt2DummyResources.findRepairs(original, logger);
            long aliasesStart = System.nanoTime();
            Map<String, String> aliases = Aapt2SyntheticNames.find(original);
            if (logger != null) logger.log("AAPT2 synthetic name scan: " + elapsed(aliasesStart) + " ms; aliases=" + aliases.size());
            String compileDir = resDir;
            String linkManifest = manifest;
            if (!repairs.isEmpty() || !aliases.isEmpty()) {
                staged = Files.createTempDirectory(out.getAbsoluteFile().getParentFile().toPath(), "aapt2-res-");
                long copyStart = System.nanoTime();
                Aapt2DummyResources.stage(original, staged, repairs);
                if (logger != null) logger.log("AAPT2 temporary resource copy: " + elapsed(copyStart) + " ms");
                if (!aliases.isEmpty()) {
                    long rewriteStart = System.nanoTime();
                    Aapt2SyntheticNames.rewriteStage(staged, aliases);
                    if (logger != null) logger.log("AAPT2 synthetic name rewrite: " + elapsed(rewriteStart) + " ms");
                    String originalManifest = new String(Files.readAllBytes(new File(manifest).toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    String rewrittenManifest = Aapt2SyntheticNames.rewriteXml(originalManifest, aliases);
                    if (!originalManifest.equals(rewrittenManifest)) {
                        stagedManifest = Files.createTempFile(out.getAbsoluteFile().getParentFile().toPath(), "aapt2-manifest-", ".xml");
                        Files.write(stagedManifest, rewrittenManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        linkManifest = stagedManifest.toString();
                    }
                    if (logger != null) logger.log("AAPT2 temporary synthetic drawable aliases: " + aliases.size());
                }
                compileDir = staged.toString();
            }
            String manifestBeforeRepair = new String(Files.readAllBytes(new File(linkManifest).toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            String manifestAfterRepair = repairSyntheticFrameworkAttributeNames(manifestBeforeRepair);
            manifestAfterRepair = removeEmptyAndroidAttributes(manifestAfterRepair);
            if (!manifestBeforeRepair.equals(manifestAfterRepair)) {
                if (stagedManifest == null) {
                    stagedManifest = Files.createTempFile(out.getAbsoluteFile().getParentFile().toPath(),
                            "aapt2-manifest-", ".xml");
                }
                Files.write(stagedManifest, manifestAfterRepair.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                linkManifest = stagedManifest.toString();
                if (logger != null) logger.log("AAPT2 manifest: restored synthetic Android attribute names");
            }
            if (logger != null) logger.log("AAPT2 resource scan and staging: " + elapsed(repairStart) + " ms; repaired files=" + repairs.size());
            try {
                runTimed(new String[]{aapt2, "compile", "--legacy", "--no-crunch", "--dir", compileDir, "-o", flat.getAbsolutePath()}, logger);
            } catch (IOException e) {
                if (staged == null || e.getMessage() == null) throw e;
                throw new IOException(e.getMessage().replace(staged.toString(), original.toString()), e);
            }
            String[] linkArgs = {aapt2, "link", "--auto-add-overlay", "--no-version-vectors", "-I", framework, "--manifest", linkManifest, "-o", out.getAbsolutePath(), flat.getAbsolutePath()};
            try {
                runTimed(linkArgs, logger);
            } catch (IOException strictFailure) {
                String manifestXml = new String(Files.readAllBytes(new File(manifest).toPath()), java.nio.charset.StandardCharsets.UTF_8);
                Set<String> missingAttributes = MissingResourceAttributes.find(strictFailure.getMessage(), manifestXml);
                if (!missingAttributes.isEmpty()) {
                    if (out.exists() && !out.delete()) throw new IOException("Cannot remove failed AAPT2 output", strictFailure);
                    missingAttributeStage = Files.createTempDirectory(out.getAbsoluteFile().getParentFile().toPath(), "aapt2-attrs-");
                    Aapt2DummyResources.stage(new File(compileDir).toPath(), missingAttributeStage, Collections.<Path, byte[]>emptyMap());
                    MissingResourceAttributes.writeDeclarations(missingAttributeStage, missingAttributes);
                    if (logger != null) logger.log("AAPT2: undeclared app attributes " + missingAttributes + "; adding temporary declarations and retrying link");
                    if (flat.exists() && !flat.delete()) throw new IOException("Cannot remove failed AAPT2 intermediates", strictFailure);
                    try {
                        runTimed(new String[]{aapt2, "compile", "--legacy", "--no-crunch", "--dir", missingAttributeStage.toString(), "-o", flat.getAbsolutePath()}, logger);
                    } catch (IOException compileFailure) {
                        throw new IOException(compileFailure.getMessage().replace(missingAttributeStage.toString(), new File(resDir).getAbsolutePath()), compileFailure);
                    }
                    linkArgs[linkArgs.length - 1] = flat.getAbsolutePath();
                    compileDir = missingAttributeStage.toString();
                    try {
                        runTimed(linkArgs, logger);
                    } catch (IOException retryFailure) {
                        retryLinkWithManifestCompatibility(retryFailure, linkArgs, out, logger);
                    }
                } else {
                    retryLinkWithManifestCompatibility(strictFailure, linkArgs, out, logger);
                }
            }
            if (!aliases.isEmpty()) {
                long restoreStart = System.nanoTime();
                Aapt2SyntheticNames.restoreApk(out, aliases);
                if (logger != null) logger.log("AAPT2 synthetic drawable names restored in resource table: " + elapsed(restoreStart) + " ms");
            }
            if (!out.isFile() || out.length() == 0) {
                throw new IOException("AAPT2 produced no output");
            }
            if (flat.exists()) {
                flat.delete();
            }
        } finally {
            if (staged != null) Aapt2DummyResources.deleteTree(staged);
            if (missingAttributeStage != null) Aapt2DummyResources.deleteTree(missingAttributeStage);
            if (stagedManifest != null) Files.deleteIfExists(stagedManifest);
            if (flat.exists()) {
                flat.delete();
            }
        }
    }

    private static void retryLinkWithManifestCompatibility(IOException failure, String[] linkArgs,
                                                              File output, ApkBuildPipeline.Logger logger)
            throws IOException, InterruptedException {
        if (!isKnownOldManifestValidationFailure(failure.getMessage())) throw failure;
        if (output.exists() && !output.delete()) throw new IOException("Cannot remove failed AAPT2 output", failure);
        if (logger != null) logger.log("AAPT2: older manifest validator rejected modern elements; retrying with validation warnings. Original error: " + failure.getMessage());
        String[] retry = new String[linkArgs.length + 1];
        System.arraycopy(linkArgs, 0, retry, 0, linkArgs.length);
        retry[linkArgs.length] = "--warn-manifest-validation";
        String warnings = runTimed(retry, logger);
        if (!safeRetryWarnings(warnings)) {
            if (output.exists()) output.delete();
            throw new IOException("AAPT2 manifest validation reported additional problems: " + warnings, failure);
        }
    }

    private static final Pattern SYNTHETIC_FRAMEWORK_ATTRIBUTE = Pattern.compile(
            "android:attr_0x([0-9a-fA-F]{8})");
    private static final Pattern EMPTY_ANDROID_ATTRIBUTE = Pattern.compile(
            "\\s+android:[A-Za-z_][A-Za-z0-9_.-]*\\s*=\\s*(['\\\"])(?:\\s*)\\1");

    static String removeEmptyAndroidAttributes(String manifestXml) {
        return EMPTY_ANDROID_ATTRIBUTE.matcher(manifestXml).replaceAll("");
    }

    static String repairSyntheticFrameworkAttributeNames(String manifestXml) {
        Matcher matcher = SYNTHETIC_FRAMEWORK_ATTRIBUTE.matcher(manifestXml);
        StringBuffer repaired = new StringBuffer(manifestXml.length());
        while (matcher.find()) {
            String name = AndroidManifestAttributeNames.nameFor(
                    (int) Long.parseLong(matcher.group(1), 16));
            if (name == null) continue;
            matcher.appendReplacement(repaired, Matcher.quoteReplacement("android:" + name));
        }
        matcher.appendTail(repaired);
        return repaired.toString();
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1000000L;
    }

    static boolean isKnownOldManifestValidationFailure(String message) {
        if (message == null || message.length() >= 131072) return false;
        boolean found = false;
        for (String line : message.split("\n")) {
            if (!line.contains("error:")) continue;
            if (!knownManifestDiagnostic(line, "error")) return false;
            found = true;
        }
        return found;
    }

    private static boolean knownManifestDiagnostic(String line, String level) {
        return line.contains(level + ": unexpected element <queries> found in <manifest>.")
                || line.contains(level + ": unexpected element <profileable> found in <manifest><application>.")
                || line.contains(level + ": unexpected element <property> found in <manifest><application>.")
                || line.contains(level + ": unexpected element <property> found in <manifest><application><service>.")
                || line.contains(level + ": unexpected element <property> found in <manifest><application><activity>.")
                || line.contains(level + ": unexpected element <property> found in <manifest><application><receiver>.")
                || line.contains(level + ": unexpected element <property> found in <manifest><application><provider>.");
    }

    static boolean safeRetryWarnings(String message) {
        if (message == null || message.length() >= 131072) return false;
        for (String line : message.split("\n")) {
            if (line.contains("error:")) return false;
            if (line.contains("warning: unexpected element") && !knownManifestDiagnostic(line, "warning")) return false;
        }
        return true;
    }

    private static String runTimed(String[] args, ApkBuildPipeline.Logger logger) throws IOException, InterruptedException {
        long started = System.nanoTime();
        try {
            String output = run(args);
            if (logger != null && !output.trim().isEmpty()) logger.log("AAPT2 " + args[1] + " output: " + output);
            return output;
        } finally {
            if (logger != null) logger.log("AAPT2 " + args[1] + ": " + elapsed(started) + " ms");
        }
    }

    private static String run(String[] args) throws IOException, InterruptedException {
        final Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        final StringBuilder msg = new StringBuilder();
        Thread drain = new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                Aapt2Pipeline.lambda$run$0(p, msg);
            }
        }, "aapt2-output");
        drain.setDaemon(true);
        drain.start();
        try {
            boolean finished = p.waitFor(300L, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                drain.join(1000L);
                throw new IOException("AAPT2 timed out");
            }
            drain.join(1000L);
            synchronized (msg) {
                if (p.exitValue() != 0) throw new IOException("AAPT2 " + args[1] + ": " + msg.toString());
                return msg.toString();
            }
        } catch (InterruptedException interrupted) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    static void lambda$run$0(Process p, StringBuilder msg) {
        try {
            Reader r = new InputStreamReader(p.getInputStream(), "UTF-8");
            try {
                char[] buf = new char[4096];
                while (true) {
                    int n = r.read(buf);
                    if (n == -1) {
                        r.close();
                        return;
                    } else {
                        synchronized (msg) {
                            if (msg.length() < 131072) {
                                msg.append(buf, 0, Math.min(n, 131072 - msg.length()));
                            }
                        }
                    }
                }
            } finally {
            }
        } catch (IOException e) {
        }
    }
}
