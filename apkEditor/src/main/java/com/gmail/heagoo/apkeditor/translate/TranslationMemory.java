package com.gmail.heagoo.apkeditor.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local, opt-in-by-edit translation memory: only translations the user corrected are reused. */
public final class TranslationMemory {
    private static final String PREFIX = "human_translation_v1_";
    private static final int MAX_ENTRIES = 300;

    private TranslationMemory() { }

    public static String find(Context context, String sourceLanguage, String targetLanguage, String source) {
        if (context == null || sourceLanguage == null || targetLanguage == null || source == null) return null;
        String value = preferences(context).getString(key(sourceLanguage, targetLanguage, source), null);
        if (value == null || value.trim().isEmpty() || !TranslationTokens.preserved(source, value)) return null;
        return value;
    }

    public static boolean remember(Context context, String sourceLanguage, String targetLanguage,
                                   String source, String correctedTranslation) {
        if (context == null || sourceLanguage == null || targetLanguage == null || source == null
                || correctedTranslation == null || correctedTranslation.trim().isEmpty()
                || !TranslationTokens.preserved(source, correctedTranslation)) return false;
        SharedPreferences prefs = preferences(context);
        String key = key(sourceLanguage, targetLanguage, source);
        List<String> keys = new ArrayList<>();
        for (String existing : prefs.getAll().keySet()) if (existing.startsWith(PREFIX)) keys.add(existing);
        Collections.sort(keys);
        SharedPreferences.Editor edit = prefs.edit();
        while (keys.size() >= MAX_ENTRIES && !keys.isEmpty()) edit.remove(keys.remove(0));
        edit.putString(key, correctedTranslation).apply();
        return true;
    }

    private static SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static String key(String sourceLanguage, String targetLanguage, String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return PREFIX + sourceLanguage + "_" + targetLanguage + "_" + hex;
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
