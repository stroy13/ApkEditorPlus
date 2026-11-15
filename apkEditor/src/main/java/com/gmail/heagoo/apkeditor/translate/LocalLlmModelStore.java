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

    private static File directory(Context context) { return new File(context.getFilesDir(), "local-litert-models"); }
}
