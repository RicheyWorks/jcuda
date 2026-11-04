/* JCuda - Java bindings for CUDA - http://www.jcuda.org */
package jcuda.test;

import static jcuda.driver.JCudaDriver.*;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import jcuda.CudaException;
import jcuda.driver.CUdevice;
import jcuda.driver.CUresult;
import jcuda.runtime.JCuda;
import jcuda.runtime.cudaError;

/** Device metadata only: no context creation, allocation, launch or reset. */
@RunWith(Parameterized.class)
public class JCudaPciBusIdTest
{
    @Parameterized.Parameters(name = "{0}/{1}")
    public static Collection<Object[]> cases()
    {
        Collection<Object[]> cases = new ArrayList<Object[]>();
        for (String api : new String[] {"driver", "runtime"})
            for (String mode : new String[] {"invalid", "exceptions", "negative", "zero", "empty", "null", "valid", "short"})
                cases.add(new Object[] {api, mode});
        cases.add(new Object[] {"driver", "uninitialized"});
        cases.add(new Object[] {"driver", "null-device"});
        return cases;
    }
    private final String api, mode;
    public JCudaPciBusIdTest(String api, String mode) { this.api = api; this.mode = mode; }

    @Test public void preservesOutputsOnError() throws Exception
    {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        File log = File.createTempFile("jcuda-pci-", ".log");
        Process p = new ProcessBuilder(new File(new File(System.getProperty("java.home"), "bin"), executable).getPath(),
            "-Xcheck:jni", "-Djava.library.path=" + System.getProperty("java.library.path"), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            getClass().getName(), api, mode).redirectErrorStream(true).redirectOutput(log).start();
        try
        {
            assertTrue("PCI query child timed out", p.waitFor(30, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(output, 0, p.exitValue());
            assertTrue(output, output.contains("PCI_QUERY_OK"));
            assertFalse(output, output.contains("WARNING in native method"));
        }
        finally
        {
            if (p.isAlive()) p.destroyForcibly();
            log.delete();
        }
    }

    private static int get(String api, String[] out, int len, CUdevice device, int ordinal)
    {
        return api.equals("driver") ? cuDeviceGetPCIBusId(out, len, device)
            : JCuda.cudaDeviceGetPCIBusId(out, len, ordinal);
    }

    public static void main(String[] args)
    {
        String api = args[0], mode = args[1];
        setExceptionsEnabled(false);
        JCuda.setExceptionsEnabled(false);
        String[] out = {"sentinel", "tail"};
        CUdevice device = new CUdevice();
        if (mode.equals("uninitialized"))
        {
            assertEquals(CUresult.CUDA_ERROR_NOT_INITIALIZED, get(api, out, 32, device, 0));
            assertArrayEquals(new String[] {"sentinel", "tail"}, out);
        }
        else if (mode.equals("negative") || mode.equals("zero"))
            assertThrows(IllegalArgumentException.class, () -> get(api, out, mode.equals("zero") ? 0 : -1, device, 0));
        else if (mode.equals("empty"))
            assertThrows(IllegalArgumentException.class, () -> get(api, new String[0], 32, device, 0));
        else if (mode.equals("null"))
            assertThrows(NullPointerException.class, () -> get(api, null, 32, device, 0));
        else if (mode.equals("null-device"))
            assertThrows(NullPointerException.class, () -> get(api, out, 32, null, 0));
        else
        {
            assertEquals(0, cuInit(0));
            assertEquals(0, cuDeviceGet(device, 0));
            if (mode.equals("invalid") || mode.equals("exceptions"))
            {
                if (mode.equals("exceptions"))
                {
                    setExceptionsEnabled(true);
                    JCuda.setExceptionsEnabled(true);
                    assertThrows(CudaException.class, () -> get(api, out, 32, CU_DEVICE_CPU, -1));
                }
                else
                    assertEquals(api.equals("driver") ? CUresult.CUDA_ERROR_INVALID_DEVICE : cudaError.cudaErrorInvalidDevice,
                        get(api, out, 32, CU_DEVICE_CPU, -1));
                assertArrayEquals(new String[] {"sentinel", "tail"}, out);
            }
            else
            {
                assertEquals(0, get(api, out, 32, device, 0));
                String full = out[0];
                assertTrue(full, full.matches("[0-9a-fA-F]+:[0-9a-fA-F]{2}:[0-9a-fA-F]{2}\\.[0-9a-fA-F]"));
                assertEquals("tail", out[1]);
                if (mode.equals("short"))
                {
                    for (int len = 1; len <= full.length(); len++)
                    {
                        String[] small = {"sentinel"};
                        int result = get(api, small, len, device, 0);
                        if (result == 0)
                        {
                            assertTrue(full.startsWith(small[0]));
                            assertTrue(small[0].length() < len);
                        }
                        else
                        {
                            assertEquals(api.equals("driver") ? CUresult.CUDA_ERROR_INVALID_VALUE : cudaError.cudaErrorInvalidValue, result);
                            assertEquals("sentinel", small[0]);
                        }
                    }
                }
                else
                {
                    assertEquals(0, get(api, out, full.length() + 1, device, 0));
                    assertEquals(full, out[0]);
                    assertEquals(0, get(api, out, 64, device, 0));
                    assertEquals(full, out[0]);
                    CUdevice roundTrip = new CUdevice();
                    assertEquals(0, cuDeviceGetByPCIBusId(roundTrip, full));
                    assertEquals(device, roundTrip);
                    int[] ordinal = {-1};
                    assertEquals(0, JCuda.cudaDeviceGetByPCIBusId(ordinal, full));
                    assertEquals(0, ordinal[0]);
                }
            }
        }
        System.out.println("PCI_QUERY_OK " + api + "/" + mode);
    }
}
