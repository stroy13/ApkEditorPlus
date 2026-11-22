package brut.androlib.res.decoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Small framework-attribute fallback for manifests decoded without android.jar. */
final class AndroidManifestAttributeNames {
    private static final Map<Integer, String> NAMES;

    static {
        Map<Integer, String> names = new HashMap<>();
        names.put(0x01010003, "name");
        names.put(0x01010009, "protectionLevel");
        names.put(0x0101000b, "sharedUserId");
        names.put(0x01010026, "mimeType");
        names.put(0x0101020c, "minSdkVersion");
        names.put(0x0101021b, "versionCode");
        names.put(0x0101021c, "versionName");
        names.put(0x01010270, "targetSdkVersion");
        names.put(0x01010271, "maxSdkVersion");
        names.put(0x01010281, "glEsVersion");
        names.put(0x0101028e, "required");
        names.put(0x01010572, "compileSdkVersion");
        names.put(0x01010573, "compileSdkVersionCodename");
        names.put(0x0101064e, "requiredSplitTypes");
        names.put(0x0101064f, "splitTypes");
        NAMES = Collections.unmodifiableMap(names);
    }

    private AndroidManifestAttributeNames() { }

    public static String nameFor(int resourceId) {
        return NAMES.get(resourceId);
    }

    static boolean isFrameworkAttribute(int resourceId) {
        return (resourceId & 0xff000000) == 0x01000000;
    }

    static boolean isValidAttributeName(String name) {
        if (name == null || name.isEmpty() || name.matches("attr_0x[0-9a-fA-F]{8}")
                || !isNameStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isNameStart(c) && !(c >= '0' && c <= '9') && c != '-' && c != '.') return false;
        }
        return true;
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }
}
