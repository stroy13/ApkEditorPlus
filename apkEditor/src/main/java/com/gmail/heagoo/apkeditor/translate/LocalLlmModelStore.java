package com.gmail.heagoo.apkeditor.translate;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** App-private storage for user-selected LiteRT-LM model bundles. */
public final class LocalLlmModelStore {
    private static final String PREFS = "local_litert_lm";
    private static final String SELECTED = "selected_model";
    public static final int MODEL_GEMMA3_1B = 0;
    public static final int MODEL_GEMMA4_E2B = 1;
    public static final int MODEL_GEMMA4_E4B = 2;
    public static final long RECOMMENDED_MODEL_BYTES = 584417280L;
    public static final String RECOMMENDED_MODEL_SHA256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be";
    public static final String RECOMMENDED_MODEL_URL = "https://github.com/topoteretes/LiteRT-LM/releases/download/model-gemma3-1b-it-int4-v1/gemma3-1b-it-int4.litertlm";
    public static final long GEMMA4_E2B_BYTES = 2588147712L;
    private static final String GEMMA4_SHA256 = "5f1f73af802f5675f199b352359f4b3edcaf051fc6fbc4a220ed61724eab0c56";
    private static final String[] GEMMA4_PART_URLS = {
            "https://github.com/topoteretes/LiteRT-LM/releases/download/model-gemma4-e2b-it-patched-v1/gemma-4-E2B-it-patched.litertlm.part0",
            "https://github.com/topoteretes/LiteRT-LM/releases/download/model-gemma4-e2b-it-patched-v1/gemma-4-E2B-it-patched.litertlm.part1"};
    private static final long[] GEMMA4_PART_BYTES = {1293942784L, 1294204928L};
    private static final String[] GEMMA4_PART_SHA256 = {
            "409710d482005227e1d409c9923da4d58a93c0b5f5e05f8eccdcda70748ee244",
            "8c230a2593ddd95c38573637ba6b2ee89a09bde27e41059c6fce6479ebea9216"};
    public static final long GEMMA4_E4B_BYTES = 3659530240L;
    private static final String GEMMA4_E4B_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0";
    private static final String[] GEMMA4_E4B_PART_URLS = {
            "https://github.com/th9rain/laobai-gemma-demo/releases/download/model-weights-v1/gemma-4-E4B-it.litertlm.part01",
            "https://github.com/th9rain/laobai-gemma-demo/releases/download/model-weights-v1/gemma-4-E4B-it.litertlm.part02"};
    private static final long[] GEMMA4_E4B_PART_BYTES = {1900000000L, 1759530240L};
    private static final String[] GEMMA4_E4B_PART_SHA256 = {
            "8a921d41ea6ae8750d5a94c511aaa05a934f57c3566c2f5c67f7c05ad8499263",
            "7034d29845a70bb69f0f5447a266703b69fc76fc29faf2c6ff3845995d5fa60e"};
    private LocalLlmModelStore() {}

    public static File selected(Context context) {
        String name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SELECTED, null);
        if (name == null || name.contains("/") || name.contains("\\")) return null;
        File file = new File(directory(context), name);
        return file.isFile() && file.length() > 0 ? file : null;
    }

    public static File[] available(Context context) {
        File dir = directory(context);
        File[] files = dir.listFiles(file -> file.isFile() && file.length() > 0 && file.getName().endsWith(".litertlm"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        return files;
    }

    /** Returns an installed file created by one of the built-in download options. */
    public static File downloaded(Context context, int model) {
        String prefix = modelPrefix(model);
        if (prefix == null) return null;
        File current = selected(context);
        if (current != null && isBuiltInModelFile(model, current.getName())) return current;
        for (File file : available(context)) {
            if (isBuiltInModelFile(model, file.getName())) return file;
        }
        return null;
    }

    public static boolean isDownloaded(Context context, int model) {
        return downloaded(context, model) != null;
    }

    static String modelPrefix(int model) {
        switch (model) {
            case MODEL_GEMMA3_1B: return "gemma3-1b-int4-";
            case MODEL_GEMMA4_E2B: return "gemma4-e2b-int4-";
            case MODEL_GEMMA4_E4B: return "gemma4-e4b-int4-";
            default: return null;
        }
    }

    static boolean isBuiltInModelFile(int model, String fileName) {
        String prefix = modelPrefix(model);
        return prefix != null && fileName != null
                && fileName.startsWith(prefix) && fileName.endsWith(".litertlm");
    }

    public static boolean select(Context context, String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) return false;
        File file = new File(directory(context), fileName);
        return file.isFile() && file.length() > 0
                && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, fileName).commit();
    }

    public static File importModel(Context context, Uri uri) throws IOException {
        return importModel(context, uri, null);
    }

    public static File importModel(Context context, Uri uri, String displayName) throws IOException {
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create local model directory");
        File temp = File.createTempFile("model-", ".partial", dir);
        boolean committed = false;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("Cannot open selected model");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled");
                out.write(buffer, 0, n);
            }
            if (temp.length() == 0) throw new IOException("Selected model is empty");
            out.getFD().sync();
            String label = displayName == null ? "local-model" : new File(displayName).getName().replaceAll("(?i)\\.litertlm$", "")
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            if (label.isEmpty()) label = "local-model";
            if (label.length() > 48) label = label.substring(0, 48);
            String name = label + "-" + UUID.randomUUID().toString().substring(0, 8) + ".litertlm";
            File target = new File(dir, name);
            if (!temp.renameTo(target)) throw new IOException("Cannot store local model");
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, name).commit()) {
                target.delete();
                throw new IOException("Cannot select local model");
            }
            committed = true;
            return target;
        } finally { if (!committed) temp.delete(); }
    }

    public static File downloadRecommended(Context context, LocalModelStore.Progress progress) throws IOException {
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create local model directory");
        File downloaded = LocalModelDownload.downloadBundle(RECOMMENDED_MODEL_URL, RECOMMENDED_MODEL_SHA256, dir, progress);
        if (downloaded.length() != RECOMMENDED_MODEL_BYTES) {
            downloaded.delete();
            throw new IOException("Recommended model size mismatch");
        }
        String name = "gemma3-1b-int4-" + UUID.randomUUID().toString().substring(0, 8) + ".litertlm";
        File target = new File(dir, name);
        if (!downloaded.renameTo(target)) { downloaded.delete(); throw new IOException("Cannot install downloaded model"); }
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, name).commit()) {
            target.delete();
            throw new IOException("Cannot select downloaded model");
        }
        return target;
    }

    public static File downloadGemma4E2B(Context context, LocalModelStore.Progress progress) throws IOException {
        return downloadSplitModel(context, progress, "gemma4-e2b-int4", GEMMA4_E2B_BYTES,
                GEMMA4_SHA256, GEMMA4_PART_URLS, GEMMA4_PART_BYTES, GEMMA4_PART_SHA256);
    }

    public static File downloadGemma4E4B(Context context, LocalModelStore.Progress progress) throws IOException {
        return downloadSplitModel(context, progress, "gemma4-e4b-int4", GEMMA4_E4B_BYTES,
                GEMMA4_E4B_SHA256, GEMMA4_E4B_PART_URLS, GEMMA4_E4B_PART_BYTES, GEMMA4_E4B_PART_SHA256);
    }

    /** Download split assets concurrently, verify each chunk, concatenate, then verify the complete model. */
    private static File downloadSplitModel(Context context, LocalModelStore.Progress progress, String modelName,
                                           long modelBytes, String modelSha256, String[] urls,
                                           long[] partBytes, String[] partSha256) throws IOException {
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create local model directory");
        long temporaryBytes = modelBytes;
        for (long bytes : partBytes) temporaryBytes += bytes;
        long minimumFree = temporaryBytes + 134217728L;
        if (dir.getUsableSpace() < minimumFree)
            throw new IOException(modelName + " needs about " + (minimumFree / (1024 * 1024)) + " MiB free storage");
        if (urls.length == 0 || urls.length != partBytes.length || urls.length != partSha256.length)
            throw new IOException("Invalid split model catalog");

        File[] downloadedParts = new File[urls.length];
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(urls.length);
        java.util.List<java.util.concurrent.Future<File>> futures = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicLong aggregate = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLongArray partProgressBytes = new java.util.concurrent.atomic.AtomicLongArray(urls.length);
        File assembled = null;
        boolean committed = false;
        if (progress != null) progress.onTotal(modelBytes);
        try {
            for (int i = 0; i < urls.length; i++) {
                final int partIndex = i;
                futures.add(workers.submit(() -> LocalModelDownload.downloadBundle(urls[partIndex],
                        partSha256[partIndex], dir, new LocalModelStore.Progress() {
                            @Override public boolean isCancelled() {
                                return Thread.currentThread().isInterrupted() || (progress != null && progress.isCancelled());
                            }
                            @Override public void onTotal(long ignored) { }
                            @Override public void onBytes(long bytes) {
                                if (progress != null) {
                                    long previous = partProgressBytes.getAndSet(partIndex, bytes);
                                    long now = aggregate.addAndGet(bytes - previous);
                                    progress.onBytes(now);
                                }
                            }
                        })));
            }
            for (int i = 0; i < futures.size(); i++) {
                try { downloadedParts[i] = futures.get(i).get(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Model download interrupted", e); }
                catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    throw new IOException("Model part download failed", cause);
                }
                if (downloadedParts[i].length() != partBytes[i]) throw new IOException("Model part size mismatch");
            }
            if (progress != null && progress.isCancelled()) throw new IOException("Download cancelled");
            assembled = File.createTempFile(modelName + "-", ".partial", dir);
            try (FileOutputStream out = new FileOutputStream(assembled)) {
                byte[] buffer = new byte[1024 * 1024];
                for (File part : downloadedParts) {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(part)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (progress != null && progress.isCancelled()) throw new IOException("Download cancelled");
                            out.write(buffer, 0, n);
                        }
                    }
                }
                out.getFD().sync();
            }
            if (assembled.length() != modelBytes || !modelSha256.equalsIgnoreCase(sha256(assembled)))
                throw new IOException("Model checksum or size mismatch");
            if (progress != null && progress.isCancelled()) throw new IOException("Download cancelled");
            String name = modelName + "-" + UUID.randomUUID().toString().substring(0, 8) + ".litertlm";
            File target = new File(dir, name);
            if (!assembled.renameTo(target)) throw new IOException("Cannot install model");
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, name).commit()) {
                target.delete();
                throw new IOException("Cannot select model");
            }
            committed = true;
            return target;
        } finally {
            workers.shutdownNow();
            for (java.util.concurrent.Future<File> future : futures) {
                if (!future.isDone()) future.cancel(true);
                else if (!future.isCancelled()) {
                    try { File part = future.get(); if (part != null) part.delete(); }
                    catch (Exception ignored) { }
                }
            }
            for (File part : downloadedParts) if (part != null) part.delete();
            if (assembled != null && !committed) assembled.delete();
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            StringBuilder value = new StringBuilder(64);
            for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }

    private static File directory(Context context) { return new File(context.getFilesDir(), "local-litert-models"); }
}
