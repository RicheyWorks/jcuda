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

**Area:** CI/build. **Hardware:** none. **Dependency:** the Java 8 compiler baseline and focused test selection documented in FORK_INTEGRATION.md.

Propose a minimal CI job that compiles the Java module and runs only explicitly identified no-CUDA tests. The three draft Pointer test classes in #49–#51 are candidate inputs; they are not yet in upstream `master`. The parent POM and Java compiler baseline need deliberate handling rather than an undocumented cache substitution.

The proposed [parent build fix #2](https://github.com/jcuda/jcuda-parent/pull/2) now gives a reviewable Java 8 baseline tested with JDK 21/22. It is still unmerged; evaluate that proposal explicitly rather than treating it as upstream's current policy.

**Acceptance:** Pin the build environment, resolve/install the actual parent, print tool versions, select only tests that do not load CUDA, and publish clear test results. A clean runner must reproduce the job. Do not silently skip native tests and describe the result as full CUDA coverage, or change the upstream compiler target incidentally. Discuss an upstream CI proposal before expanding it across all repositories.
