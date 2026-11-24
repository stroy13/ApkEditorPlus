package com.gmail.heagoo.apkeditor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import com.gmail.heagoo.apkeditor.SettingActivity;
import com.gmail.heagoo.apkeditor.ce.ManifestInfo;
import com.gmail.heagoo.apkeditor.ce.ManifestParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Modern, fail-fast APK signing/assembly shared by quick edit and full compose. */
public final class SignHelper {

    private SignHelper() {
    }

    public static void sign(Context ctx, String srcApkPath, String targetApkPath,
                            Map<String, String> replacedFiles,
                            Map<String, String> addedFiles,
                            Set<String> deletedFiles) throws Exception {
        sign(ctx, srcApkPath, targetApkPath, replacedFiles, addedFiles, deletedFiles, null, null);
    }

    public static void sign(Context ctx, String srcApkPath, String targetApkPath,
                            Map<String, String> replacedFiles,
                            Map<String, String> addedFiles,
                            Set<String> deletedFiles,
                            ApkBuildPipeline.Progress progress,
                            ApkBuildPipeline.Cancellation cancellation) throws Exception {
        sign(ctx, srcApkPath, targetApkPath, replacedFiles, addedFiles, deletedFiles, progress, cancellation, null);
    }

    public static void sign(Context ctx, String srcApkPath, String targetApkPath,
                            Map<String, String> replacedFiles,
                            Map<String, String> addedFiles,
                            Set<String> deletedFiles,
                            ApkBuildPipeline.Progress progress,
                            ApkBuildPipeline.Cancellation cancellation,
                            ApkBuildPipeline.Logger logger) throws Exception {
        // When the caller does not provide a log, still write a visible build-log.txt
        // next to the target APK so every signing/assembly path is diagnosable.
        BuildLog ownLog = null;
        ApkBuildPipeline.Logger out = logger;
        if (out == null) {
            try {
                File target = new File(targetApkPath);
                File parent = target.getAbsoluteFile().getParentFile();
                if (parent != null) {
                    ownLog = new BuildLog(new File(parent, "build-log.txt"), BuildLog.languageFor(ctx));
                    out = ownLog;
                }
            } catch (IOException ignored) {
                out = null;
            }
        }
        try {
            signInternal(ctx, srcApkPath, targetApkPath, replacedFiles, addedFiles, deletedFiles,
                    progress, cancellation, out);
            if (out != null) {
                out.log("SignHelper finished successfully");
            }
        } catch (Exception e) {
            if (out != null) {
                out.log("SignHelper failed: " + e);
            }
            throw e;
        } finally {
            if (ownLog != null) {
                ownLog.close();
            }
        }
    }

    private static void signInternal(Context ctx, String srcApkPath, String targetApkPath,
                                     Map<String, String> replacedFiles,
                                     Map<String, String> addedFiles,
                                     Set<String> deletedFiles,
                                     ApkBuildPipeline.Progress progress,
                                     ApkBuildPipeline.Cancellation cancellation,
                                     ApkBuildPipeline.Logger out) throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String keyName = prefs.getString("SignApkWith", "testkey");
        if (out != null) {
            out.log("SignHelper: source=" + srcApkPath + ", target=" + targetApkPath);
            out.log("Signing key preference: " + keyName);
        }
        PrivateKey privateKey;
        X509Certificate certificate;
        if ("customkey".equals(keyName)) {
            String privatePath = prefs.getString(SettingActivity.STR_PRIVATEKEYPATH, "");
            String publicPath = prefs.getString(SettingActivity.STR_PUBLICKEYPATH, "");
            if (privatePath.isEmpty() || publicPath.isEmpty()) {
                throw new IOException("Custom signing key is not configured");
            }
            try (InputStream keyInput = new BufferedInputStream(new FileInputStream(privatePath));
                 InputStream certInput = new BufferedInputStream(new FileInputStream(publicPath))) {
                privateKey = readPrivateKey(keyInput);
                certificate = readCertificate(certInput);
            }
        } else {
            if (!"editorkey".equals(keyName)) {
                keyName = "testkey";
            }
            try (InputStream keyInput = ctx.getAssets().open(keyName + ".pk8");
                 InputStream certInput = ctx.getAssets().open(keyName + ".x509.pem")) {
                privateKey = readPrivateKey(keyInput);
                certificate = readCertificate(certInput);
            }
        }

        ManifestInfo manifest = readManifest(new File(srcApkPath));
        // Use the manifest that will actually be packaged, not the source one.
        ManifestInfo effectiveManifest = manifest;
        if (replacedFiles != null && replacedFiles.containsKey("AndroidManifest.xml")) {
            try (InputStream input = new BufferedInputStream(new FileInputStream(replacedFiles.get("AndroidManifest.xml")))) {
                ManifestParser parser = new ManifestParser(input);
                parser.parse();
                effectiveManifest = parser.getManifestInfo();
                if (effectiveManifest == null) throw new IOException("Cannot parse replacement manifest");
            }
            if (out != null) out.log("Effective replacement manifest: " + ManifestParser.describe(effectiveManifest));
        }
        if (out != null) {
            out.log("Source manifest: " + ManifestParser.describe(manifest));
        }
        String schemes = prefs.getString("SignSchemes", "v123");
        boolean v1 = schemes.contains("1");
        boolean v2 = schemes.contains("2");
        boolean v3 = schemes.contains("3");
        boolean v4 = schemes.contains("4");
        if (!v1 && !v2 && !v3) {
            v1 = true;
            v2 = true;
            v3 = true;
        }
        int minSdk = effectiveManifest.minSdkVersion > 0 ? effectiveManifest.minSdkVersion : 26;
        int targetSdk = effectiveManifest.targetSdkVersion > 0 ? effectiveManifest.targetSdkVersion : minSdk;
        int compressionLevel = SettingActivity.getCompressionLevel(ctx);
        if (out != null) {
            out.log("Signature schemes preference: " + schemes + " -> v1=" + v1 + ", v2=" + v2
                    + ", v3=" + v3 + ", v4=" + v4 + "; compressionLevel=" + compressionLevel);
        }
        ApkBuildPipeline.SigningOptions options = new ApkBuildPipeline.SigningOptions(
                privateKey, certificate, minSdk, targetSdk, v1, v2, v3, v4, compressionLevel,
                effectiveManifest.extractNativeLibs);
        ApkBuildPipeline.build(new File(srcApkPath), new File(targetApkPath),
                replacedFiles, addedFiles, deletedFiles, options, progress, cancellation, out);
        try {
            validateOutputWithAndroid(ctx, new File(targetApkPath), effectiveManifest, out);
        } catch (Exception e) {
            if (out != null) {
                out.log("Android package parser rejected the generated APK; preserving signed output for diagnosis: " + e);
            }
            // Keep the signed artifact so the user can inspect it or attempt installation.
            // ZIP integrity and apksig verification have already passed; this independent
            // framework parser check is inconclusive when it returns null without a cause.
            throw e;
        }
    }

    /**
     * Final gate: ask Android's own package parser to read the generated APK.
     * apksig + chunk validation do not catch every semantic manifest problem that
     * PackageInstaller rejects with "cannot parse package".
     */
    private static void validateOutputWithAndroid(Context ctx, File apk,
                                                   ManifestInfo expected,
                                                   ApkBuildPipeline.Logger out)
            throws Exception {
        PackageManager pm = ctx.getPackageManager();
        // Request component arrays so this check covers the same package metadata
        // that Android will use at install time. GET_META_DATA is intentionally not
        // requested: loading arbitrary application metadata is optional here and
        // should not turn a valid package-identity check into a resource lookup.
        int flags = PackageManager.GET_ACTIVITIES
                | PackageManager.GET_SERVICES
                | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS;
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        boolean identityOnlyFallback = false;
        if (!hasPackageIdentity(info)) {
            // PackageInfo generation can fail independently of archive parsing when
            // one requested component array contains metadata this platform cannot
            // materialize. Retry without optional arrays so the final gate still
            // proves package identity instead of reporting a false parser failure.
            if (out != null) {
                out.log("Android package parser returned no component-rich PackageInfo; "
                        + "retrying with identity-only flags");
            }
            PackageInfo identity = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (hasPackageIdentity(identity)) {
                info = identity;
                identityOnlyFallback = true;
                if (out != null) {
                    out.log("Android package parser identity-only retry succeeded");
                }
            }
        }
        if (!hasPackageIdentity(info)) {
            ManifestInfo manifest = readManifest(apk);
            String splitDiagnostic = splitArchiveDiagnostic(manifest);
            if (splitDiagnostic != null) throw new IOException(splitDiagnostic + ": " + apk);
            String details = ManifestParser.describe(manifest);
            String assetDiagnostic = assetManagerDiagnostic(apk);
            String legacyDiagnostic = legacyPackageParserDiagnostic(apk);
            if (out != null) {
                out.log("Android package parser rejected generated APK; parsed output manifest: " + details);
                out.log("Framework parser diagnostics: " + legacyDiagnostic
                        + "; asset manager: " + assetDiagnostic);
            }
            throw new IOException("Android package parser cannot read generated APK; manifest="
                    + details + "; framework=" + legacyDiagnostic + "; assets=" + assetDiagnostic
                    + "; signature and ZIP structure passed, but Android installability is unverified: " + apk);
        }
        if (expected != null && expected.packageName != null
                && !expected.packageName.equals(info.packageName)) {
            throw new IOException("Generated package mismatch: expected=" + expected.packageName
                    + ", framework=" + info.packageName);
        }
        long versionCode = android.os.Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        if (expected != null && expected.versionCode >= 0
                && versionCode != Integer.toUnsignedLong(expected.versionCode)) {
            throw new IOException("Generated versionCode mismatch: expected="
                    + Integer.toUnsignedLong(expected.versionCode) + ", framework=" + versionCode);
        }
        if (expected != null && expected.versionName != null
                && !expected.versionName.equals(info.versionName)) {
            throw new IOException("Generated versionName mismatch: expected=" + expected.versionName
                    + ", framework=" + info.versionName);
        }
        if (expected != null && expected.applicationCls != null
                && info.applicationInfo.className != null) {
            String expectedApplication = resolveComponentName(expected.packageName, expected.applicationCls);
            String frameworkApplication = resolveComponentName(info.packageName, info.applicationInfo.className);
            if (!expectedApplication.equals(frameworkApplication)) {
                throw new IOException("Generated application mismatch: expected=" + expectedApplication
                        + ", framework=" + frameworkApplication);
            }
        }
        if (out != null) {
            out.log("Android package parser OK: package=" + info.packageName
                    + ", metadataMode=" + (identityOnlyFallback ? "identity-only" : "components")
                    + ", versionCode=" + versionCode
                    + ", versionName=" + info.versionName
                    + ", application=" + info.applicationInfo.className
                    + ", components={activities=" + count(info.activities)
                    + ", services=" + count(info.services)
                    + ", receivers=" + count(info.receivers)
                    + ", providers=" + count(info.providers) + "}");
        }
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) {
                throw new IOException("Generated APK has no AndroidManifest.xml");
            }
            try (InputStream input = zip.getInputStream(entry)) {
                ManifestParser parser = new ManifestParser(input);
                parser.parse();
                ManifestInfo parsed = parser.getManifestInfo();
                if (parsed == null || parsed.packageName == null) {
                    throw new IOException("Generated manifest has no package name");
                }
                ApkStructureValidator.validatePackageName(parsed.packageName);
                if (!parsed.packageName.equals(info.packageName)) {
                    throw new IOException("Manifest package mismatch: binary=" + parsed.packageName
                            + ", framework=" + info.packageName);
                }
                if (out != null) {
                    out.log("Output manifest: " + ManifestParser.describe(parsed));
                }
            }
        }
    }

    /**
     * Probe the same native asset path that PackageParser uses. This is deliberately
     * diagnostic only: a successful probe does not replace PackageManager's final
     * identity check, but a failure usually identifies a bad resources.arsc/AXML
     * archive rather than a component-metadata problem.
     */
    private static String assetManagerDiagnostic(File apk) {
        AssetManager assets = new AssetManager();
        try {
            int cookie = assets.addAssetPath(apk.getAbsolutePath());
            if (cookie == 0) {
                return "addAssetPath returned 0";
            }
            XmlResourceParser parser = null;
            try {
                parser = assets.openXmlResourceParser(cookie, "AndroidManifest.xml");
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    // Consume the parser so malformed later chunks are not hidden.
                }
                return "AssetManager opened AndroidManifest.xml";
            } finally {
                if (parser != null) {
                    parser.close();
                }
            }
        } catch (Throwable error) {
            return "AssetManager failed: " + throwableSummary(error);
        } finally {
            try {
                assets.close();
            } catch (Throwable ignored) {
                // Diagnostic cleanup must not hide the original parse result.
            }
        }
    }

    /**
     * Older Android releases expose the legacy parser as a hidden class. When it is
     * reflectively accessible, its PackageParserException contains the actual parse
     * error that getPackageArchiveInfo intentionally discards. Hidden-API blocking is
     * reported as a diagnostic string and is otherwise harmless.
     */
    private static String legacyPackageParserDiagnostic(File apk) {
        try {
            Class<?> parserClass = Class.forName("android.content.pm.PackageParser");
            Object parser = parserClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method parse;
            Object result;
            try {
                parse = parserClass.getMethod("parsePackage", File.class, int.class, boolean.class);
                result = parse.invoke(parser, apk, 0, false);
            } catch (NoSuchMethodException missingModernMethod) {
                parse = parserClass.getMethod("parseMonolithicPackage", File.class, int.class);
                result = parse.invoke(parser, apk, 0);
            }
            return result == null ? "legacy parser returned null" : "legacy parser accepted archive";
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            return "legacy parser rejected: " + throwableSummary(cause);
        } catch (Throwable error) {
            return "legacy parser unavailable: " + throwableSummary(error);
        }
    }

    private static String throwableSummary(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        String text = error.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
        try {
            java.lang.reflect.Field field = error.getClass().getField("error");
            Object code = field.get(error);
            text += " (code=" + code + ")";
        } catch (Throwable ignored) {
            // Most failures do not expose a numeric package-parser code.
        }
        return text;
    }

    private static boolean hasPackageIdentity(PackageInfo info) {
        return info != null && info.applicationInfo != null && info.packageName != null;
    }

    private static int count(Object[] values) {
        return values == null ? 0 : values.length;
    }

    private static String resolveComponentName(String packageName, String name) {
        if (name == null || packageName == null) {
            return name;
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') < 0) {
            return packageName + "." + name;
        }
        return name;
    }

    static String splitArchiveDiagnostic(ManifestInfo manifest) {
        if (manifest == null) {
            return null;
        }
        if ((manifest.splitName != null && !manifest.splitName.isEmpty())
                || manifest.isFeatureSplit
                || (manifest.configForSplit != null && !manifest.configForSplit.isEmpty())) {
            StringBuilder detail = new StringBuilder("This archive is a split APK");
            if (manifest.splitName != null && !manifest.splitName.isEmpty()) {
                detail.append(" (split=").append(manifest.splitName).append(')');
            }
            return detail.append("; PackageManager parses a standalone file as a base APK; "
                    + "build from a universal APK or provide the complete split set").toString();
        }
        if (manifest.isSplitRequired
                && (manifest.splitTypes == null || manifest.splitTypes.isEmpty())) {
            return "This is a split APK base requiring additional APK splits"
                    + (manifest.requiredSplitTypes == null || manifest.requiredSplitTypes.isEmpty()
                    ? "" : " (required: " + manifest.requiredSplitTypes + ")")
                    + "; build from a universal APK or provide the complete split set";
        }
        return null;
    }

    private static ManifestInfo readManifest(File apk) throws Exception {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) {
                throw new IOException("APK has no AndroidManifest.xml: " + apk);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                ManifestParser parser = new ManifestParser(input);
                parser.parse();
                ManifestInfo info = parser.getManifestInfo();
                if (info == null || info.packageName == null) {
                    throw new IOException("Cannot parse AndroidManifest.xml in " + apk);
                }
                return info;
            }
        }
    }

    static PrivateKey readPrivateKey(InputStream input) throws Exception {
        byte[] encoded = readAll(input);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
        String[] algorithms = {"RSA", "EC", "DSA"};
        Exception last = null;
        for (String algorithm : algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("Unsupported or invalid PKCS#8 private key", last);
    }

    static X509Certificate readCertificate(InputStream input) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(input);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }
}
