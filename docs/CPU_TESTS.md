# Java-only verification without a GPU

From this fork's `jcuda` directory, after installing the integrated sibling parent:

~~~powershell
mvn -f ../jcuda-parent/pom.xml install
mvn -f JCudaJava/pom.xml -Pcpu-tests test
~~~

This explicit Maven profile selects 113 Java-only checks. It needs no CUDA device, driver, toolkit or native compilation. JDK and Maven are prerequisites. Tested on Windows 11 with Maven 3.9.9, Temurin 22.0.2 and Oracle JDK 21.0.4. The integrated parent targets Java 8, but an actual JDK 8 runtime is not verified.

Use the RicheyWorks parent source, not an undocumented cache substitution. The existing Maven dependency declaration still resolves the platform-specific native JAR, but these tests do not load it. Normal dependency access or an already populated cache is required; `-o` only works after caching artifacts. The standalone [Windows artifact build](WINDOWS_BUILD.md) is separate and does not use Maven/native-JAR dependencies.

## What runs

| Test class | Cases | Scope |
| --- | --- | --- |
| `PointerBufferStateTest` | 13 | Direct-buffer position, limit and mark |
| `PointerByteBufferOffsetTest` | 12 | Pointer-relative byte-buffer views |
| `PointerCharBufferTest` | 6 | Character-buffer support and restrictions |
| `PointerHeapBufferTest` | 35 | Seven primitive heap buffer types, five layouts |
| `LibUtilsExtractionTest` | 3 | Resource extraction failure/retry, without native loading |
| `SizeofPointerTest` | 14 | Pointer-size property handling in isolated JVMs |
| `FlagCombinationStringTest` | 30 | Diagnostic flag names |
| **Total** | **113** | Java-only checks |

The profile pins Surefire 3.2.5 and fails if no selected tests are found. The normal test selection is unchanged when the profile is absent. An explicit `-Dtest` overrides selection using ordinary Maven semantics; the 113-case selection describes the profile without that override. This is a subset, not the full CUDA suite or a configured remote CI job.

## Heap-buffer coverage

The 35 heap cases cover byte, char, short, int, long, float and double buffers across an offset wrap window, slice, nested slice, duplicate and empty slice at the backing array's end. Known physical indices distinguish a wrapped offset from a slice's array offset. Tests check stored byte offsets, composed pointer offsets, original pointer immutability, buffer array identity, position/limit/mark preservation and parent-buffer state.

A subclass exposes the existing protected pointer offset through the protected copy constructor; production accessors are unchanged. No reflection into private fields or native memory access is needed. The cases use 40-element arrays, and an end pointer is inspected only as metadata, never dereferenced.

All 35 pass on the existing correct implementation. To check test effectiveness, a temporary deliberate mutation ignored array offsets in the seven conversion paths: 21 slice/nested/end cases failed while 14 wrap/duplicate controls passed. The original source was restored and the complete 113-case suite passed again on JDK 21. This was a deliberately introduced fault, not a newly discovered production bug.

## Evidence and limits

All 113 cases pass on both documented JDKs. Library-load logging across the JDK 21 Maven/test/child JVMs found no JCuda or NVIDIA libraries loaded. No GPU work, ingestion pause or reset occurred.

These tests verify Java pointer construction and state, not JNI pinning, actual native buffer transfer, huge-array integer boundaries, arbitrary platform ABI support, or GPU correctness. Linux, an actual JDK 8 host and a clean remote CI runner remain unverified. See [FORK_INTEGRATION.md](FORK_INTEGRATION.md) for the separately verified native/GPU checks.
