package brut.androlib.res.decoder;

import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the resource decoder against the installed modern Android framework table.
 * Synthetic sparse tables cover individual encodings; this test catches framework
 * chunk and entry forms that are only present in a current SDK platform.
 */
public class ARSCDecoderFrameworkIntegrationTest {
    @Test
    public void decodesInstalledApi36OrNewerFrameworkTable() throws Exception {
        File frameworkJar = findModernFrameworkJar();
        boolean required = "true".equalsIgnoreCase(System.getenv("CI"))
                || Boolean.getBoolean("apkeditor.frameworkTest");
        if (frameworkJar == null) {
            if (required) {
                throw new AssertionError("No Android API 36+ platform was installed for framework parsing");
            }
            Assume.assumeTrue("Android API 36+ SDK platform is not installed", false);
            return;
        }

        try (ZipFile apk = new ZipFile(frameworkJar)) {
            ZipEntry resources = apk.getEntry("resources.arsc");
            assertNotNull("android.jar has no resources.arsc", resources);
            ResTable table = new ResTable();
            ARSCDecoder.ARSCData decoded;
            try (InputStream input = apk.getInputStream(resources)) {
                // Framework tables may contain resource forms newer than this app's
                // resource model. keepBroken must keep the parser gate alive while
                // still rejecting impossible chunk boundaries and allocations.
                decoded = ARSCDecoder.decode(input, false, true, table, null, false);
            }
            assertTrue("API 36+ framework table decoded no packages",
                    decoded.getPackages().length > 0);
            boolean hasFrameworkPackage = false;
            for (ResPackage pkg : decoded.getPackages()) {
                if (pkg != null && pkg.getId() == 1) {
                    hasFrameworkPackage = true;
                    break;
                }
            }
            assertTrue("API 36+ framework table has no android package", hasFrameworkPackage);
        }
    }

    private static File findModernFrameworkJar() {
        String sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk == null || sdk.isEmpty()) {
            sdk = System.getenv("ANDROID_HOME");
        }
        if (sdk == null || sdk.isEmpty()) {
            return null;
        }
        File platforms = new File(sdk, "platforms");
        File[] candidates = platforms.listFiles(file -> file.isDirectory()
                && file.getName().matches("android-\\d+(?:\\.\\d+)?"));
        if (candidates == null) {
            return null;
        }
        Arrays.sort(candidates, Comparator.comparing(File::getName).reversed());
        for (File candidate : candidates) {
            String suffix = candidate.getName().substring("android-".length());
            int dot = suffix.indexOf('.');
            int api = Integer.parseInt(dot < 0 ? suffix : suffix.substring(0, dot));
            File jar = new File(candidate, "android.jar");
            if (api >= 36 && jar.isFile()) {
                return jar;
            }
        }
        return null;
    }
}
