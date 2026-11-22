package brut.androlib.res.decoder;

import android.util.TypedValue;

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
    public void normalizesUnresolvedReferencesToHexResourceIds() throws Exception {
        ResAttrDecoder decoder = new ResAttrDecoder();
        assertEquals("@0x7f100100", decoder.decode(TypedValue.TYPE_STRING, 0,
                "@2131755264", ICON, "icon"));
        assertEquals("@0x7f010005", decoder.decode(TypedValue.TYPE_STRING, 0,
                "@2130771973", ENABLE_ON_BACK_INVOKED_CALLBACK,
                "enableOnBackInvokedCallback"));
    }
}
