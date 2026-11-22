package com.gmail.heagoo.apkeditor.util;

import com.gmail.heagoo.apkeditor.ce.ManifestInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SignHelperSplitDiagnosticTest {
    @Test public void explainsBaseSplitThatCannotBeParsedAsStandaloneApk() {
        ManifestInfo manifest = new ManifestInfo();
        manifest.isSplitRequired = true;
        manifest.requiredSplitTypes = "base__abi,base__density";
        assertEquals("This is a split APK base requiring additional APK splits (required: base__abi,base__density); build from a universal APK or provide the complete split set",
                SignHelper.splitArchiveDiagnostic(manifest));
    }

    @Test public void doesNotFlagStandaloneOrAlreadyCombinedSplits() {
        assertNull(SignHelper.splitArchiveDiagnostic(new ManifestInfo()));
        ManifestInfo combined = new ManifestInfo();
        combined.isSplitRequired = true;
        combined.splitTypes = "base__abi,base__density";
        assertNull(SignHelper.splitArchiveDiagnostic(combined));
    }
}
