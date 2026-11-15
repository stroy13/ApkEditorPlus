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
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create local model directory");
        long largestPart = Math.max(GEMMA4_PART_BYTES[0], GEMMA4_PART_BYTES[1]);
        if (dir.getUsableSpace() < GEMMA4_E2B_BYTES + largestPart + 134217728L)
            throw new IOException("Gemma 4 E2B needs about 3.8 GB free storage during installation");
        File assembled = File.createTempFile("gemma4-e2b-", ".partial", dir);
        java.util.ArrayList<File> parts = new java.util.ArrayList<>();
        boolean committed = false;
        try {
            long completedBytes = 0;
            for (int i = 0; i < GEMMA4_PART_URLS.length; i++) {
                if (progress != null && progress.isCancelled()) throw new IOException("Download cancelled");
                final long offset = completedBytes;
                LocalModelStore.Progress partProgress = progress == null ? null : new LocalModelStore.Progress() {
                    @Override public boolean isCancelled() { return progress.isCancelled(); }
                    @Override public void onTotal(long bytes) { progress.onTotal(GEMMA4_E2B_BYTES); }
                    @Override public void onBytes(long bytes) { progress.onBytes(offset + bytes); }
                };
                File part = LocalModelDownload.downloadBundle(GEMMA4_PART_URLS[i], GEMMA4_PART_SHA256[i], dir, partProgress);
                parts.add(part);
                if (part.length() != GEMMA4_PART_BYTES[i]) throw new IOException("Gemma 4 part size mismatch");
                try (java.io.FileInputStream in = new java.io.FileInputStream(part);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(assembled, true)) {
                    byte[] buffer = new byte[1024 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (progress != null && progress.isCancelled()) throw new IOException("Download cancelled");
                        out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                completedBytes += part.length();
                part.delete();
                parts.remove(part);
                if (progress != null) progress.onBytes(completedBytes);
            }
            if (assembled.length() != GEMMA4_E2B_BYTES || !GEMMA4_SHA256.equalsIgnoreCase(sha256(assembled)))
                throw new IOException("Gemma 4 model checksum or size mismatch");
            String name = "gemma4-e2b-int4-" + UUID.randomUUID().toString().substring(0, 8) + ".litertlm";
            File target = new File(dir, name);
            if (!assembled.renameTo(target)) throw new IOException("Cannot install Gemma 4 model");
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED, name).commit()) {
                target.delete();
                throw new IOException("Cannot select Gemma 4 model");
            }
            committed = true;
            return target;
        } finally {
            if (!committed) assembled.delete();
            for (File part : parts) part.delete();
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
