# Contributing

This is **RicheyWorks' contributor fork** of [JCuda](https://github.com/jcuda/jcuda), the Java bindings for NVIDIA CUDA. The original project and its maintainer remain upstream. This fork is a place to develop focused changes and submit them for upstream review; it is not an official replacement or a new JCuda release.

Java, CUDA and JNI developers are welcome. A useful contribution can be a reproducible bug, a focused regression test, a small binding fix, or a verified build improvement. Review the [starter proposals](docs/STARTER_ISSUES.md) before claiming work; they are proposals, not assigned or already published upstream issues.

Follow the [verified contribution inventory](docs/CONTRIBUTION_INVENTORY.md) for fix counts, PR states and test limits, and the [Beautiful Java milestones](docs/BEAUTIFUL_JAVA_ROADMAP.md) for the 20-fix report and later GPU-art plan.

## Before changing code

1. Search [upstream issues](https://github.com/jcuda/jcuda/issues) and [open pull requests](https://github.com/jcuda/jcuda/pulls), including discussion comments. A complete proposed fix may exist in a comment even without a PR.
2. Read the upstream [build instructions](https://github.com/jcuda/jcuda-main/blob/master/BUILDING.md). `jcuda-main` is the aggregate build; this repository contains the core Java and JNI bindings. The shared parent and native utilities live in sibling repositories.
3. Start one branch per independent change from current upstream `master`. Preserve other contributors' branches and work. Open draft PRs early when there is a concrete patch and honest validation evidence.

## Obtain the source

Use a fresh working directory. Clone the relevant projects as siblings:

```powershell
git clone https://github.com/jcuda/jcuda-parent.git
git clone https://github.com/jcuda/jcuda-common.git
git clone https://github.com/RicheyWorks/jcuda.git
cd jcuda
git remote add upstream https://github.com/jcuda/jcuda.git
git fetch upstream
git switch -c my-focused-fix upstream/master
```

For the complete set of libraries and native packaging, clone the additional repositories listed in `jcuda-main/BUILDING.md`. Do not assume cloning only `jcuda-main` downloads its sibling projects.

## Build and test the Java change

Upstream currently declares version **12.6.0**. Its parent POM configures Java 7 source/target, while some source uses Java 8 APIs. JDK 8 is the expected baseline for that configuration, but we have not verified this checkout on a JDK 8 host. A newer JDK may reject the compiler target; our JDK 22 build did. Changing the parent locally to Java 8 allowed our focused validation, but that was an isolated verification adaptation, not an upstream build fix or supported-version claim.

There is now a separate proposed parent build fix, [jcuda-parent #2](https://github.com/jcuda/jcuda-parent/pull/2), targeting Java 8. Fresh core compilation passed on JDK 21 and 22, and the 48 Java-only regression cases passed on JDK 21 with that parent installed normally. Rebuilt artifacts require Java 8 or newer; no JDK 8 runtime or full packaging/reactor certification is implied.

To opt into that **unmerged proposal** from the fresh sibling layout above, fetch its source branch before installing the parent:

```powershell
git -C ../jcuda-parent fetch https://github.com/RicheyWorks/jcuda-parent.git fix/java8-compiler-baseline
git -C ../jcuda-parent switch -c try-java8-parent FETCH_HEAD
```

With the chosen JDK and Maven available, install the parent before building this Java module:

```powershell
mvn -f ../jcuda-parent/pom.xml install
mvn -f JCudaJava/pom.xml test-compile
```

The module resolves a platform-specific `jcuda-natives` dependency. Compilation alone does not load CUDA; executing the existing CUDA tests does. For a specific regression, select its test class rather than accidentally running the full GPU suite:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=YourRegressionTest' test
```

Replace `YourRegressionTest` with an actual test on your branch. An offline build (`-o`) only works once all required artifacts are cached. Do not commit local repository caches, generated classes, native binaries, or machine-specific paths.

## Native and GPU verification

Native changes need CMake, a compatible C++ toolchain, JNI headers, and matching CUDA headers and import libraries. Use upstream's native build instructions. Inspect the CMake configuration and cache: an older CUDA version on `PATH` can be selected even when a newer toolkit directory was intended. A directory named `v12.6` is not proof that the complete toolkit is installed.

Build and load the **changed** JNI library for native regressions. Passing Java compilation or running against Maven's previously published native JAR does not validate a JNI patch. Report the library path and how the JVM selected it. Record CUDA toolkit/header version separately from the GPU driver version.

Run GPU tests only when the machine's current workload permits it. Prefer isolated test threads, tiny allocations, no unnecessary kernels, and `finally` cleanup. Check available memory and coordinate any needed pause with the workload owner; do not interrupt or overwrite saved assets. A test may require an NVIDIA driver/device even when it launches no kernels.

## Current review work

These are **draft proposals**, not merged fixes or released behavior:

| Upstream PR | Behavior | Evidence |
| --- | --- | --- |
| [#49](https://github.com/jcuda/jcuda/pull/49) | Preserve direct-buffer marks in `Pointer.toBuffer` | 13 focused no-CUDA regression cases |
| [#50](https://github.com/jcuda/jcuda/pull/50) | Honor stored pointer offsets in `getByteBuffer` views | 12 focused no-CUDA regression cases |
| [#51](https://github.com/jcuda/jcuda/pull/51) | Accept `CharBuffer` in `Pointer.toBuffer` | 6 focused no-CUDA regression cases |
| [#52](https://github.com/jcuda/jcuda/pull/52) | Allow `cuCtxSetCurrent(null)` per the CUDA contract | 2 driver-context tests against a freshly built JNI DLL, with JNI checking |
| [jcuda-common #4](https://github.com/jcuda/jcuda-common/pull/4) | Preserve requested critical-array copy-back mode | Companion [jcuda #53](https://github.com/jcuda/jcuda/pull/53): 12 tiny native-transfer cases pass under JNI checking after the shared fix |
| [#54](https://github.com/jcuda/jcuda/pull/54) | Clean up incomplete library-resource extraction so retries can succeed | 3 no-CUDA cases, including an injected mid-read failure followed by successful retry |
| [#55](https://github.com/jcuda/jcuda/pull/55) | Fall back to known architecture when the pointer-size model property is unavailable | 14 no-CUDA cases in isolated JVMs; simulated properties, not hardware certification |
| [jcuda-parent #2](https://github.com/jcuda/jcuda-parent/pull/2) | Fix rejected Java 7 compiler settings with an explicit Java 8 baseline | Fresh core compilation on JDK 21/22; 48 Java-only cases on JDK 21; rebuilt classfiles require Java 8 |
| [jcuda #56](https://github.com/jcuda/jcuda/pull/56) | Preserve driver texture resource device-pointer offsets | 8 GPU descriptor cases pass under JNI checking; 4 fail before; no sampling kernels |
| [jcuda #57](https://github.com/jcuda/jcuda/pull/57) | Format combined handle and graphics-registration flag names | 30 CPU-only cases; 15 fail before, all pass after; all 78 Java-only integration cases pass on JDK 21 |

The three Java Pointer patches (#49–#51) applied together without conflicts and passed their combined 31 cases in an isolated local worktree. Those Maven runs used the disclosed task-local Java 8 parent adaptation under JDK 22. Native transfers and the complete CUDA suite were not part of those 31 tests. Each PR branch remains independent of the others.

PR #52 was checked separately on Windows x86_64 with an RTX 4090 and driver 617.42. Its context tests run on isolated CPU threads, launch no kernels, and destroy the temporary context. The native build used existing MSVC plus checksum-verified official CUDA 12.6 headers/import libraries extracted into the workspace because the local 12.6 toolkit folder was incomplete. This is not evidence that a full CUDA 12.6 toolkit or the complete CUDA test suite was validated.

## What to include in a contribution

- A concrete before/after example and the relevant CUDA API contract for binding changes.
- A regression that fails for the old behavior, plus the exact commands and results for the fix.
- OS/architecture, JDK, Maven, CUDA headers/toolkit and driver details when relevant.
- Any unrun checks, local build adaptations, hardware requirements, or compatibility changes.
- Only related source/test changes, preserving the repository's existing style and license notices.

Use your own fork for publication unless you have explicit write access here. Target upstream for changes intended for JCuda itself; respect its maintainer's review and release decisions. No PR, fork, test result, or social post implies upstream endorsement.
