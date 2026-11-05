/* JCuda - Java bindings for CUDA - http://www.jcuda.org */
package jcuda.test;

import static jcuda.nvrtc.JNvrtc.*;
import static jcuda.nvrtc.nvrtcResult.*;
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

/** Queries compiler metadata only; no GPU or program compilation is needed. */
@RunWith(Parameterized.class)
public class JNvrtcArchitectureCapacityTest
{
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases()
    {
        return Arrays.asList(new Object[][] {
            {"short"}, {"empty"}, {"exact"}, {"padded"}, {"null"},
            {"exceptions"}, {"count-empty"}, {"count-null"}, {"count-padded"}
        });
    }
    private final String mode;
    public JNvrtcArchitectureCapacityTest(String mode) { this.mode = mode; }

    @Test public void respectsCapacity() throws Exception
    {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        File log = File.createTempFile("jcuda-arch-capacity-", ".log");
        Process p = new ProcessBuilder(new File(new File(System.getProperty("java.home"), "bin"), executable).getPath(),
            "-Xcheck:jni", "-Djava.library.path=" + System.getProperty("java.library.path"), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            getClass().getName(), mode).redirectErrorStream(true).redirectOutput(log).start();
        try
        {
            assertTrue("Architecture query child timed out", p.waitFor(30, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(output, 0, p.exitValue());
            assertTrue(output, output.contains("ARCH_CAPACITY_OK"));
            assertFalse(output, output.contains("WARNING in native method"));
        }
        finally
        {
            if (p.isAlive()) p.destroyForcibly();
            log.delete();
        }
    }

    public static void main(String[] args)
    {
        String mode = args[0];
        setExceptionsEnabled(mode.equals("exceptions"));
        int[] count = {-1};
        assertEquals(NVRTC_SUCCESS, nvrtcGetNumSupportedArchs(count));
        assertTrue(count[0] > 0);
        if (mode.equals("null"))
            assertThrows(NullPointerException.class, () -> nvrtcGetSupportedArchs(null));
        else if (mode.equals("count-null"))
            assertThrows(NullPointerException.class, () -> nvrtcGetNumSupportedArchs(null));
        else if (mode.equals("count-empty"))
            assertThrows(IllegalArgumentException.class, () -> nvrtcGetNumSupportedArchs(new int[0]));
        else if (mode.equals("count-padded"))
        {
            int[] padded = {-1, 0x51A7, 0x51A7};
            assertEquals(NVRTC_SUCCESS, nvrtcGetNumSupportedArchs(padded));
            assertArrayEquals(new int[] {count[0], 0x51A7, 0x51A7}, padded);
        }
        else if (mode.equals("short") || mode.equals("empty") || mode.equals("exceptions"))
        {
            int[] output = new int[mode.equals("empty") ? 0 : count[0] - 1];
            Arrays.fill(output, 0x51A7);
            int[] before = output.clone();
            assertThrows(IllegalArgumentException.class, () -> nvrtcGetSupportedArchs(output));
            assertArrayEquals(before, output);
        }
        else
        {
            int[] exact = new int[count[0]];
            assertEquals(NVRTC_SUCCESS, nvrtcGetSupportedArchs(exact));
            assertTrue(exact[0] > 0);
            for (int i = 1; i < exact.length; i++) assertTrue(exact[i] > exact[i - 1]);
            if (mode.equals("padded"))
            {
                int[] padded = new int[exact.length + 16];
                Arrays.fill(padded, 0x51A7);
                assertEquals(NVRTC_SUCCESS, nvrtcGetSupportedArchs(padded));
                assertArrayEquals(exact, Arrays.copyOf(padded, exact.length));
                for (int i = exact.length; i < padded.length; i++)
                    assertEquals("Trailing element " + i, 0x51A7, padded[i]);
            }
        }
        System.out.println("ARCH_CAPACITY_OK " + mode);
    }
}
