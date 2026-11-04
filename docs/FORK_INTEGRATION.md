# RicheyWorks source integration

The twenty verified fixes are combined across `RicheyWorks/jcuda`, `RicheyWorks/jcuda-common` and `RicheyWorks/jcuda-parent`. Individual branches and commits remain intact. MIT license and original attribution are unchanged. No new upstream PR, contact, payment or release is part of this integration.

## Inputs and dependencies

Core source integration before these documentation updates: `7f0c57fd19095bfb74c4f3d91b6a127a2a565978`. This merges all 18 core fix heads, companion buffer-transfer tests and existing contributor documentation. Two remaining counted fixes are shared JNI copy-back (`76c38070e825f5e0bf109bb7f0ce895a8e454006`) and parent Java 8 target (`eeb0008654cd96acdb16f596dd8109a760b9664e`). [integration-inputs.json](integration-inputs.json) records all inputs and historical PR links.

The existing input patch from [community PR48](https://github.com/jcuda/jcuda/pull/48) is also merged: `a6908ccb637e025e8dcec629cc13961d1e02ff05`, authored as Kowala and submitted by GitHub user bleenhou. Its authorship is preserved. It enables the previously separate nondefault stream-window input/output round trips. It is credited separately and excluded from our twenty-fix count.

Shared copy-back is compiled into all three JNI libraries. Allocation/property and access-descriptor fixes are now present together, enabling mapped-memory round trips. Lowered-name error handling and binary-output bounds are combined in one NVRTC library. No source conflicts required manual resolution.

## Combined verification on 2026-11-04

Windows x86_64, RTX 4090, NVIDIA 617.42, Temurin 22, existing MSVC 19.44/CMake. Parent installed normally into an isolated Maven cache. All three JNI libraries rebuilt against integrated shared source and checksum-verified official CUDA 12.6 headers/import libraries. Workspace NVRTC 12.6.85/cudart 12.6 DLLs were supplied only to test processes. No installation or persistent environment/security change.

- **198 focused regression cases passed**, zero failures/errors/skips, in one Maven run with combined libraries and `-Xcheck:jni`: 78 Java-only and 120 native/API cases, including child-process regressions.
- **9 optional cases passed** separately: three mapped-memory permission round trips and six stream-window/flag cases with nondefault input enabled. This is another mode of existing tests, not nine new fixes or wholly new test methods.
- A tiny real `add_one` kernel returned all 64 expected values bit-exactly: zero mismatches, first 1.0 / last 32.5 / sum 1072.0. Combined classes and driver JNI under checking; 512 explicit device-buffer bytes. Correctness smoke check, not a benchmark or art render.
- Driver/runtime/NVRTC selection confirmed by JVM library logs, including direct runtime-window and successful NVRTC name probes, all using combined native output.
- Local core JAR, source JAR and Javadoc JAR packaging passed (`mvn -DskipTests verify`); existing Javadoc warnings remain. No Maven/native binary release is published.

Selected classes:

```text
PointerBufferStateTest,PointerByteBufferOffsetTest,PointerCharBufferTest,
LibUtilsExtractionTest,SizeofPointerTest,FlagCombinationStringTest,
JCudaDeviceIdentityTest,JCudaDeviceNameBoundsTest,JCudaDriverAccessDescriptorTest,
JCudaDriverAllocationPropertyTest,JCudaDriverBufferSliceTest,JCudaDriverCurrentContextTest,
JCudaDriverResourcePointerOffsetTest,JCudaDriverSparsePropertyTest,JCudaGraphQueryCapacityTest,
JCudaManagedMemoryCountTest,JCudaStreamWindowOutputTest,JNvrtcLoweredNameTest,JNvrtcBinaryCapacityTest
```

Use the comma-separated selection in `-Dtest=...` after following [CONTRIBUTING.md](../CONTRIBUTING.md). Set `-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>` and supply matching dependent DLLs to the process. Offline mode requires cached dependencies.

Additional integration modes:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JCudaDriverAccessDescriptorTest,JCudaStreamWindowOutputTest' '-Djcuda.test.vmmMappedAccess=true' '-Djcuda.test.windowRoundTrip=true' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

## Limits

This is not the full CUDA suite or multi-library reactor. Linux, other architectures/GPUs/toolkits, actual JDK 8 execution, large NVRTC outputs and concurrent program mutation are unverified. Managed prefetch compiled but was not executed. Resource descriptors were queried without texture sampling; mapped permissions were queried without enforcement/data-access tests. Existing CMake deprecations and native warnings remain. No GPU reset or ingestion pause was needed.

Local build logs, Surefire results, library logs and smoke JSON preserve evidence. Native binaries are ignored build outputs, not committed. Consumers must rebuild source for these JNI changes; official 12.6 artifacts do not acquire them automatically.

The earlier twenty-fix report remains a historical snapshot. Current fork integration is independent of upstream acceptance; historical submissions are not changed or resubmitted here.
