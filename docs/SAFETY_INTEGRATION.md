# Three JNI safety fixes integrated

Integration record, 2026-11-05. The owner approved publication and merging of
the three completed fixes into RicheyWorks/jcuda. Integration started from
`c28a801` in a separate worktree, preserving unrelated worktrees and commits.
All merges were conflict-free. No upstream submission or binary release is part
of this integration.

| Fix | Source commit | Merge commit | Detailed evidence |
| --- | --- | --- | --- |
| PTX compiler lengths, capacities and error outputs | `217c7b7`, evidence correction `c78d0d3` | `b460d49` | [PTX validation](PTX_BOUNDARY_VALIDATION.md) |
| NVRTC architecture-query capacity and output tails | `58d8e8d` | `da9c4d4` | [Architecture validation](NVRTC_ARCHITECTURE_CAPACITY.md) |
| Driver/runtime PCI bus-ID errors and capacities | `45b4c76` | `72c36a1` | [PCI validation](PCI_BUS_ID_ERRORS.md) |

The inventory now contains 23 distinct fixes: the original twenty plus these
three. Each wrapper family counts once. Earlier per-fix records describing
unmerged feature branches are historical snapshots; the merge record above
supersedes that status.

## Combined verification

All four JNI libraries (driver, runtime, NVRTC and PTX compiler) were rebuilt from
the merged source against integrated common JNI code. Windows x86_64, MSVC
Release, CUDA 12.6 headers/imports, NVRTC/PTX compiler 12.6.85 and cudart 12.6.77.
Legacy CMake was configured twice with explicit roots; selected cache paths were
verified rather than inferred from directory names. Existing CMake deprecations
and an unrelated driver pointer-cast warning remain.

**178 tests passed, zero failures**, with `-Xcheck:jni`:

- 113 existing Java-only regression cases.
- 24 existing NVRTC binary-capacity and 6 lowered-name cases.
- 8 PTX boundary, 9 NVRTC architecture and 18 PCI bus-ID cases.

Tests ran through JUnit 4.13.2 on JDK 22 with freshly compiled Java 8-targeted
classes (`javac --release 8`). This was direct JUnit execution, not a Maven run.
Native suites use isolated child JVMs. JVM library logs confirmed all four DLLs
loaded from the merged worktree; runtime probes reported both compilers as 12.6.
PCI identifiers round-tripped through the driver and runtime APIs.

Equivalent Maven selection, after installing the normal fork dependencies:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=PointerBufferStateTest,PointerByteBufferOffsetTest,PointerCharBufferTest,PointerHeapBufferTest,LibUtilsExtractionTest,SizeofPointerTest,FlagCombinationStringTest,JNvrtcBinaryCapacityTest,JNvrtcLoweredNameTest,JNvPTXCompilerBoundaryTest,JNvrtcArchitectureCapacityTest,JCudaPciBusIdTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

Supply matching NVRTC/cudart DLLs to the process. Official native JARs do not
contain these fixes. The CPU-only profile intentionally excludes the native suites.

## Limits

This run covers CPU compilation and GPU device metadata, not kernel execution,
the full GPU suite or the multi-repository reactor. The earlier 198-case and
optional GPU results remain historical evidence, not newly repeated results.
No device reset, explicit context creation, device allocation, ingestion pause
or art interruption was needed. Linux, 32-bit, actual JDK 8 runtime, allocation
failure injection and concurrent program mutation remain unverified. No native
binaries are committed or released.
