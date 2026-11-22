package brut.androlib.res.decoder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AndroidManifestAttributeNamesTest {
    @Test
    public void recoversRequiredManifestAttributesWhenFrameworkTableIsUnavailable() {
        assertEquals("name", AndroidManifestAttributeNames.nameFor(0x01010003));
        assertEquals("glEsVersion", AndroidManifestAttributeNames.nameFor(0x01010281));
        assertTrue(AndroidManifestAttributeNames.isFrameworkAttribute(0x01010003));
        assertFalse(AndroidManifestAttributeNames.isFrameworkAttribute(0x7f010003));
        assertEquals(null, AndroidManifestAttributeNames.nameFor(0x01019999));
        assertTrue(AndroidManifestAttributeNames.isValidAttributeName("compileSdkVersion"));
        assertFalse(AndroidManifestAttributeNames.isValidAttributeName("attr_0x01010003"));
        assertFalse(AndroidManifestAttributeNames.isValidAttributeName("bad name"));
    }
}
