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
}
