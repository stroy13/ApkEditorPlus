package brut.androlib.res.decoder;

import android.util.TypedValue;

import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResTypeSpec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ResAttrDecoderTest {
    private static final int ANDROID = 0x01000000;
    private static final int PROTECTION_LEVEL = ANDROID | 0x00010009;
    private static final int ICON = ANDROID | 0x00010002;
    private static final int CONFIG_CHANGES = ANDROID | 0x0001001f;
    private static final int LAUNCH_MODE = ANDROID | 0x0001001d;
    private static final int WINDOW_SOFT_INPUT_MODE = ANDROID | 0x0001022b;
    private static final int ENABLE_ON_BACK_INVOKED_CALLBACK = ANDROID | 0x0001066c;

    @Test
    public void decodesFrameworkEnumsAndFlagsWithoutAnArscPackage() throws Exception {
        ResAttrDecoder decoder = new ResAttrDecoder();

        assertEquals("signature", decoder.decode(TypedValue.TYPE_STRING, 0,
                "0x2", PROTECTION_LEVEL, "protectionLevel"));
        assertEquals("mcc|mnc|keyboard|keyboardHidden|orientation|screenLayout|screenSize|"
                        + "smallestScreenSize|layoutDirection",
                decoder.decode(TypedValue.TYPE_STRING, 0, "0x2db3", CONFIG_CHANGES,
                        "configChanges"));
        assertEquals("singleTask", decoder.decode(TypedValue.TYPE_STRING, 0, "2",
                LAUNCH_MODE, "launchMode"));
        assertEquals("adjustResize", decoder.decode(TypedValue.TYPE_STRING, 0, "0x10",
                WINDOW_SOFT_INPUT_MODE, "windowSoftInputMode"));
    }

    @Test
    public void normalizesUnresolvedReferencesToNullInsteadOfNumericLiterals() throws Exception {
        ResAttrDecoder decoder = new ResAttrDecoder();
        assertEquals("@null", decoder.decode(TypedValue.TYPE_STRING, 0,
                "@2131755264", ICON, "icon"));
        assertEquals("@null", decoder.decode(TypedValue.TYPE_STRING, 0,
                "@2130771973", ENABLE_ON_BACK_INVOKED_CALLBACK,
                "enableOnBackInvokedCallback"));
    }

    @Test
    public void keepsGeneratedProtectedResourceNamesSymbolic() throws Exception {
        ResTable table = new ResTable();
        ResPackage pkg = new ResPackage(table, 0x7f, "com.example");
        table.addPackage(pkg, true);
        ResTypeSpec drawable = new ResTypeSpec("drawable", table, pkg, 0x10, 1);
        pkg.addType(drawable);
        int id = 0x7f100000;
        ResResSpec spec = new ResResSpec(new ResID(id), null, pkg, drawable, true);
        pkg.addResSpec(spec);
        drawable.addResSpec(spec);

        ResAttrDecoder decoder = new ResAttrDecoder();
        decoder.setCurrentPackage(pkg);
        assertEquals("@drawable/noname_0x7f100000",
                decoder.decode(TypedValue.TYPE_REFERENCE, id, null, ICON, "icon"));
    }
}
