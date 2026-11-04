/* JCuda - Java bindings for CUDA - http://www.jcuda.org */
package jcuda.test;

import static jcuda.driver.JCudaDriver.*;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;

/** Isolates primary-context ownership from other tests/runtime initialization. */
@RunWith(Parameterized.class)
public class JCudaPrimaryContextLifetimeTest {
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {{"fresh"}, {"retained"}});
    }
    private final String mode;
    public JCudaPrimaryContextLifetimeTest(String mode) { this.mode = mode; }

    @Test
    public void balancesOnlyItsRetainedReference() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = new File(new File(System.getProperty("java.home"), "bin"), executable).getPath();
        File log = File.createTempFile("jcuda-primary-lifetime-", ".log");
        Process child = new ProcessBuilder(java, "-Xcheck:jni",
            "-Djava.library.path=" + System.getProperty("java.library.path"), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            getClass().getName(), mode).redirectErrorStream(true).redirectOutput(log).start();
        try {
            assertTrue("Primary-context child timed out", child.waitFor(30, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(output, 0, child.exitValue());
            assertTrue(output, output.contains("PRIMARY_LIFETIME_OK"));
        } finally {
            if (child.isAlive()) child.destroyForcibly();
            log.delete();
        }
    }

    private static int active(CUdevice device) {
        int[] flags = {0}, active = {-1};
        cuDevicePrimaryCtxGetState(device, flags, active);
        return active[0];
    }

    public static void main(String[] args) {
        setExceptionsEnabled(true);
        cuInit(0);
        CUdevice device = new CUdevice(); cuDeviceGet(device, 0);
        assertEquals("Fresh child starts without a primary owner", 0, active(device));
        boolean retained = "retained".equals(args[0]);
        if (retained) cuDevicePrimaryCtxRetain(new CUcontext(), device);
        try {
            for (int repeat = 0; repeat < 3; repeat++) {
                new JCudaDriverPrimaryContextTest().testPrimaryContextCreation();
                assertEquals("Test must preserve its caller's ownership, iteration " + repeat,
                    retained ? 1 : 0, active(device));
            }
        } finally {
            if (retained) cuDevicePrimaryCtxRelease(device);
        }
        assertEquals("No reference leaked after caller cleanup", 0, active(device));
        System.out.println("PRIMARY_LIFETIME_OK");
    }
}
