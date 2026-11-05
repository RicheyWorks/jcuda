# PCI bus-ID JNI error handling

Both `cuDeviceGetPCIBusId` and `cudaDeviceGetPCIBusId` converted their native
buffer to a Java string even when CUDA returned an error. Since error outputs
are undefined, this could read uninitialized memory and replaced the caller's
existing output. Negative lengths also reached native allocation, zero lengths
left no storage for a terminator, and an empty result array was rejected only
after doing native work. A failed Java string allocation leaked the native buffer.

The wrappers now validate that the Java array has at least one element and that
the native byte capacity is positive. They return CUDA errors before reading or
publishing output, release native storage on every handled return path, preserve
JVM string-allocation exceptions, and reserve a guard terminator using unsigned
size arithmetic. Public signatures and valid calls are unchanged. Positive short
capacities still go to CUDA, preserving its truncation/error behavior. Other Java
array elements remain untouched. Invalid capacities throw
`IllegalArgumentException`; existing null checks remain `NullPointerException`.
The Javadoc now distinguishes a one-element Java String array from native byte
capacity (13 bytes holds the complete PCI identifier including the terminator).

## Verification, 2026-11-04

- Baseline `c28a801`, rebuilt driver and runtime JNI: an invalid-device query
  returned the expected CUDA error but replaced `"sentinel"` with `""` in both
  APIs. Both regression probes failed. This is a reproduced output-corruption
  defect; the invalid native read follows from source, not a claimed crash.
- **18 new isolated child-JVM cases passed** under `-Xcheck:jni`: both APIs with
  invalid devices, exception mode, negative/zero lengths, empty/null outputs,
  exact/larger valid capacities, positive short capacities, and trailing-output
  preservation; plus driver-not-initialized and null-device checks.
- Valid identifiers round-tripped through both driver and runtime
  `GetByPCIBusId`, returning device zero. Tests query the actual identity rather
  than hard-coding a machine's PCI address.
- **131 tests passed together**: the 18 new cases and 113 existing Java-only
  regressions. Direct JUnit 4.13.2, JDK 22, `javac --release 8`; not a Maven run.
- Windows x86_64/MSVC Release, verified CUDA 12.6 header/import paths and cudart
  12.6.77. The legacy CMake configuration was run twice and selected paths checked.
  JVM library logs confirmed both freshly built isolated JNI DLLs. Existing CMake
  deprecation and an unrelated driver pointer-cast warning remain.

Maven entry point with the normal fork parent/dependencies installed:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JCudaPciBusIdTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

Supply matching cudart DLLs and rebuild both driver/runtime JNI modules. These
tests require a working CUDA device and driver; they deliberately fail rather
than silently skipping missing prerequisites.

## Limits

Only device metadata was queried. Tests contain no explicit context creation,
device memory allocation, kernel launch or reset, and ingestion/art workloads
were not paused. This is not a full GPU suite or a benchmark. Linux, 32-bit,
actual JDK 8 execution, multi-device configurations, allocator-failure injection
and giant requested capacities remain untested. No binary release or master
change is included. Both wrappers address the same defect and count as one
focused contribution.

Native contracts: [driver PCI query](https://docs.nvidia.com/cuda/cuda-driver-api/group__CUDA__MEM.html),
[runtime device queries](https://docs.nvidia.com/cuda/cuda-runtime-api/cuda_runtime_api/group__CUDART__DEVICE.html).
