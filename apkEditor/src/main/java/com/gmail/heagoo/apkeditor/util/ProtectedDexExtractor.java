package com.gmail.heagoo.apkeditor.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import brut.directory.Directory;
import brut.directory.DirectoryException;

/** Extracts asset-backed DEX files used by the libarm_protect loader. */
public final class ProtectedDexExtractor {
    private static final Pattern ASSET_DEX = Pattern.compile(
            "assets/classes(?:[2-9]|[1-9][0-9]+)?\\.dex");
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};

    private ProtectedDexExtractor() {}

    public static boolean isSupported(Directory directory) throws DirectoryException {
        if (directory == null) return false;
        boolean hasLoader = false;
        for (String entry : directory.getFiles(true)) {
            if (entry.endsWith("/libarm_protect.so")) {
                hasLoader = true;
                break;
            }
        }
        return hasLoader && !getAssetDexEntries(directory).isEmpty();
    }

    public static List<String> getAssetDexEntries(Directory directory) throws DirectoryException {
        if (directory == null) return Collections.emptyList();
        Set<String> entries = directory.getFiles(true);
        List<String> dexEntries = new ArrayList<>();
        for (String entry : entries) {
            if (ASSET_DEX.matcher(entry).matches()) dexEntries.add(entry);
        }
        Collections.sort(dexEntries);
        return dexEntries;
    }

    /**
     * This protector stores its classes*.dex payloads bitwise inverted in assets.
     * The native loader reverses each byte before adding the dex to the class path.
     */
    public static List<File> extract(Directory directory, File outputDir)
            throws DirectoryException, IOException {
        List<String> entries = getAssetDexEntries(directory);
        List<File> result = new ArrayList<>();
        if (entries.isEmpty()) return result;
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create DEX output directory: " + outputDir);
        }

        byte[] inputBuffer = new byte[64 * 1024];
        for (String entry : entries) {
            String outputName = entry.substring("assets/".length());
            File output = new File(outputDir, outputName);
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create DEX directory: " + parent);
            }
            try (InputStream in = directory.getFileInput(entry);
                 OutputStream out = new FileOutputStream(output)) {
                int count;
                long position = 0;
                byte[] prefix = new byte[DEX_MAGIC.length];
                int prefixCount = 0;
                while ((count = in.read(inputBuffer)) != -1) {
                    for (int i = 0; i < count; i++) {
                        inputBuffer[i] = (byte) ~inputBuffer[i];
                        if (position < DEX_MAGIC.length) prefix[prefixCount++] = inputBuffer[i];
                        position++;
                    }
                    out.write(inputBuffer, 0, count);
                }
                if (prefixCount != DEX_MAGIC.length || !startsWithDexMagic(prefix)) {
                    output.delete();
                    throw new IOException("Protected asset did not decode as DEX: " + entry);
                }
            }
            result.add(output);
        }
        return result;
    }

    private static boolean startsWithDexMagic(byte[] bytes) {
        for (int i = 0; i < DEX_MAGIC.length; i++) {
            if (bytes[i] != DEX_MAGIC[i]) return false;
        }
        return true;
    }
}
