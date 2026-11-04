# Contributing to the RicheyWorks JCuda fork

This is a development fork of [JCuda](https://github.com/jcuda/jcuda), originally by Marco Hutter and community contributors. Preserve the MIT license and attribution. Development and integration now happen in the RicheyWorks forks; historical upstream PR links document individual patches. We are not submitting further upstream PRs or promising upstream review, acceptance or releases.

The twenty verified changes are combined here and in two shared dependencies. Read [the integration record](docs/FORK_INTEGRATION.md) and [fix inventory](docs/CONTRIBUTION_INVENTORY.md). Documentation and GPU art do not count as additional binding fixes. This is source integration, not a published Maven/native binary release.

## Obtain the integrated source

Clone the relevant forks as siblings in a fresh directory:

```powershell
git clone https://github.com/RicheyWorks/jcuda-parent.git
git clone https://github.com/RicheyWorks/jcuda-common.git
git clone https://github.com/RicheyWorks/jcuda.git
cd jcuda
mvn -f ../jcuda-parent/pom.xml install
mvn -f JCudaJava/pom.xml test-compile
```

The parent targets Java 8. Compilation and focused checks have run on newer JDKs; an actual JDK 8 runtime and the complete multi-repository reactor are not certified. Resolve declared dependencies normally; offline mode requires an existing cache. Do not commit caches or machine-specific paths.

For other CUDA libraries and full native packaging, consult the original [jcuda-main build instructions](https://github.com/jcuda/jcuda-main/blob/master/BUILDING.md). These three repositories cover the focused core source work, not every aggregate binding.

## Select tests deliberately

Java-only example:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=PointerBufferStateTest,PointerByteBufferOffsetTest,PointerCharBufferTest,LibUtilsExtractionTest,SizeofPointerTest,FlagCombinationStringTest' test
```

Native changes require rebuilding the relevant JNI library against integrated `jcuda-common`. Inspect CMake's CUDA/JNI includes and import libraries: a toolkit directory name alone does not prove a complete installation. Make dependent CUDA/NVRTC DLLs available to the test process as needed.

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JNvrtcBinaryCapacityTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

Replace the placeholder. Use JVM library logging to confirm which DLL loaded. Maven's published 12.6 native JAR does not contain this fork's JNI fixes. Java compilation or a test against that old binary does not validate changed native code. See the integration record for optional mapped-memory and nondefault stream-window checks.

GPU tests should use private resources, bounded workloads and `finally` cleanup. Coordinate any needed pause with the workload owner; never reset the device or modify unrelated assets. Avoid invoking the entire device suite accidentally.

## Make a focused contribution

Check fork issues, branches and historical upstream work, including proposed patches in discussion comments, to avoid duplication. Start one branch from this fork's current `master` per independent defect. Include a failing reproduction, relevant native contract, tests/results and unrun checks. Preserve existing style, copyright and license notices.

Submit authorized review work to this fork or keep a local reviewable patch. Do not send unsolicited messages or submit our work upstream. [Starter proposals](docs/STARTER_ISSUES.md) are ideas, not assignments or guarantees that work remains unaddressed; reassess them against integrated source first.
