package com.gmail.heagoo.apkeditor.translate;

/** Resolve explicit Android locale qualifiers without guessing a default resource language. */
public final class ResourceLanguageTag {
    private ResourceLanguageTag() {}
    public static String fromQualifier(String qualifier) {
        if (qualifier == null || !qualifier.matches("-[a-z]{2,3}(?:-r[A-Z]{2,3})?")) return null;
        return qualifier.substring(1).replace("-r", "-");
    }
    /** Language-only code for exact-match glossaries; discard Android region variants such as rXC. */
    public static String languageOnlyFromQualifier(String qualifier) {
        String tag = fromQualifier(qualifier);
        if (tag == null) return null;
        java.util.Locale locale = java.util.Locale.forLanguageTag(tag);
        String language = locale.getLanguage();
        return language == null || language.isEmpty() ? null : language;
    }
}
