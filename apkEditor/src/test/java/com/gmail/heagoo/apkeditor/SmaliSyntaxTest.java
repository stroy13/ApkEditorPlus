package com.gmail.heagoo.apkeditor;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class SmaliSyntaxTest {
    @Test
    public void highlightsInstructionRegistersTypesStringsAndComments() {
        String line = "    invoke-virtual {p0, v1}, Lcom/example/Thing;->call(ILjava/lang/String;)V # note";
        List<SmaliSyntax.Span> spans = SmaliSyntax.tokenize(line);

        assertHas(spans, SmaliSyntax.KEYWORD, "invoke-virtual", line);
        assertHas(spans, SmaliSyntax.REGISTER, "p0", line);
        assertHas(spans, SmaliSyntax.REGISTER, "v1", line);
        assertHas(spans, SmaliSyntax.DESCRIPTOR, "Lcom/example/Thing;", line);
        assertHas(spans, SmaliSyntax.DESCRIPTOR, "Ljava/lang/String;", line);
        assertHas(spans, SmaliSyntax.KEYWORD_TYPE, "I", line);
        assertHas(spans, SmaliSyntax.KEYWORD_TYPE, "V", line);
        assertHas(spans, SmaliSyntax.COMMENT, "# note", line);
    }

    @Test
    public void highlightsDirectivesLabelsStringsAndNumbers() {
        String line = ":cond_0\n";
        assertHas(SmaliSyntax.tokenize(line.substring(0, line.length() - 1)),
                SmaliSyntax.LABEL, ":cond_0", line.substring(0, line.length() - 1));
        assertHas(SmaliSyntax.tokenize("    .class public LExample;"),
                SmaliSyntax.MARKUP, ".class", "    .class public LExample;");
        assertHas(SmaliSyntax.tokenize("    const/4 v0, 0x7f"),
                SmaliSyntax.DIGIT, "0x7f", "    const/4 v0, 0x7f");
        assertHas(SmaliSyntax.tokenize("    const-string v0, \"hello\""),
                SmaliSyntax.STRING, "\"hello\"", "    const-string v0, \"hello\"");
        assertHas(SmaliSyntax.tokenize("    .field value:Lcom/example/Thing;"),
                SmaliSyntax.DESCRIPTOR, "Lcom/example/Thing;", "    .field value:Lcom/example/Thing;");
        assertHas(SmaliSyntax.tokenize("    invoke-static {[Ljava/lang/String;}, LExample;->run([I)V"),
                SmaliSyntax.DESCRIPTOR, "Ljava/lang/String;", "    invoke-static {[Ljava/lang/String;}, LExample;->run([I)V");
        assertHas(SmaliSyntax.tokenize("    invoke-static {[Ljava/lang/String;}, LExample;->run([I)V"),
                SmaliSyntax.KEYWORD_TYPE, "I", "    invoke-static {[Ljava/lang/String;}, LExample;->run([I)V");
    }

    private static void assertHas(List<SmaliSyntax.Span> spans, int token, String value, String line) {
        for (SmaliSyntax.Span span : spans) {
            if (span.token == token && value.equals(line.substring(span.start, span.end))) {
                return;
            }
        }
        assertTrue("missing token " + token + " for " + value, false);
    }
}
