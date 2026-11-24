package brut.androlib.res.decoder;

import com.gmail.heagoo.apkeditor.util.ApkStructureValidator;

import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResTable;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the complete resource-table gate on a real Firefox APK when the CI
 * workflow provides one. The fixture is downloaded by CI and is intentionally not
 * committed to the repository.
 */
public class ARSCDecoderRealApkIntegrationTest {
    @Test
    public void validatesAndDecodesRealFirefoxResourceTable() throws Exception {
        String path = System.getProperty("apkeditor.realApk");
        if (path == null || path.isEmpty()) {
            path = System.getenv("APKEDITOR_REAL_APK");
        }
        boolean required = "true".equalsIgnoreCase(System.getenv("CI"));
        if (path == null || path.isEmpty()) {
            if (required) {
                throw new AssertionError("CI did not provide a real Firefox APK");
            }
            Assume.assumeTrue("real APK integration fixture is not configured", false);
            return;
        }

        File apkFile = new File(path);
        assertTrue("real Firefox APK does not exist: " + apkFile, apkFile.isFile());
        try (ZipFile apk = new ZipFile(apkFile)) {
            ZipEntry resources = apk.getEntry("resources.arsc");
            assertNotNull("real Firefox APK has no resources.arsc", resources);
            byte[] bytes;
            try (InputStream input = apk.getInputStream(resources)) {
                bytes = input.readAllBytes();
            }
            ApkStructureValidator.validateResources(bytes);

            ResTable table = new ResTable();
            ARSCDecoder.ARSCData decoded;
            try (InputStream input = apk.getInputStream(resources)) {
                decoded = ARSCDecoder.decode(input, false, false, table, null, false);
            }
            assertTrue("real Firefox resource table decoded no packages",
                    decoded.getPackages().length > 0);
            ResPackage package_ = decoded.getOnePackage();
            assertTrue("real Firefox resource table decoded no resource specs",
                    package_.getResSpecCount() > 0);
            assertTrue("real Firefox resource table decoded no values files",
                    package_.listValuesFiles().size() > 0);
            System.out.println("Real Firefox resources.arsc: " + bytes.length
                    + " bytes, packages=" + decoded.getPackages().length
                    + ", specs=" + package_.getResSpecCount()
                    + ", valuesFiles=" + package_.listValuesFiles().size()
                    + ", sparse=" + table.getSparseResources());
        }
    }
}
