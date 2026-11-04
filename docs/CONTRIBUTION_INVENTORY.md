# Beautiful Java contribution inventory

Snapshot verified **2026-11-04**. This is a running record of proposals from this contributor fork, not a claim that upstream has accepted them. Refresh PR status before sharing a report.

**Milestone count: 6 distinct implemented and verified proposed fixes out of 20. Upstream merged fixes: 0.** All six remain draft proposals. The missing `CharBuffer` path is counted once as an API omission fix; the two `getByteBuffer` overloads are one fix, and the shared-JNI fix plus its companion test PR are one fix.

## Counted fixes

| # | Behavior addressed | Upstream draft PR | Verification and limits |
| --- | --- | --- | --- |
| 1 | `Pointer.toBuffer` discards a direct buffer's saved mark | [jcuda #49](https://github.com/jcuda/jcuda/pull/49) | 13 no-CUDA cases fail before and pass after; six supported types, slices and a read-only direct buffer |
| 2 | Both `getByteBuffer` overloads ignore the pointer's stored offset | [jcuda #50](https://github.com/jcuda/jcuda/pull/50) | 12 no-CUDA cases; 8 fail before, all pass after; includes ranges, shared memory and overflow |
| 3 | `Pointer.toBuffer` rejects otherwise supported character buffers | [jcuda #51](https://github.com/jcuda/jcuda/pull/51) | 6 no-CUDA cases; 5 valid inputs error before, all pass after; Java pointer-construction coverage, not native character transfer |
| 4 | `cuCtxSetCurrent(null)` throws instead of following CUDA's null-context contract | [jcuda #52](https://github.com/jcuda/jcuda/pull/52) | 2 driver-context cases fail before and pass against rebuilt JNI with `-Xcheck:jni`; private CPU threads and cleaned-up temporary context, no kernels/transfers |
| 5 | Shared JNI forces `JNI_ABORT` for an otherwise pinned array, discarding native output under HotSpot's JNI checker | [jcuda-common #4](https://github.com/jcuda/jcuda-common/pull/4) | Companion [jcuda #53](https://github.com/jcuda/jcuda/pull/53): 12 heap/direct numeric transfer cases pass normally but fail with zero output under `-Xcheck:jni`; all pass under checking after rebuilding with the common fix |
| 6 | Failed native-resource extraction leaves a partial file that blocks a fresh extraction on retry | [jcuda #54](https://github.com/jcuda/jcuda/pull/54) | 3 no-CUDA cases: injected read failure/retry, preservation of existing files, and missing-resource handling; 1 case fails before, all pass after. Cleanup is best-effort; existing corrupt files and cross-process extraction races are not repaired |

All are independent branches from their respective upstream master. Fixes #1–#3 also applied together without conflicts and passed their combined 31 no-CUDA cases in an isolated worktree. Other tests were validated on their respective native builds; do not describe the separate results as one complete CUDA-suite run.

Java validation used declared JUnit 4.13.1, Temurin JDK 22, and an isolated local parent adjusted to Java 8 source/target because upstream's Java 7 compiler target was rejected. That build adaptation is disclosed in the PRs and is not part of their source changes.

Native checks used Windows x86_64, RTX 4090, driver 617.42, existing MSVC 19.44/CMake, and checksum-verified official CUDA 12.6 headers/import libraries extracted into the workspace. JVM library logging confirmed the rebuilt DLL. These checks do not establish full-toolkit, Linux, JDK 8 or cross-library compatibility. GPU transfers used at most 24 device bytes per case; no kernels, resets or ingestion pauses were needed.

## Supporting contributions, not additional fixes

- [jcuda #53](https://github.com/jcuda/jcuda/pull/53) is the companion regression for fix #5, not fix #6.
- [Fork draft PR #1](https://github.com/RicheyWorks/jcuda/pull/1) contains contributor setup guidance, scoped starter proposals, this inventory and the milestone plan. Documentation and planning do not increase the fix count.
- [Starter proposals](STARTER_ISSUES.md) are unassigned ideas, not implemented fixes or published upstream issues.

## Counting and reporting rules

Count a distinct production bug or missing supported behavior only after implementing it and verifying it proportionately. Keep the reproduction, source changes and test evidence reviewable. Do not split overloads, multiple parameterized cases, companion repositories, or follow-up polish into extra fixes for the same root cause. Track documentation, tests, tooling and unimplemented ideas separately.

At **20** counted fixes, prepare the Beautiful Java report with one row per distinct fix, linked commits/PRs, before/after behavior, test evidence, compatibility limits and current status. Separate local work, submitted drafts, accepted/merged changes and released behavior. Include reproducible entry points for new contributors and remaining gaps. Do not wait for a convenient headline or claim that 20 proposals means 20 upstream merges.

After that report, proceed to the [original JCuda GPU art milestone](BEAUTIFUL_JAVA_ROADMAP.md). No art benchmark or GPU-computation claim exists yet.
