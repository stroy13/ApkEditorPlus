package com.gmail.heagoo.apkeditor.translate;

import java.io.IOException;

/** Remove one complete leading reasoning block, never expose reasoning as a translation. */
public final class TranslationOutput {
    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";
    private TranslationOutput() {}

    public static boolean hasVisibleContent(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (!Character.isWhitespace(cp) && type != Character.CONTROL && type != Character.FORMAT
                    && type != Character.UNASSIGNED && type != Character.PRIVATE_USE
                    && type != Character.SURROGATE && type != Character.NON_SPACING_MARK
                    && type != Character.COMBINING_SPACING_MARK && type != Character.ENCLOSING_MARK) return true;
        }
        return false;
    }

    public static boolean containsCyrillic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC && Character.isLetter(cp)) return true;
        }
        return false;
    }

    public static String clean(String source, String output) throws IOException {
        if (output == null) throw new TranslationRejectedException(TranslationRejectedException.Reason.EMPTY, "Translation is empty");
        String text = output.trim();
        // Literal markers in source are resource content, not model protocol.
        if (source.contains(OPEN) || source.contains(CLOSE)) {
            if (text.contains(OPEN) || text.contains(CLOSE))
                throw new TranslationRejectedException(TranslationRejectedException.Reason.REASONING, "Ambiguous reasoning markers in source");
        } else if (text.startsWith(OPEN)) {
            int end = text.indexOf(CLOSE, OPEN.length());
            if (end < 0 || text.substring(OPEN.length(), end).contains(OPEN))
                throw new TranslationRejectedException(TranslationRejectedException.Reason.REASONING, "Incomplete or malformed reasoning block");
            text = text.substring(end + CLOSE.length()).trim();
        } else if (text.startsWith(CLOSE)) {
            // Some templates include the opening tag in the assistant prompt.
            // A leading close alone means there is no generated reasoning to discard.
            text = text.substring(CLOSE.length()).trim();
        }
        if (text.isEmpty() || !hasVisibleContent(text)) throw new TranslationRejectedException(TranslationRejectedException.Reason.EMPTY, "Translation is empty or invisible");
        if (text.contains(OPEN) || text.contains(CLOSE))
            throw new TranslationRejectedException(TranslationRejectedException.Reason.REASONING, "Unexpected or incomplete reasoning markers");
        if (!TranslationTokens.preserved(source, text))
            throw new TranslationRejectedException(TranslationRejectedException.Reason.PLACEHOLDER, "Translation changed a resource placeholder");
        return text;
    }
}
