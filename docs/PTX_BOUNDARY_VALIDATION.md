# PTX compiler JNI boundary validation

The JNI binding now rejects PTX byte lengths and option counts outside their
Java arrays before calling CUDA. Compiled-program, error-log and info-log getters
query CUDA's required byte count before allocating/copying output. Invalid
capacities throw `IllegalArgumentException`; existing null-argument checks remain
`NullPointerException`, including null entries within the requested options.
Version and size result arrays must contain at least one element. Failed native
size/version queries and failed creation do not publish undefined native outputs.

Valid prefixes remain supported: `ptxCodeLen` and `numCompileOptions` need not
equal the backing-array length. Null trailing options outside the requested
prefix are accepted. Exact and larger output arrays work, with trailing bytes
preserved. CUDA result codes (and the existing optional `CudaException` mode)
are preserved when a size query fails. Existing null options-array behavior is
unchanged, even for a zero option count.

## Evidence (Windows x86_64, 2026-11-04)

- Baseline `d431c66`: an invalid compiler-handle size query overwrote a Java
  sentinel (`1234567`) with `4607182418800017408`; the regression failed. This is
  a reproduced undefined-output defect. Native overwrite/read risks in the other
  missing checks were identified from source; a heap-corruption crash was not
  deliberately reproduced.
- Toolchain correction: the initial build and baseline reproduction used the
  installed CUDA 11.6 PTX compiler because legacy FindCUDA reset the requested
  paths. Subsequent inspection detected this; the build was reconfigured twice
  with explicit CUDA 12.6 roots and every relevant cache path verified. The
  final library uses CUDA 12.6.85's static PTX compiler and 12.6 headers/import
  libraries with integrated common JNI sources. A runtime version probe reports
  12.6. This supersedes the initial claim that the first build used 12.6.
- All **8 PTX regression methods passed**, in isolated child JVMs with
  `-Xcheck:jni`: negative/oversized/maximal lengths and counts, null options,
  valid prefixes, short/empty/exact/padded output arrays for all three getters,
  failed compilation logs, empty logs, invalid handles, incomplete compilation,
  scalar capacities, unchanged error outputs and exception-enabled behavior.
- These 8 plus the existing **78 Java-only regressions passed together: 86
  tests**, zero failures. Compiled with JDK 22 `javac --release 8`, executed on
  JDK 22 with JUnit 4.13.2. This was direct JUnit execution, not a Maven run.
- After rebasing onto fork master `c28a801`, the final tree passed **121 tests**:
  all 8 PTX methods plus the expanded 113 Java-only cases, with JNI checking.
  All 121 passed again against the corrected, verified CUDA 12.6 build.
- JVM library logging confirmed the isolated freshly built PTX JNI DLL loaded.

## Reproduce

Build `JNvPTXCompilerJNI` against matching CUDA/shared JNI dependencies, then:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JNvPTXCompilerBoundaryTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

This is the Maven entry point for the same test class; it requires the fork's
parent/dependencies as described in `CONTRIBUTING.md`. Verify the loaded library:
published native JARs do not contain these source changes.

## Limits

The tests compile a tiny `sm_75` PTX program on the CPU. They do not initialize a
GPU context, allocate device memory or launch a kernel; ingestion and GPU art
were not paused. No full GPU suite, Linux/32-bit build, other toolkit beyond the
11.6 and 12.6 runs above, actual
JDK 8 runtime or allocation-failure injection was run. Callers must serialize
compile/destroy operations against queries on the same handle; concurrent handle
mutation between size and data calls is outside this change. No native binary
release is published. This is one focused boundary-safety contribution, not one
fix per assertion or wrapper.

Native contract: [NVIDIA PTX Compiler API](https://docs.nvidia.com/cuda/archive/12.6.0/ptx-compiler-api/index.html).
