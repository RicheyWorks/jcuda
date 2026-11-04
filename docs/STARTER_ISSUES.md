# Potential starter issues

These are scoped proposals for discussion in the contributor fork. They have **not** been filed upstream, assigned, or approved by the upstream maintainer. Recheck current issues, PRs and comments before starting. Keep each proposal in its own branch; ask here if someone is already implementing it.

## 1. Test heap-buffer slice offsets without loading CUDA

**Area:** Java tests. **Hardware:** none.

The existing `TestPointerToBuffer` uses CUDA host-to-host copies and concentrates on float buffers. Add a separate, parameterized no-CUDA test for array-backed byte, short, int, long, float and double buffers, including nested slices and nonzero positions. Character-buffer support is now integrated in this fork; include it when extending heap-buffer coverage.

Draft [PR #53](https://github.com/jcuda/jcuda/pull/53) adds actual driver-transfer coverage, but needs CUDA hardware. This proposal is specifically the CPU-only construction layer for contributors and CI without a GPU.

**Acceptance:** Verify the stored byte offset equals `(arrayOffset + position) * elementSize`, and that position, limit and mark remain intact. A test subclass can expose protected `Pointer.getByteOffset()` via the protected copy constructor; avoid production accessors added only for testing. State that these tests verify Java pointer construction, not native memory transfer. Run the focused test under the declared JUnit 4 dependency.

## 2. Primary-context test cleanup — completed in this fork

The retained reference is now released in finally. Two isolated ownership regressions fail before/pass after; five focused context checks pass together. See the [development log](DEVELOPMENT_LOG.md). This is no longer an unassigned starter proposal.

## 3. Add a CPU-only CI check for the integrated fork

**Area:** CI/build. **Hardware:** none. The Java8 parent baseline and focused Java-only test classes are already integrated in the RicheyWorks forks.

Propose a minimal job in this fork that obtains the actual parent, compiles the core and selects only PointerBufferStateTest, PointerByteBufferOffsetTest, PointerCharBufferTest, LibUtilsExtractionTest, SizeofPointerTest and FlagCombinationStringTest. These78 cases do not need a CUDA device; dependency resolution still needs the declared Maven artifacts. Do not submit this work upstream.

**Acceptance:** Pin the runner/JDK and dependency sources, print versions, install the integrated parent normally, publish clear results and verify a clean runner. Do not silently skip native tests and call the result full CUDA coverage. This remains an unimplemented proposal, not a verified CI job.
