# NVRTC architecture-list capacity fix

`nvrtcGetSupportedArchs` previously allocated native storage using the caller's
Java array length, even though NVRTC writes its entire architecture list. An
undersized array therefore permitted a native overwrite. A larger array copied
uninitialized native trailing elements back to Java.

The binding now queries the required architecture count first, rejects short
arrays with `IllegalArgumentException`, allocates only the required native
storage, and copies back exactly the returned list on success. Larger arrays
retain their tail; null arrays still throw `NullPointerException`. Validation
also applies with CUDA exceptions enabled. `nvrtcGetNumSupportedArchs` checks
that its scalar output array has an element and writes it only on success.
Native error codes remain unchanged. Public signatures are unchanged.

## Verification, 2026-11-04

- Baseline `c28a801`, rebuilt against CUDA 12.6: the safe padded-array regression
  failed at element 14, changing sentinel `20903` to `-1162167622`. This proves
  the uninitialized-tail copy. The short-buffer overwrite risk is source
  evidence; no deliberate heap-corruption crash was needed.
- All **9 new isolated child-JVM cases passed** under `-Xcheck:jni`: short,
  empty, exact, padded and null list arrays; exception-enabled rejection;
  empty/null/padded scalar count arrays. Tests use the runtime count, not a
  hard-coded architecture list. Rejected arrays and trailing sentinels remain
  unchanged; returned architectures are positive and sorted.
- **152 selected tests passed together**: 113 Java-only cases, 9 new
  architecture cases, 24 existing NVRTC binary-capacity cases, and 6 existing
  NVRTC lowered-name cases. Direct JUnit 4.13.2, JDK 22, Java sources compiled
  with `--release 8`; this was not a Maven run.
- MSVC Release/x64, matching CUDA 12.6 headers/import libraries and NVRTC
  12.6.85. Legacy FindCUDA initially selected installed 11.6 headers and the
  build failed; configuring twice with explicit roots fixed that. Final cache
  paths were checked. Runtime NVRTC version is 12.6, and JVM library logging
  confirms the freshly built isolated JNI DLL.

The Maven entry point for these tests, with normal fork dependencies installed:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JNvrtcArchitectureCapacityTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

Supply matching NVRTC DLLs to the test process and verify the loaded JNI library.
Published native JARs do not contain this source fix.

## Scope and limits

This is one architecture-query boundary fix, independent of the earlier PTX
compiler fix and existing NVRTC binary-output fix. Metadata queries and NVRTC
compilation tests run on the CPU; no GPU context, reset, kernel, ingestion pause
or art interruption was needed. Master remains unchanged. Linux, 32-bit, actual
JDK 8 execution, native failure injection and out-of-memory behavior were not
tested. No native binary release is published.

Native contract: [NVIDIA NVRTC general information queries](https://docs.nvidia.com/cuda/nvrtc/#general-information-query).
