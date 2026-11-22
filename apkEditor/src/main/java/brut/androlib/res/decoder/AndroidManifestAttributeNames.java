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
        names.put(0x01010281, "glEsVersion");
        NAMES = Collections.unmodifiableMap(names);
    }

    private AndroidManifestAttributeNames() { }

    static String nameFor(int resourceId) {
        return NAMES.get(resourceId);
    }

    static boolean isFrameworkAttribute(int resourceId) {
        return (resourceId & 0xff000000) == 0x01000000;
    }

    static boolean isValidAttributeName(String name) {
        if (name == null || name.isEmpty() || !isNameStart(name.charAt(0))) return false;
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
