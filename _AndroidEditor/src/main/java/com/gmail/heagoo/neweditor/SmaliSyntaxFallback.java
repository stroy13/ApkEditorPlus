package com.gmail.heagoo.neweditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Conservative fallback for the smali mode when an OEM XML parser rejects the
 * bundled syntax definition. It deliberately uses the same token ids as
 * {@code smali.xml} and keeps every span inside one physical line.
 */
final class SmaliSyntaxFallback {
    private static final int COMMENT = 1;
    private static final int DIGIT = 5;
    private static final int KEYWORD = 8;
    private static final int KEYWORD_TYPE = 10;
    private static final int LABEL = 12;
    private static final int STRING = 13;
    private static final int DESCRIPTOR = 14;
    private static final int REGISTER = 15;
    private static final int MARKUP = 17;
    private static final int OPERATOR = 18;

    private SmaliSyntaxFallback() {
    }

    static SyntaxSnapshot parse(String text, SyntaxSnapshot.Color colors,
                                BooleanSupplier cancelled) {
        ArrayList<SyntaxSnapshot.Line> lines = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (true) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? length : newline;
            ArrayList<SyntaxSnapshot.Span> spans = new ArrayList<>();
            tokenize(text.substring(start, end), start, spans, colors);
            lines.add(new SyntaxSnapshot.Line(start, end, spans));
            if (newline < 0) {
                break;
            }
            start = newline + 1;
            if (start > length) {
                break;
            }
        }
        return SyntaxSnapshot.create(lines, length);
    }

    private static void tokenize(String line, int offset,
                                  List<SyntaxSnapshot.Span> spans,
                                  SyntaxSnapshot.Color colors) {
        int n = line.length();
        int i = 0;
        boolean first = true;
        while (i < n) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '#') {
                add(spans, offset, i, n, COMMENT, colors, line);
                return;
            }
            if (c == '\'' || c == '"') {
                int end = quotedEnd(line, i, c);
                add(spans, offset, i, end, STRING, colors, line);
                i = end;
                first = false;
                continue;
            }
            if (first) {
                if (c == ':') {
                    int end = wordEnd(line, i + 1);
                    add(spans, offset, i, end, LABEL, colors, line);
                    i = end;
                    first = false;
                    continue;
                }
                if (c == '.') {
                    int end = wordEnd(line, i + 1);
                    add(spans, offset, i, end, MARKUP, colors, line);
                    i = end;
                    first = false;
                    continue;
                }
                int end = instructionEnd(line, i);
                if (end > i) {
                    add(spans, offset, i, end, KEYWORD, colors, line);
                    i = end;
                    first = false;
                    continue;
                }
                first = false;
            }
            if (c == ':' && boundary(line, i)) {
                int end = wordEnd(line, i + 1);
                add(spans, offset, i, end, LABEL, colors, line);
                i = end;
                continue;
            }
            if (c == 'L' && descriptorBoundary(line, i)) {
                int end = line.indexOf(';', i + 1);
                if (end >= 0) {
                    add(spans, offset, i, end + 1, DESCRIPTOR, colors, line);
                    i = end + 1;
                    continue;
                }
            }
            if ((c == 'v' || c == 'p') && boundary(line, i)) {
                int end = i + 1;
                while (end < n && Character.isDigit(line.charAt(end))) {
                    end++;
                }
                if (end > i + 1) {
                    add(spans, offset, i, end, REGISTER, colors, line);
                    i = end;
                    continue;
                }
            }
            if (isType(c) && typeBoundary(line, i)) {
                add(spans, offset, i, i + 1, KEYWORD_TYPE, colors, line);
                i++;
                continue;
            }
            if (Character.isDigit(c)
                    || ((c == '-' || c == '+') && i + 1 < n
                    && Character.isDigit(line.charAt(i + 1)))) {
                int end = numberEnd(line, i);
                add(spans, offset, i, end, DIGIT, colors, line);
                i = end;
                continue;
            }
            if (c == '-' && i + 1 < n && line.charAt(i + 1) == '>') {
                add(spans, offset, i, i + 2, OPERATOR, colors, line);
                i += 2;
                continue;
            }
            i++;
        }
    }

    private static void add(List<SyntaxSnapshot.Span> spans, int offset,
                            int start, int end, int token,
                            SyntaxSnapshot.Color colors, String line) {
        if (end <= start || spans.size() >= 4096) {
            return;
        }
        Token value = new Token((byte) token, start, end - start, null);
        spans.add(new SyntaxSnapshot.Span(offset + start, offset + end,
                colors.color(value)));
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
        while (i < text.length() && (nameChar(text.charAt(i)) || text.charAt(i) == ':')) {
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
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.'
                    || c == '+' || c == '-')) {
                break;
            }
            i++;
        }
        return i;
    }

    private static boolean boundary(String text, int index) {
        if (index == 0) {
            return true;
        }
        char previous = text.charAt(index - 1);
        return Character.isWhitespace(previous) || "{[(,=;:)]>".indexOf(previous) >= 0;
    }

    private static boolean typeBoundary(String text, int index) {
        if (!boundary(text, index)) {
            return false;
        }
        if (index + 1 == text.length()) {
            return true;
        }
        char next = text.charAt(index + 1);
        return Character.isWhitespace(next) || "[]();{},:;".indexOf(next) >= 0
                || isType(next);
    }

    private static boolean descriptorBoundary(String text, int index) {
        if (boundary(text, index)) {
            return true;
        }
        return "ZBSCIJFDV".indexOf(text.charAt(index - 1)) >= 0;
    }

    private static boolean nameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-'
                || c == '/' || c == '$' || c == '.';
    }

    private static boolean isType(char c) {
        return "ZBSCIJFDV".indexOf(c) >= 0;
    }
}
