/* JCuda - Java bindings for CUDA - http://www.jcuda.org */
package jcuda.test;

import static jcuda.nvptxcompiler.JNvPTXCompiler.*;
import static jcuda.nvptxcompiler.nvPTXCompileResult.*;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import jcuda.nvptxcompiler.nvPTXCompilerHandle;

/** CPU compilation only: no CUDA context, device allocation or kernel launch. */
public class JNvPTXCompilerBoundaryTest
{
    private static final byte[] PTX = (".version 7.0\n.target sm_75\n.address_size 64\n"
        + ".visible .entry noop() { ret; }\n").getBytes(StandardCharsets.US_ASCII);

    @Test public void inputBounds() throws Exception { child("inputs"); }
    @Test public void binaryBounds() throws Exception { child("binary"); }
    @Test public void errorLogBounds() throws Exception { child("error"); }
    @Test public void infoLogBounds() throws Exception { child("info"); }
    @Test public void errorOutputsUnchanged() throws Exception { child("errors"); }
    @Test public void scalarBounds() throws Exception { child("scalars"); }
    @Test public void exceptionMode() throws Exception { child("exceptions"); }
    @Test public void emptyLogs() throws Exception { child("emptyLogs"); }

    private void child(String mode) throws Exception
    {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        File log = File.createTempFile("jcuda-ptx-boundary-", ".log");
        Process p = new ProcessBuilder(new File(new File(System.getProperty("java.home"), "bin"), executable).getPath(),
            "-Xcheck:jni", "-Djava.library.path=" + System.getProperty("java.library.path"), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            getClass().getName(), mode).redirectErrorStream(true).redirectOutput(log).start();
        try
        {
            assertTrue("PTX child timeout", p.waitFor(45, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(output, 0, p.exitValue());
            assertTrue(output, output.contains("PTX_BOUNDARY_OK"));
            assertFalse(output, output.contains("WARNING in native method"));
        }
        finally
        {
            if (p.isAlive()) p.destroyForcibly();
            log.delete();
        }
    }

    private static int size(String kind, nvPTXCompilerHandle h, long[] out)
    {
        if (kind.equals("binary")) return nvPTXCompilerGetCompiledProgramSize(h, out);
        if (kind.equals("error")) return nvPTXCompilerGetErrorLogSize(h, out);
        return nvPTXCompilerGetInfoLogSize(h, out);
    }
    private static int get(String kind, nvPTXCompilerHandle h, byte[] out)
    {
        if (kind.equals("binary")) return nvPTXCompilerGetCompiledProgram(h, out);
        if (kind.equals("error")) return nvPTXCompilerGetErrorLog(h, out);
        return nvPTXCompilerGetInfoLog(h, out);
    }

    public static void main(String[] args)
    {
        setExceptionsEnabled(false);
        String mode = args[0];
        nvPTXCompilerHandle h = new nvPTXCompilerHandle();
        if (mode.equals("inputs"))
        {
            for (long n : new long[] {-1, PTX.length + 1, Long.MAX_VALUE})
                assertThrows(IllegalArgumentException.class, () -> nvPTXCompilerCreate(h, n, PTX));
            assertThrows(NullPointerException.class, () -> nvPTXCompilerCreate(h, 0, null));
            // A prefix is legal; garbage after the supplied byte count is ignored.
            byte[] padded = Arrays.copyOf(PTX, PTX.length + 12);
            Arrays.fill(padded, PTX.length, padded.length, (byte)'!');
            assertEquals(0, nvPTXCompilerCreate(h, PTX.length, padded));
            try
            {
                for (int n : new int[] {-1, 2, Integer.MAX_VALUE})
                    assertThrows(IllegalArgumentException.class, () -> nvPTXCompilerCompile(h, n, new String[] {"--gpu-name=sm_75"}));
                assertThrows(NullPointerException.class, () -> nvPTXCompilerCompile(h, 1, new String[] {null}));
                assertThrows(NullPointerException.class, () -> nvPTXCompilerCompile(h, 0, null));
                assertEquals(0, nvPTXCompilerCompile(h, 1, new String[] {"--gpu-name=sm_75", null}));
            }
            finally { assertEquals(0, nvPTXCompilerDestroy(h)); }
        }
        else if (mode.equals("errors"))
        {
            for (String kind : new String[] {"binary", "error", "info"})
            {
                long[] n = {1234567};
                assertEquals(NVPTXCOMPILE_ERROR_INVALID_COMPILER_HANDLE, size(kind, h, n));
                assertEquals(1234567, n[0]);
                byte[] output = {42};
                assertEquals(NVPTXCOMPILE_ERROR_INVALID_COMPILER_HANDLE, get(kind, h, output));
                assertEquals(42, output[0]);
                assertThrows(NullPointerException.class, () -> get(kind, h, null));
            }
            assertEquals(0, nvPTXCompilerCreate(h, PTX.length, PTX));
            try
            {
                long[] n = {1234567};
                assertEquals(NVPTXCOMPILE_ERROR_COMPILER_INVOCATION_INCOMPLETE, size("binary", h, n));
                assertEquals(1234567, n[0]);
                assertEquals(NVPTXCOMPILE_ERROR_COMPILER_INVOCATION_INCOMPLETE, get("binary", h, new byte[0]));
            }
            finally { assertEquals(0, nvPTXCompilerDestroy(h)); }
        }
        else if (mode.equals("exceptions"))
        {
            setExceptionsEnabled(true);
            assertThrows(IllegalArgumentException.class, () -> nvPTXCompilerCreate(h, -1, PTX));
            long[] n = {1234567};
            assertThrows(jcuda.CudaException.class, () -> size("binary", h, n));
            assertEquals(1234567, n[0]);
            setExceptionsEnabled(false);
        }
        else if (mode.equals("emptyLogs"))
        {
            assertEquals(0, nvPTXCompilerCreate(h, PTX.length, PTX));
            try
            {
                assertEquals(0, nvPTXCompilerCompile(h, 1, new String[] {"--gpu-name=sm_75"}));
                for (String kind : new String[] {"error", "info"})
                {
                    long[] n = {0};
                    assertEquals(0, size(kind, h, n));
                    byte[] out = new byte[(int)n[0]];
                    assertEquals(0, get(kind, h, out));
                    for (byte b : out) assertEquals(0, b);
                }
            }
            finally { assertEquals(0, nvPTXCompilerDestroy(h)); }
        }
        else if (mode.equals("scalars"))
        {
            int[] major = {99};
            assertThrows(IllegalArgumentException.class, () -> nvPTXCompilerGetVersion(major, new int[0]));
            assertEquals(99, major[0]);
            assertThrows(IllegalArgumentException.class, () -> nvPTXCompilerGetVersion(new int[0], major));
            assertEquals(0, nvPTXCompilerGetVersion(new int[1], new int[1]));
            for (String kind : new String[] {"binary", "error", "info"})
                assertThrows(IllegalArgumentException.class, () -> size(kind, h, new long[0]));
        }
        else
        {
            byte[] source = mode.equals("error") ? "invalid PTX".getBytes(StandardCharsets.US_ASCII) : PTX;
            assertEquals(0, nvPTXCompilerCreate(h, source.length, source));
            try
            {
                int result = nvPTXCompilerCompile(h, 2, new String[] {"--gpu-name=sm_75", "--verbose"});
                if (mode.equals("error")) assertNotEquals(0, result); else assertEquals(0, result);
                long[] n = {0};
                assertEquals(0, size(mode, h, n));
                assertTrue("Expected nonempty " + mode, n[0] > 1);
                byte[] shortOutput = new byte[(int)n[0] - 1];
                Arrays.fill(shortOutput, (byte)42);
                assertThrows(IllegalArgumentException.class, () -> get(mode, h, shortOutput));
                for (byte b : shortOutput) assertEquals(42, b);
                assertThrows(IllegalArgumentException.class, () -> get(mode, h, new byte[0]));
                byte[] exact = new byte[(int)n[0]];
                assertEquals(0, get(mode, h, exact));
                byte[] padded = new byte[exact.length + 16];
                Arrays.fill(padded, (byte)42);
                assertEquals(0, get(mode, h, padded));
                assertArrayEquals(exact, Arrays.copyOf(padded, exact.length));
                for (int i = exact.length; i < padded.length; i++) assertEquals(42, padded[i]);
            }
            finally { assertEquals(0, nvPTXCompilerDestroy(h)); }
        }
        System.out.println("PTX_BOUNDARY_OK " + mode);
    }
}
