package com.gmail.heagoo.apkeditor;

import com.gmail.heagoo.neweditor.SyntaxSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyntaxSnapshotTest {
    @Test(timeout = 5000)
    public void parsesEveryLineAndKeepsTrailingEmptyLine() {
        SyntaxSnapshot snapshot = SyntaxSnapshot.parse(
                "first\nsecond\n", null, token -> 0, () -> false);

        assertEquals(3, snapshot.lines.size());
        assertEquals(0, snapshot.lines.get(0).start);
        assertEquals(6, snapshot.lines.get(1).start);
        assertEquals(13, snapshot.lines.get(2).start);
        assertEquals(13, snapshot.length);
    }
}
