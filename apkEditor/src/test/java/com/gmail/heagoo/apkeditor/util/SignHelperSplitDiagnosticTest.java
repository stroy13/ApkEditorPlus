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

    @Test public void detectsSplitIdentityAttributes() {
        ManifestInfo named = new ManifestInfo();
        named.splitName = "config.arm64_v8a";
        assertEquals("This archive is a split APK (split=config.arm64_v8a); PackageManager parses a standalone file as a base APK; build from a universal APK or provide the complete split set",
                SignHelper.splitArchiveDiagnostic(named));

        ManifestInfo feature = new ManifestInfo();
        feature.isFeatureSplit = true;
        assertEquals("This archive is a split APK; PackageManager parses a standalone file as a base APK; build from a universal APK or provide the complete split set",
                SignHelper.splitArchiveDiagnostic(feature));
    }

    @Test public void doesNotFlagStandaloneOrAlreadyCombinedSplits() {
        assertNull(SignHelper.splitArchiveDiagnostic(new ManifestInfo()));
        ManifestInfo combined = new ManifestInfo();
        combined.isSplitRequired = true;
        combined.splitTypes = "base__abi,base__density";
        assertNull(SignHelper.splitArchiveDiagnostic(combined));
    }
}
