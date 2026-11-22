package com.gmail.heagoo.apkeditor;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.decoder.ARSCDecoder;
import brut.androlib.res.util.ExtFile;
import brut.directory.Directory;
import com.gmail.heagoo.apkeditor.ApkDecoderMine;
import com.gmail.heagoo.apkeditor.base.R;
import com.gmail.heagoo.apkeditor.smali.ResSmaliIdProvider;
import com.gmail.heagoo.apkeditor.util.ApkResourceLayout;
import com.gmail.heagoo.apkeditor.util.ProtectedDexExtractor;
import com.gmail.heagoo.apkeditor.util.ZipPathSanitizer;
import com.gmail.heagoo.apklib.ManifestInfoCollector;
import com.gmail.heagoo.common.IOUtils;
import com.gmail.heagoo.common.RefInvoke;
import com.gmail.heagoo.common.SDCard;
import com.gmail.heagoo.neweditor.Document;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkParseThread extends Thread {
    private WeakReference<Activity> activityRef;
    private ResPackage apkPackage;
    private String manifestPackageName;
    private String apkPath;
    private File sanitizedApkFile;
    private volatile boolean cancelled;
    private WeakReference<ApkParseConsumer> consumerRef;
    private String decodeRootPath;
    private volatile ApkDecoderMine decoder;
    private String errMessage;
    private volatile String errorStackTrace;
    private ResSmaliIdProvider idProvider;
    private boolean isFullDecoding;
    private boolean mApkProtected;
    private ResTable resTable;
    private long stageStartNanos;
    public static boolean sKeepBroken = false;
    public static boolean isProAndNoModification = true;
    private static final Object DECODE_LOCK = new Object();
    private final long[] stageNanos = new long[11];
    private int timingStage = -1;
    // Watchdog: if the decode makes no progress at all for this long, the UI is
    // told about the stall instead of waiting forever on "Processing manifest
    // and resources..." (the last label that was posted before the hang).
    private static final long STALL_TIMEOUT_MS = 120000L;
    private volatile long lastProgressNanos = System.nanoTime();
    private volatile int lastPhase = -1;
    private volatile boolean finished;
    private volatile boolean failureReported;

    private synchronized void recordStage(int phase) {
        long now = System.nanoTime();
        if (this.timingStage >= 0 && this.timingStage < this.stageNanos.length) {
            long[] jArr = this.stageNanos;
            int i = this.timingStage;
            jArr[i] = jArr[i] + (now - this.stageStartNanos);
        }
        this.timingStage = phase;
        this.stageStartNanos = now;
    }

    public synchronized String getStageTimings() {
        StringBuilder result;
        String[] names = {"waiting", "app resource table", "manifest", "resource values", "other files", "Android framework", "resource indexing", "resource files", "old project cleanup", "bundled tools", "string indexing"};
        long now = System.nanoTime();
        result = new StringBuilder("Stage time (ms):");
        int i = 0;
        while (i < names.length) {
            long n = this.stageNanos[i] + (i == this.timingStage ? now - this.stageStartNanos : 0L);
            if (n > 0) {
                result.append("\n  ").append(names[i]).append(": ").append(n / 1000000);
            }
            i++;
        }
        ApkDecoderMine current = this.decoder;
        if (current != null) {
            result.append(Document.LINE_SEPARATOR_UNIX).append(current.getWorkerStats());
        }
        ResTable table = this.resTable;
        if (table != null) {
            result.append(Document.LINE_SEPARATOR_UNIX).append(table.getFrameworkLoadInfo());
        }
        return result.toString();
    }

    void indexStringsStarted() {
        progress(10);
    }

    public void progress(int phase) {
        recordStage(phase);
        this.lastPhase = phase;
        this.lastProgressNanos = System.nanoTime();
        ApkParseConsumer consumer = this.consumerRef.get();
        if (this.cancelled || consumer == null) {
            return;
        }
        consumer.decodeProgress(phase);
    }

    public void progressCount(int done, int total) {
        this.lastProgressNanos = System.nanoTime();
        ApkParseConsumer consumer = this.consumerRef.get();
        if (this.cancelled || consumer == null) {
            return;
        }
        consumer.decodeProgressCount(done, total);
    }

    /**
     * Reports a decode failure to the UI exactly once. Without this a thread that
     * dies with an {@link Error} (or an early return) left the loading screen
     * spinning on the last progress label forever.
     */
    private void reportFailure(String message) {
        if (this.failureReported || this.cancelled) {
            return;
        }
        this.failureReported = true;
        if (message == null || message.length() == 0) {
            message = "Unknown error";
        }
        this.errMessage = message;
        Log.e("ApkParse", "APK decoding failed: " + message);
        ApkParseConsumer consumer = this.consumerRef.get();
        if (consumer != null) {
            consumer.decodeFailed(message);
        }
    }

    private void startWatchdog() {
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ApkParseThread.this.finished) {
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (ApkParseThread.this.finished || ApkParseThread.this.cancelled) {
                        return;
                    }
                    long idleMs = (System.nanoTime() - ApkParseThread.this.lastProgressNanos) / 1000000L;
                    // Phase 0 = "waiting for previous operation" (the decode lock may
                    // legitimately be busy with a big project), so be more patient there.
                    long timeoutMs = ApkParseThread.this.lastPhase == 0 ? 10 * STALL_TIMEOUT_MS : STALL_TIMEOUT_MS;
                    if (idleMs < timeoutMs) {
                        continue;
                    }
                    String message = buildStallMessage(idleMs);
                    // Notify first (reportFailure is ignored once cancelled), then
                    // ask the decoder to stop so the retry button starts clean.
                    ApkParseThread.this.reportFailure(message);
                    ApkParseThread.this.stopParse();
                    return;
                }
            }
        }, "apk-decode-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private String buildStallMessage(long idleMs) {
        long seconds = idleMs / 1000L;
        int phase = this.lastPhase;
        String message;
        Activity activity = this.activityRef.get();
        if (activity != null) {
            try {
                message = activity.getString(R.string.lab_load_stalled, (int) seconds, phase);
            } catch (Throwable t) {
                message = "APK decoding stalled: no progress for " + seconds + " s (stage " + phase + ")";
            }
        } else {
            message = "APK decoding stalled: no progress for " + seconds + " s (stage " + phase + ")";
        }
        return message + Document.LINE_SEPARATOR_UNIX + getStageTimings();
    }

    public ApkParseThread(Activity ctx, ApkParseConsumer consumer, String apkPath, String decodeRootPath, boolean isFullDecoding) {
        this.activityRef = new WeakReference<>(ctx);
        this.consumerRef = new WeakReference<>(consumer);
        this.apkPath = apkPath;
        this.decodeRootPath = decodeRootPath;
        this.isFullDecoding = isFullDecoding;
    }

    @Override
    public void run() {
        boolean success = false;
        progress(0);
        startWatchdog();
        try {
            success = decodeApk();
        } catch (Throwable t) {
            // Errors (NoClassDefFoundError, OutOfMemoryError, StackOverflowError,
            // ...) used to escape and kill this thread silently, leaving the
            // loading screen hanging on the last progress label.
            Log.e("ApkParse", "APK decoding failed", t);
            this.errorStackTrace = Log.getStackTraceString(t);
            if (this.errMessage == null) {
                this.errMessage = t instanceof OutOfMemoryError
                        ? "OutOfMemoryError: insufficient memory while opening the APK"
                        : t.toString();
            }
        } finally {
            this.finished = true;
            File sanitized = this.sanitizedApkFile;
            if (sanitized != null) sanitized.delete();
        }
        if (!success) {
            reportFailure(this.errMessage);
        }
    }

    private boolean decodeApk() {
        synchronized (DECODE_LOCK) {
            if (this.cancelled) {
                return true;
            }
            progress(9);
            Activity activity = this.activityRef.get();
            if (activity == null) {
                this.errMessage = "Activity is no longer available";
                return false;
            }
            ApkComposeThread tmp = new ApkComposeThread(activity, null, null, null);
            if (!tmp.prepare()) {
                this.errMessage = tmp.getErrMessage();
                return false;
            }
            return parse();
        }
    }

    private void readAssetFile(Context ctx, String filename, byte[] data) throws IOException {
        int curRead;
        AssetManager am = ctx.getAssets();
        InputStream input = am.open(filename);
        int readLen = input.read(data);
        while (readLen < data.length && (curRead = input.read(data, readLen, data.length - readLen)) != -1) {
            readLen += curRead;
        }
        input.close();
    }

    private boolean parse() {
        boolean z;
        boolean z2;
        try {
            Activity activity = this.activityRef.get();
            byte[] bArr = null;
            byte[] bArr2 = null;
            byte[] bArr3 = null;
            if (!this.isFullDecoding) {
                bArr = new byte[68];
                readAssetFile(activity, "dummy_png.png", bArr);
                bArr2 = new byte[667];
                readAssetFile(activity, "dummy_jpg.jpg", bArr2);
                bArr3 = new byte[97];
                readAssetFile(activity, "dummy.9.png", bArr3);
            }
            File sourceApk = new File(this.apkPath);
            File safeApk = ZipPathSanitizer.sanitize(sourceApk, activity.getCacheDir());
            if (!safeApk.equals(sourceApk)) this.sanitizedApkFile = safeApk;
            ExtFile extFile = new ExtFile(safeApk);
            Directory directory = extFile.getDirectory();
            this.mApkProtected = !ApkResourceLayout.hasStandardResourceFiles(directory);
            if (this.mApkProtected) {
                if (!directory.containsDir("r")) {
                    if (!directory.containsDir("R")) {
                        z = false;
                        z2 = true;
                    }
                }
                try {
                    InputStream fileInput = directory.getFileInput("AndroidManifest.xml");
                    ManifestInfoCollector manifestInfoCollector = new ManifestInfoCollector();
                    manifestInfoCollector.parse(fileInput, "manifest", "package");
                    String value = manifestInfoCollector.getValue();
                    this.manifestPackageName = value;
                    fileInput.close();
                    String makeWorkingDir = SDCard.makeWorkingDir(activity);
                    File workingDir = new File(makeWorkingDir);
                    List<File> dexFiles = ProtectedDexExtractor.isSupported(directory)
                            ? ProtectedDexExtractor.extract(directory, workingDir)
                            : new java.util.ArrayList<File>();
                    if (dexFiles.isEmpty()) {
                        // Keep the legacy path for protectors that leave their loader DEX
                        // unencrypted at the archive root.
                        InputStream fileInput2 = directory.getFileInput("classes.dex");
                        File loaderDex = new File(workingDir, "classes.dex");
                        FileOutputStream fileOutputStream = new FileOutputStream(loaderDex);
                        IOUtils.copy(fileInput2, fileOutputStream);
                        fileInput2.close();
                        fileOutputStream.close();
                        dexFiles.add(loaderDex);
                    }
                    z = false;
                    z2 = true;
                    try {
                        for (File dexFile : dexFiles) {
                            RefInvoke.invokeStaticMethod("com.gmail.heagoo.apkeditor.plus.ResourceDecoder", "decodeResources", new Class[]{String.class, String.class}, new Object[]{dexFile.getAbsolutePath(), makeWorkingDir});
                        }
                        this.idProvider = new ResSmaliIdProvider(makeWorkingDir, value);
                    } catch (Exception dexDecodeError) {
                        Log.w("ApkParse", "Could not extract R classes from protected DEX payload", dexDecodeError);
                    }
                } catch (Exception e3) {
                    z = false;
                    z2 = true;
                }
            } else {
                z = false;
                z2 = true;
            }
            boolean z3 = z2;
            try {
                progress(z3 ? 1 : 0);
                this.resTable = getResTable(extFile, z3);
                if (this.cancelled) {
                    return z;
                }
                progress(10);
                ApkParseConsumer apkParseConsumer = this.consumerRef.get();
                if (apkParseConsumer != null) {
                    apkParseConsumer.resTableDecoded(true);
                }
                if (this.cancelled) {
                    return z;
                }
                progress(5);
                this.resTable.getPackage(1);
                Log.i("ApkParse", "Framework parser gate: "
                        + this.resTable.getFrameworkLoadInfo() + "; "
                        + getResourceDiagnostics());
                if (this.cancelled) {
                    return z;
                }
                this.decoder = new ApkDecoderMine(this.resTable, bArr, bArr2, bArr3);
                this.decoder.setProgressListener(new ApkDecoderMine.ProgressListener() {
                    @Override
                    public void onProgress(int phase) {
                        ApkParseThread.this.progress(phase);
                    }

                    @Override
                    public void onCount(int done, int total) {
                        ApkParseThread.this.progressCount(done, total);
                    }
                });
                if (this.cancelled) {
                    return z;
                }
                progress(8);
                if (!ProjectCleanup.deleteAll(new File(this.decodeRootPath), new BooleanSupplier() {
                    @Override
                    public final boolean getAsBoolean() {
                        return ApkParseThread.this.m109lambda$parse$0$comgmailheagooapkeditorApkParseThread();
                    }
                })) {
                    return z;
                }
                isProAndNoModification = ApkComposeThread.isProAndNoModification(this.activityRef.get());
                File file = new File(this.decodeRootPath);
                if (!file.exists()) {
                    file.mkdirs();
                }
                this.decoder.decode(extFile, file);
                if (!this.cancelled && this.consumerRef.get() != null) {
                    this.consumerRef.get().resourceDecoded(this.decoder.getFileEntry2ZipEntry());
                    return true;
                }
                return true;
            } catch (Exception e4) {
                this.errMessage = e4.toString();
                this.errorStackTrace = Log.getStackTraceString(e4);
                Log.e("ApkParse", "APK resource decode failed", e4);
                return z;
            }
        } catch (Exception e5) {
            Log.e("ApkParse", "APK failed before resource decoding started", e5);
            this.errorStackTrace = Log.getStackTraceString(e5);
            this.errMessage = e5.getMessage() != null ? e5.getMessage() : e5.toString();
            z = false;
        }
        return z;
    }

    boolean m109lambda$parse$0$comgmailheagooapkeditorApkParseThread() {
        return this.cancelled;
    }

    private ResTable getResTable(ExtFile apkFile, boolean loadMainPkg) throws AndrolibException {
        Activity activity = this.activityRef.get();
        ResTable resTable = new ResTable(activity.getApplicationContext(), this.mApkProtected);
        if (loadMainPkg) {
            loadMainPkg(resTable, apkFile);
        }
        return resTable;
    }

    private ResPackage loadMainPkg(ResTable resTable, ExtFile apkFile) throws AndrolibException {
        LOGGER.info("Loading resource table of apk file...");
        // Protected tables frequently carry qualifier bytes newer than this
        // decoder knows. Keep those entries so a single invalid configuration does
        // not remove every string resource from the project.
        boolean keepBroken = sKeepBroken || this.mApkProtected;
        ResPackage[] pkgs = getResPackagesFromApk(apkFile, resTable, keepBroken);
        if (pkgs == null) {
            Log.w("ApkParse", "Resource table returned no packages");
            return null;
        }
        // A normal APK can still contain a newer/invalid qualifier. If the first
        // pass produced no string resources, retry once with broken configs kept;
        // otherwise the String tab sees an apparently valid package with zero
        // values even though the table contains them.
        if (!keepBroken && countStringResources(pkgs) == 0) {
            Log.w("ApkParse", "No string resources after strict table decode; retrying with keepBroken");
            pkgs = getResPackagesFromApk(apkFile, resTable, true);
            if (pkgs == null) {
                Log.w("ApkParse", "keepBroken resource table retry returned no packages");
                return null;
            }
        }
        Log.i("ApkParse", describePackages(pkgs));
        // Do not trust package-array order. Protected/resource-virtualized APKs
        // can contain decoy package chunks, and a one-package table can still be a
        // stringless decoy. Prefer the manifest package only when it actually has
        // string resources; otherwise choose the non-framework package with the
        // strongest string-resource signal.
        this.apkPackage = null;
        ResPackage manifestCandidate = null;
        for (ResPackage candidate : pkgs) {
            if (candidate != null && !isFrameworkPackage(candidate)
                    && this.manifestPackageName != null
                    && this.manifestPackageName.equals(candidate.getName())) {
                manifestCandidate = candidate;
                break;
            }
        }
        if (manifestCandidate != null && countStringResources(manifestCandidate) > 0) {
            this.apkPackage = manifestCandidate;
        }
        if (this.apkPackage == null) {
            ResPackage bestCandidate = null;
            int bestStringResources = -1;
            int bestManifestMatch = -1;
            int bestPrivatePackage = -1;
            int bestSpecCount = -1;
            for (ResPackage candidate : pkgs) {
                if (candidate == null || isFrameworkPackage(candidate)) {
                    continue;
                }
                int stringResources = countStringResources(candidate);
                int manifestMatch = candidate == manifestCandidate ? 1 : 0;
                int privatePackage = candidate.getId() == 0x7f ? 1 : 0;
                int specCount = candidate.getResSpecCount();
                if (bestCandidate == null || stringResources > bestStringResources
                        || (stringResources == bestStringResources
                        && (manifestMatch > bestManifestMatch
                        || (manifestMatch == bestManifestMatch
                        && (privatePackage > bestPrivatePackage
                        || (privatePackage == bestPrivatePackage
                        && specCount > bestSpecCount)))))) {
                    bestCandidate = candidate;
                    bestStringResources = stringResources;
                    bestManifestMatch = manifestMatch;
                    bestPrivatePackage = privatePackage;
                    bestSpecCount = specCount;
                }
            }
            this.apkPackage = bestCandidate;
        }
        if (this.apkPackage == null && pkgs.length > 0) {
            // All candidates were framework-like. Keep the first package so the
            // existing decoder can report a useful result instead of failing with
            // a null package.
            this.apkPackage = pkgs[0];
        }
        if (this.apkPackage == null) {
            throw new AndrolibException("Arsc files with zero or multiple packages");
        }
        resTable.addPackage(this.apkPackage, true);
        Log.i("ApkParse", "Selected resource package: "
                + describePackage(this.apkPackage));
        LOGGER.info("Loaded.");
        return this.apkPackage;
    }

    private boolean isFrameworkPackage(ResPackage pkg) {
        return pkg != null && (pkg.getId() == 1
                || "android".equals(pkg.getName())
                || "com.htc".equals(pkg.getName()));
    }

    private String describePackages(ResPackage[] packages) {
        StringBuilder result = new StringBuilder("Resource table packages=")
                .append(packages.length).append(" [");
        for (int i = 0; i < packages.length; i++) {
            if (i != 0) {
                result.append(", ");
            }
            result.append(describePackage(packages[i]));
        }
        return result.append(']').toString();
    }

    private String describePackage(ResPackage pkg) {
        if (pkg == null) {
            return "null";
        }
        return "id=0x" + Integer.toHexString(pkg.getId())
                + ", name=" + pkg.getName()
                + ", specs=" + pkg.getResSpecCount()
                + ", types=" + pkg.listTypes().size()
                + ", stringSpecs=" + countStringSpecs(pkg)
                + ", stringResources=" + countStringResources(pkg);
    }

    public String getResourceDiagnostics() {
        return "protected=" + this.mApkProtected + "; "
                + describePackage(this.apkPackage) + "; framework="
                + (this.resTable == null ? "not initialized" : this.resTable.getFrameworkLoadInfo());
    }

    private int countStringSpecs(ResPackage pkg) {
        int count = 0;
        for (ResResSpec spec : pkg.listResSpecs()) {
            if (isStringResourceSpec(spec)) {
                count++;
            }
        }
        return count;
    }

    private int countStringResources(ResPackage[] packages) {
        int count = 0;
        if (packages == null) {
            return 0;
        }
        for (ResPackage pkg : packages) {
            if (!isFrameworkPackage(pkg)) {
                count += countStringResources(pkg);
            }
        }
        return count;
    }

    private int countStringResources(ResPackage pkg) {
        int count = 0;
        for (ResResSpec spec : pkg.listResSpecs()) {
            if (isStringResourceSpec(spec) && spec.getAllResources() != null) {
                count += spec.getAllResources().size();
            }
        }
        return count;
    }

    private boolean isStringResourceSpec(ResResSpec spec) {
        if (spec == null || spec.getType() == null) {
            return false;
        }
        if ("string".equalsIgnoreCase(spec.getType().getName())) {
            return true;
        }
        for (ResResource resource : spec.listResources()) {
            if (resource != null && resource.getValue() instanceof ResStringValue) {
                return true;
            }
        }
        return false;
    }

    private ResPackage[] getResPackagesFromApk(ExtFile apkFile, ResTable resTable, boolean keepBroken) throws AndrolibException {
        Exception e = null;
        ZipFile zipFile = null;
        Throwable th;
        Throwable th2;
        try {
            zipFile = new ZipFile((File) apkFile);
        } catch (IOException e2) {
            e = e2;
        }
        if (zipFile == null) {
            throw new AndrolibException("Could not read resources.arsc from file: " + apkFile, e);
        }
        try {
            try {
                ZipEntry entry = zipFile.getEntry("resources.arsc");
                if (entry != null) {
                    InputStream in = new BufferedInputStream(zipFile.getInputStream(entry), 65536);
                    try {
                        try {
                            try {
                                ResPackage[] packages = ARSCDecoder.decode(in, false, keepBroken, resTable, this.idProvider, this.mApkProtected).getPackages();
                                in.close();
                                zipFile.close();
                                return packages;
                            } catch (Throwable th3) {
                                th2 = th3;
                                try {
                                    in.close();
                                    throw th2;
                                } catch (Throwable th4) {
                                    th2.addSuppressed(th4);
                                    throw th2;
                                }
                            }
                        } catch (Throwable th5) {
                            th = th5;
                            try {
                                zipFile.close();
                                throw th;
                            } catch (Throwable th6) {
                                th.addSuppressed(th6);
                                throw th;
                            }
                        }
                    } catch (Throwable th7) {
                        th2 = th7;
                    }
                } else {
                    zipFile.close();
                    return null;
                }
            } catch (Throwable th8) {
                th = th8;
            }
        } catch (Throwable e3) {
            throw new AndrolibException("Could not read resources.arsc from file: " + apkFile, e3);
        }
        return null;
    }

    public ResTable getResTable() {
        return this.resTable;
    }

    public String getErrMessage() {
        return this.errMessage;
    }

    public String getErrorStackTrace() {
        return this.errorStackTrace;
    }

    public String getResourceRootPath() {
        return this.decodeRootPath + "/res";
    }

    public ResPackage getApkPackage() {
        return this.apkPackage;
    }

    public void stopParse() {
        this.cancelled = true;
        interrupt();
        if (this.decoder != null) {
            this.decoder.stopDecode();
        }
    }
}
