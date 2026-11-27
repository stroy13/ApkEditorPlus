package com.gmail.heagoo.apkeditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, line-oriented smali tokenizer for the comparison viewer.
 *
 * <p>The full editor mode is defined by {@code assets/syntax/smali.xml}. The
 * diff viewer only needs spans, so this tokenizer keeps the same jEdit token
 * ids for the constructs visible on a single smali line: instructions,
 * directives, comments, strings, descriptors, registers, labels and numbers.
 * Smali strings and comments are explicitly non-line-spanning in that mode.</p>
 */
public final class SmaliSyntax {
    public static final int COMMENT = 1;
    public static final int DIGIT = 5;
    public static final int KEYWORD = 8;
    public static final int KEYWORD_TYPE = 10;
    public static final int LABEL = 12;
    public static final int STRING = 13;
    public static final int DESCRIPTOR = 14;
    public static final int REGISTER = 15;
    public static final int MARKUP = 17;
    public static final int OPERATOR = 18;

    public static final class Span {
        public final int end;
        public final int start;
        public final int token;

        Span(int start, int end, int token) {
            this.start = start;
            this.end = end;
            this.token = token;
        }
    }

    public static List<Span> tokenize(String text) {
        List<Span> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        int n = text.length();
        int i = 0;
        boolean firstCodeToken = true;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '#') {
                add(result, i, n, COMMENT);
                break;
            }
            if (c == '\'' || c == '"') {
                int end = quotedEnd(text, i, c);
                add(result, i, end, STRING);
                i = end;
                firstCodeToken = false;
                continue;
            }
            if (firstCodeToken) {
                if (c == ':') {
                    int end = wordEnd(text, i + 1);
                    add(result, i, end, LABEL);
                    i = end;
                    firstCodeToken = false;
                    continue;
                }
                if (c == '.') {
                    int end = wordEnd(text, i + 1);
                    add(result, i, end, MARKUP);
                    i = end;
                    firstCodeToken = false;
                    continue;
                }
                int end = instructionEnd(text, i);
                if (end > i) {
                    add(result, i, end, KEYWORD);
                    i = end;
                    firstCodeToken = false;
                    continue;
                }
                firstCodeToken = false;
            }
            if (c == ':' && isBoundary(text, i)) {
                int end = wordEnd(text, i + 1);
                add(result, i, end, LABEL);
                i = end;
                continue;
            }
            if (c == 'L' && isDescriptorBoundary(text, i)) {
                int end = text.indexOf(';', i + 1);
                if (end >= 0) {
                    add(result, i, end + 1, DESCRIPTOR);
                    i = end + 1;
                    continue;
                }
            }
            if ((c == 'v' || c == 'p') && isBoundary(text, i)) {
                int end = i + 1;
                while (end < n && Character.isDigit(text.charAt(end))) {
                    end++;
                }
                if (end > i + 1) {
                    add(result, i, end, REGISTER);
                    i = end;
                    continue;
                }
            }
            if (isType(c) && isTypeBoundary(text, i)) {
                add(result, i, i + 1, KEYWORD_TYPE);
                i++;
                continue;
            }
            if (Character.isDigit(c)
                    || ((c == '-' || c == '+') && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int end = numberEnd(text, i);
                add(result, i, end, DIGIT);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < n && text.charAt(i + 1) == '>') {
                add(result, i, i + 2, OPERATOR);
                i += 2;
                continue;
            }
            i++;
        }
        return result;
    }

    private static void add(List<Span> spans, int start, int end, int token) {
        if (end > start && spans.size() < 4096) {
            spans.add(new Span(start, end, token));
        }
    }

    private static int quotedEnd(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += Math.min(2, text.length() - i);
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static int wordEnd(String text, int start) {
        int i = start;
        while (i < text.length() && (isNameChar(text.charAt(i)) || text.charAt(i) == ':')) {
            i++;
        }
        return i;
    }

    private static int instructionEnd(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == '{' || c == '}'
                    || c == '(' || c == ')' || c == ':' || c == '#') {
                break;
            }
            i++;
        }
        return i;
    }

    private static int numberEnd(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '+' || c == '-')) {
                break;
            }
            i++;
        }
        return i;
    }

    private static boolean isBoundary(String text, int index) {
        if (index == 0) {
            return true;
        }
        char previous = text.charAt(index - 1);
        return Character.isWhitespace(previous) || "{[(,=;:)]>".indexOf(previous) >= 0;
    }

    private static boolean isTypeBoundary(String text, int index) {
        if (!isBoundary(text, index)) {
            return false;
        }
        if (index + 1 == text.length()) {
            return true;
        }
        char next = text.charAt(index + 1);
        return Character.isWhitespace(next) || "[]();{},:;".indexOf(next) >= 0
                || next == 'L' || isType(next);
    }

    private static boolean isDescriptorBoundary(String text, int index) {
        if (isBoundary(text, index)) {
            return true;
        }
        char previous = text.charAt(index - 1);
        return "ZBSCIJFDV".indexOf(previous) >= 0;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/' || c == '$' || c == '.';
    }

    private static boolean isType(char c) {
        return "ZBSCIJFDV".indexOf(c) >= 0;
    }

    private SmaliSyntax() {
    }
}
