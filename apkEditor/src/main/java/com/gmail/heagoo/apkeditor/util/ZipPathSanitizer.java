package com.gmail.heagoo.apkeditor.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Rewrites only ZIP filenames that Android's ZipFile refuses to enumerate. */
public final class ZipPathSanitizer {
    private static final long EOCD = 0x06054b50L;
    private static final long CENTRAL = 0x02014b50L;
    private static final long LOCAL = 0x04034b50L;
    private static final long UINT32_MAX = 0xffffffffL;

    private ZipPathSanitizer() {}

    /** Returns source unchanged when it has no absolute entries; otherwise a safe cached copy. */
    public static File sanitize(File source, File cacheDir) throws IOException {
        List<EntryName> absoluteNames = findAbsoluteNames(source);
        if (absoluteNames.isEmpty()) return source;
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
            throw new IOException("Cannot create ZIP sanitization cache");
        }
        File safe = File.createTempFile("apk-safe-", ".apk", cacheDir);
        boolean success = false;
        try {
            Files.copy(source.toPath(), safe.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (RandomAccessFile raf = new RandomAccessFile(safe, "rw")) {
                for (EntryName entry : absoluteNames) {
                    raf.seek(entry.centralNameOffset);
                    raf.write('_');
                    raf.seek(entry.localHeaderOffset);
                    if (u32(raf) != LOCAL) throw new IOException("Invalid local ZIP header");
                    raf.seek(entry.localHeaderOffset + 26);
                    int nameLength = u16(raf);
                    if (nameLength < 1) throw new IOException("Empty ZIP entry name");
                    raf.seek(entry.localHeaderOffset + 30);
                    if (raf.read() != '/') throw new IOException("ZIP local and central names differ");
                    raf.seek(entry.localHeaderOffset + 30);
                    raf.write('_');
                }
            }
            success = true;
            return safe;
        } finally {
            if (!success) safe.delete();
        }
    }

    private static List<EntryName> findAbsoluteNames(File source) throws IOException {
        List<EntryName> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(source, "r")) {
            long length = raf.length();
            long lowerBound = Math.max(0, length - 65557L);
            long eocd = -1;
            for (long pos = length - 22; pos >= lowerBound; pos--) {
                raf.seek(pos);
                if (u32(raf) == EOCD) {
                    raf.seek(pos + 20);
                    int commentLength = u16(raf);
                    if (pos + 22L + commentLength <= length) {
                        eocd = pos;
                        break;
                    }
                }
            }
            if (eocd < 0) throw new IOException("ZIP end-of-central-directory not found");
            raf.seek(eocd + 10);
            int entries = u16(raf);
            long centralSize = u32(raf);
            long centralOffset = u32(raf);
            if (centralSize == UINT32_MAX || centralOffset == UINT32_MAX || entries == 0xffff) {
                throw new IOException("ZIP64 APKs with absolute entry paths are unsupported");
            }
            long end = centralOffset + centralSize;
            long pos = centralOffset;
            for (int i = 0; i < entries && pos < end; i++) {
                raf.seek(pos);
                if (u32(raf) != CENTRAL) throw new IOException("Invalid ZIP central-directory entry");
                raf.seek(pos + 28);
                int nameLength = u16(raf);
                int extraLength = u16(raf);
                int commentLength = u16(raf);
                raf.seek(pos + 42);
                long localOffset = u32(raf);
                if (localOffset == UINT32_MAX) throw new IOException("ZIP64 entry offset is unsupported");
                long nameOffset = pos + 46;
                if (nameLength > 0) {
                    raf.seek(nameOffset);
                    int first = raf.read();
                    if (first == '/') result.add(new EntryName(nameOffset, localOffset));
                }
                pos = nameOffset + nameLength + extraLength + commentLength;
            }
            if (pos > end) throw new IOException("ZIP central-directory size mismatch");
        }
        return result;
    }

    private static int u16(RandomAccessFile raf) throws IOException {
        int a = raf.read();
        int b = raf.read();
        if (a < 0 || b < 0) throw new IOException("Truncated ZIP structure");
        return a | (b << 8);
    }

    private static long u32(RandomAccessFile raf) throws IOException {
        long a = u16(raf);
        long b = u16(raf);
        return a | (b << 16);
    }

    private static final class EntryName {
        final long centralNameOffset;
        final long localHeaderOffset;

        EntryName(long centralNameOffset, long localHeaderOffset) {
            this.centralNameOffset = centralNameOffset;
            this.localHeaderOffset = localHeaderOffset;
        }
    }
}
