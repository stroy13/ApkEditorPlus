package brut.directory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class ZipRODirectoryMalformedPathTest {
    @Test
    public void recursiveListingSkipsRepeatedSeparatorsWithoutLooping() throws Exception {
        File apk = Files.createTempFile("malformed-apk-paths", ".zip").toFile();
        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(apk));
            put(out, "res/values/arrays.xml///fake.xml");
            put(out, "res/values/strings.xml");
            out.close();

            Set<String> entries = new ZipRODirectory(apk).getFiles(true);
            assertTrue(entries.contains("res/values/strings.xml"));
            assertFalse(entries.contains("res/values/arrays.xml///fake.xml"));
        } finally {
            apk.delete();
        }
    }

    private static void put(ZipOutputStream out, String name) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(1);
        out.closeEntry();
    }
}
