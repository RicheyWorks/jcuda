/* JCuda smoke example. MIT license: see repository LICENSE. */
import static jcuda.driver.JCudaDriver.*;
import static jcuda.nvrtc.JNvrtc.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import jcuda.Pointer;
import jcuda.driver.*;
import jcuda.nvrtc.nvrtcProgram;

/** Loads all four packaged JNI libraries and computes 64 exact GPU results. */
public final class PackagedGpuSmoke {
    public static void main(String[] args) throws Exception {
        jcuda.driver.JCudaDriver.setExceptionsEnabled(true);
        jcuda.runtime.JCuda.setExceptionsEnabled(true);
        jcuda.nvrtc.JNvrtc.setExceptionsEnabled(true);
        jcuda.nvptxcompiler.JNvPTXCompiler.setExceptionsEnabled(true);
        int[] runtime = {0}, major = {0}, minor = {0}, ptxMajor = {0}, ptxMinor = {0};
        jcuda.runtime.JCuda.cudaRuntimeGetVersion(runtime);
        nvrtcVersion(major, minor);
        jcuda.nvptxcompiler.JNvPTXCompiler.nvPTXCompilerGetVersion(ptxMajor, ptxMinor);
        if (runtime[0] != 12060 || major[0] != 12 || minor[0] != 6 || ptxMajor[0] != 12 || ptxMinor[0] != 6)
            throw new AssertionError("Expected CUDA12.6 runtime/compiler libraries");
        String source = "extern \"C\" __global__ void affine(const float* x,float* y,int n) {"
            + "int i=blockIdx.x*blockDim.x+threadIdx.x;if(i<n)y[i]=2.0f*x[i]+1.0f;}";
        nvrtcProgram program = new nvrtcProgram();
        nvrtcCreateProgram(program, source, "smoke.cu", 0, null, null);
        String ptx;
        try {
            String[] options = {"--gpu-architecture=compute_75", "--fmad=false"};
            try { nvrtcCompileProgram(program, options.length, options); }
            catch (RuntimeException failure) {
                String[] log = {null}; nvrtcGetProgramLog(program, log);
                throw new IllegalStateException(log[0], failure);
            }
            String[] output = {null}; nvrtcGetPTX(program, output); ptx = output[0];
        } finally { nvrtcDestroyProgram(program); }
        cuInit(0);
        CUdevice device = new CUdevice(); cuDeviceGet(device, 0);
        byte[] nameBytes = new byte[256]; cuDeviceGetName(nameBytes, nameBytes.length, device);
        int length = 0; while (length < nameBytes.length && nameBytes[length] != 0) length++;
        String name = new String(nameBytes, 0, length, StandardCharsets.UTF_8);
        float[] input = new float[64], output = new float[64];
        for (int i = 0; i < input.length; i++) input[i] = i * .5f;
        CUcontext context = new CUcontext(); cuCtxCreate(context, 0, device);
        try {
            CUmodule module = new CUmodule(); cuModuleLoadData(module, ptx);
            try {
                CUfunction function = new CUfunction(); cuModuleGetFunction(function, module, "affine");
                CUdeviceptr in = new CUdeviceptr(), out = new CUdeviceptr();
                cuMemAlloc(in, input.length * 4L);
                try {
                    cuMemAlloc(out, output.length * 4L);
                    try {
                        cuMemcpyHtoD(in, Pointer.to(input), input.length * 4L);
                        Pointer parameters = Pointer.to(Pointer.to(in),Pointer.to(out),Pointer.to(new int[]{input.length}));
                        cuLaunchKernel(function,1,1,1,64,1,1,0,null,parameters,null);
                        cuCtxSynchronize();
                        cuMemcpyDtoH(Pointer.to(output),out,output.length * 4L);
                    } finally { cuMemFree(out); }
                } finally { cuMemFree(in); }
            } finally { cuModuleUnload(module); }
        } finally { cuCtxDestroy(context); }
        double sum = 0;
        for (int i = 0; i < output.length; i++) {
            if (Float.floatToIntBits(output[i]) != Float.floatToIntBits(2 * input[i] + 1))
                throw new AssertionError("Mismatch at " + i);
            sum += output[i];
        }
        String json = String.format(Locale.ROOT,
            "{\n\"status\":\"PASS\",\n\"device\":\"%s\",\n\"elements\":64,\n"
            + "\"mismatches\":0,\n\"first\":%.1f,\n\"last\":%.1f,\n\"sum\":%.1f,\n"
            + "\"explicitDeviceBufferBytes\":512,\n\"runtimeVersion\":%d,\n"
            + "\"nvrtcVersion\":\"%d.%d\",\n\"nvPTXCompilerVersion\":\"%d.%d\",\n"
            + "\"javaClassesFrom\":\"%s\",\n\"note\":\"Tiny correctness smoke, not a benchmark; PTX compiler version queried, its compilation API not exercised.\"\n}\n",
            name, output[0], output[63], sum, runtime[0], major[0], minor[0], ptxMajor[0], ptxMinor[0],
            jcuda.Pointer.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm());
        Files.write(Paths.get(args[0]), json.getBytes(StandardCharsets.UTF_8));
        System.out.println(json);
    }
}
