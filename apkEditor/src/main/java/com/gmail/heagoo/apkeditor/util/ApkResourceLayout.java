package com.gmail.heagoo.apkeditor.util;

import brut.directory.Directory;
import java.util.Set;
import java.util.regex.Pattern;

/** Distinguishes real Android resource trees from protector-created decoy res directories. */
public final class ApkResourceLayout {
    private static final Pattern RESOURCE_FOLDER = Pattern.compile(
            "(anim|animator|color|drawable|font|interpolator|layout|menu|mipmap|navigation|raw|transition|values|xml)(-[A-Za-z0-9_-]+)?");
    private static final Pattern RESOURCE_FILE = Pattern.compile("[A-Za-z0-9_.]+(\\.[A-Za-z0-9_.-]+)?");

    private ApkResourceLayout() {}

    /**
     * Real compiled APK resources have files directly under res/<resource-type>/.
     * Some protectors add a fake res/values/*.xml/ tree full of decoy files; simply
     * checking containsDir("res") incorrectly sends those APKs through normal decode.
     */
    public static boolean hasStandardResourceFiles(Directory apk) {
        if (apk == null || !apk.containsDir("res")) return false;
        Set<String> files = apk.getFiles(true);
        for (String path : files) {
            if (isStandardResourcePath(path)) return true;
        }
        return false;
    }

    static boolean isStandardResourcePath(String path) {
        if (path == null || !path.startsWith("res/")) return false;
        String relative = path.substring(4);
        int slash = relative.indexOf('/');
        if (slash <= 0 || slash == relative.length() - 1 || relative.indexOf('/', slash + 1) >= 0) {
            return false;
        }
        String folder = relative.substring(0, slash);
        String filename = relative.substring(slash + 1);
        if (!RESOURCE_FOLDER.matcher(folder).matches() || !RESOURCE_FILE.matcher(filename).matches()) {
            return false;
        }
        return !folder.startsWith("values") || filename.endsWith(".xml");
    }
}
