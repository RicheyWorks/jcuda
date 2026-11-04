# Repeatable Windows build and use

This workflow builds matching Java and all four Windows x86_64 JNI libraries from this fork and its sibling `jcuda-common`. It does not use official Maven native binaries, install software, modify global PATH, deploy, or publish a release. Output names include the core Git commit and deliberately differ from official Maven artifacts.

## Prerequisites

- A 64-bit Windows PowerShell process, Git, CMake and Visual Studio2022 C++ x64 build tools with a Windows SDK already available. Tested: Windows11, PowerShell7, CMake3.31.0-rc3, VS2022 Community/MSVC compiler19.44.35229.0 (tool directory14.44.35207). Other toolchains are unverified.
- JDK21+ build tools for `--release` and fixed JAR dates. Tested end to end: Temurin22.0.2. The Java artifact targets the Java8 API/bytecode, not Java21/22. An actual JDK8 runtime is untested.
- Matching CUDA12.6 headers, import libraries, nvcc/compiler static library, and NVRTC12.6. Tested component versions: cudart/profiler-api12.6.77 and nvcc/NVRTC12.6.85. A complete CUDA12.6 installation can supply all roots. Merely having a directory named `v12.6` is insufficient.
- For the optional real computation: a CUDA-capable device and working NVIDIA driver access. Tested RTX4090, driver617.42 / driver API13040. No driver change or device reset is performed.
- Sibling checkouts of [RicheyWorks/jcuda-common](https://github.com/RicheyWorks/jcuda-common) and [RicheyWorks/jcuda-parent](https://github.com/RicheyWorks/jcuda-parent). The build records all three Git heads and dirty flags. Use clean, matching integrated sources; see [integration inputs](integration-inputs.json).

The script compiles Java directly with `javac --release 8`, matching the integrated parent baseline. Maven is not required for this artifact workflow and no Maven repository is changed. Existing Maven test/build workflows remain available separately.

## One build command

From a fresh sibling layout:

```powershell
git clone https://github.com/RicheyWorks/jcuda-parent.git
git clone https://github.com/RicheyWorks/jcuda-common.git
git clone https://github.com/RicheyWorks/jcuda.git
cd jcuda
.\tools\windows\Build.ps1 -CudaRoot 'C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v12.6' -JavaHome 'C:\path\to\jdk-22' -RunSmoke
```

Replace the JDK path and use an existing complete toolkit. Omit `-RunSmoke` for a CPU-only build. A regular terminal needs NVIDIA driver access for the smoke; restricted execution environments may require their authorized execution route. No security-policy changes are part of these commands.

For separately extracted prerequisites, supply `-NvrtcRoot` and `-NvccRoot` as well. This is the exact layout used in our task workspace (run from `jcuda`):

```powershell
.\tools\windows\Build.ps1 -CudaRoot '..\cuda-build-dependencies\cuda_cudart-windows-x86_64-12.6.77-archive' -NvrtcRoot '..\cuda-build-dependencies\cuda_nvrtc-windows-x86_64-12.6.85-archive' -NvccRoot '..\cuda-build-dependencies\cuda_nvcc-windows-x86_64-12.6.85-archive' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot' -RunSmoke
```

That cudart include directory also contains the matching nvcc `include` tree and profiler API headers. It is a workspace SDK overlay, not a claim that a full system toolkit was installed. A fresh overlay requires copying the extracted nvcc/profiler `include` contents into the workspace cudart `include` directory; keep the original archives and their licenses. The compiler root must retain `bin/nvcc.exe`, `lib/x64/nvptxcompiler_static.lib` and its supplied LICENSE/EULA.

Official source manifest: [NVIDIA CUDA12.6.3 redistribution metadata](https://developer.download.nvidia.com/compute/cuda/redist/redistrib_12.6.3.json). Archive paths are relative to `https://developer.download.nvidia.com/compute/cuda/redist/`. Verify SHA256 before extracting; the build script does not download anything automatically.

| Component | Relative archive path | SHA256 |
| --- | --- | --- |
| cudart12.6.77 | `cuda_cudart/windows-x86_64/cuda_cudart-windows-x86_64-12.6.77-archive.zip` | `7a313bc0c93b1a50bb03aa9783a199ae70c3b66e2d8084da65e8254a8577b925` |
| nvcc12.6.85 | `cuda_nvcc/windows-x86_64/cuda_nvcc-windows-x86_64-12.6.85-archive.zip` | `3fb9f76b87c37d02f947354be89b718ad5f2c76b6ab47995265bfa3a068a5e14` |
| NVRTC12.6.85 | `cuda_nvrtc/windows-x86_64/cuda_nvrtc-windows-x86_64-12.6.85-archive.zip` | `b1221a95a3758561bc73c56905743730f7459cbaff54a4870aa899fa3489219e` |
| profiler-api12.6.77 | `cuda_profiler_api/windows-x86_64/cuda_profiler_api-windows-x86_64-12.6.77-archive.zip` | `d0ed383bac1a84d5c1e1d6a3076b80bc581e488093fef5655d55c39a198aea29` |

## Outputs and isolation

Default output is ignored `out/windows-x86_64/`. A custom `-BuildRoot` must be nonexistent on its first use; later invocations require the script's ownership marker for the same checkout. No recursive delete is used. Each run gets fresh Java classes and loader directories; native CMake build directories are reused. The existing ignored `nativeLibraries/windows/x86_64/lib` output is rebuilt by the original CMake layout.

`dist/<core-commit-prefix>/` contains:

- `jcuda-richeyworks-<commit>.jar`: freshly compiled fork Java classes and MIT LICENSE.
- `jcuda-richeyworks-natives-windows-x86_64-<commit>.jar`: exactly four rebuilt DLLs under `lib/`, MIT LICENSE, the supplied NVIDIA license, and a build notice.
- `build-manifest.json`: source heads/dirty flags, tools/configuration, exact artifact and DLL hashes, prerequisite roots, logs directory and optional smoke result.
- `smoke.json` when requested, plus a copy of the matching `Run.ps1` launcher.

JAR entries use a fixed timestamp. This is a repeatable source-to-artifact workflow; byte-identical native builds across directories, toolchains or machines are **not** claimed. Logs and manifests contain local paths; review those before sharing them. No generated binary is committed or published by the script.

The packaged smoke uses only the new Java JAR, new native JAR and its own example class on the classpath. An empty `java.library.path` and fresh `java.io.tmpdir` force extraction from the native JAR instead of reusing previously extracted official DLLs. JVM logs must confirm all four paths, and extracted DLL hashes must match the staged JAR contents before the build is reported successful.

## Use the artifacts

Compile your program against the generated Java JAR. At runtime use both matching JARs, not an additional official native JAR. The launcher verifies their hashes and creates a fresh private loader directory:

```powershell
.\out\windows-x86_64\dist\<commit>\Run.ps1 -Distribution '.\out\windows-x86_64\dist\<commit>' -CudaRuntimeBin 'C:\path\to\CUDA12.6\bin' -NvrtcBin 'C:\path\to\NVRTC12.6\bin' -JavaHome 'C:\path\to\jdk-22' -ClassPath '.\my-classes' -MainClass 'MyMainClass'
```

Replace placeholders. Use `-ApplicationArguments @('first','second')` for application arguments and optional `-CheckJni` for JNI diagnostics. The launcher changes PATH only for its child process and restores it afterward. Runtime/compiler DLL prerequisites are required, but native build tools are not needed to launch already-built artifacts. Per-run extraction caches are retained to avoid unsafe cleanup around Windows DLL locks.

## Verification and limits

The real smoke loads driver/runtime/NVRTC/PTX-compiler JNI, checks CUDA12.6 component versions, compiles a tiny kernel with NVRTC, then computes `y=2*x+1` for64 floats on the4090. All64 are checked bit-exactly: first1.0, last64.0, sum2080.0, zero mismatches. It allocates512 explicit device bytes and releases private context/module/buffers. No ingestion pause/reset is needed. PTX-compiler version/loading is checked; its separate compilation API is not exercised.

The newly packaged Java/native JARs also passed all198 focused regressions under JNI checking, with fresh extraction caches inherited by child JVMs and no official native JAR on the classpath. The matching launcher was checked with the real smoke. The earlier integrated source additionally passed9 optional mapping/window checks; those optional modes were not rerun against the new packaged JARs. This is not the complete CUDA suite, multi-library reactor, benchmark, Linux validation, actual JDK8 runtime test or evidence of support for all devices/toolkits. Existing CMake/compiler warnings remain. Only the documented Windows/4090 setup has been run end to end.

## License and attribution

JCuda's MIT LICENSE and source copyright notices are preserved, including Marco Hutter and community attribution. This remains a RicheyWorks fork, not an official JCuda or NVIDIA release. The native PTX-compiler wrapper statically links NVIDIA's `nvptxcompiler_static.lib`; that NVIDIA code remains subject to its supplied CUDA EULA, included unchanged in the native JAR, and is not relicensed as MIT. The script does not bundle cudart/NVRTC runtime DLLs, install a toolkit, or change their licensing. Keep applicable notices and review the supplied NVIDIA terms for any later redistribution. No public binary release is authorized or performed here.
