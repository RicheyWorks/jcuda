# Potential starter issues

These are scoped proposals for discussion in the contributor fork. They have **not** been filed upstream, assigned, or approved by the upstream maintainer. Recheck current issues, PRs and comments before starting. Keep each proposal in its own branch; ask here if someone is already implementing it.

## 1. Test heap-buffer slice offsets without loading CUDA

**Area:** Java tests. **Hardware:** none.

The existing `TestPointerToBuffer` uses CUDA host-to-host copies and concentrates on float buffers. Add a separate, parameterized no-CUDA test for array-backed byte, short, int, long, float and double buffers, including nested slices and nonzero positions. Character buffers are being added in draft PR #51, so coordinate before adding that type.

Draft [PR #53](https://github.com/jcuda/jcuda/pull/53) adds actual driver-transfer coverage, but needs CUDA hardware. This proposal is specifically the CPU-only construction layer for contributors and CI without a GPU.

**Acceptance:** Verify the stored byte offset equals `(arrayOffset + position) * elementSize`, and that position, limit and mark remain intact. A test subclass can expose protected `Pointer.getByteOffset()` via the protected copy constructor; avoid production accessors added only for testing. State that these tests verify Java pointer construction, not native memory transfer. Run the focused test under the declared JUnit 4 dependency.

## 2. Release the retained primary context in its existing test

**Area:** GPU test cleanup. **Hardware:** CUDA-capable NVIDIA device/driver.

`JCudaDriverPrimaryContextTest.testPrimaryContextCreation` calls `cuDevicePrimaryCtxRetain` but does not release that retained reference. Add matching cleanup in a `finally` block after a successful retain, preserving the test's existing assertions. Review the CUDA primary-context reference-count contract first.

**Acceptance:** The test releases exactly the reference it acquired even when an assertion fails, and does not reset a shared primary context or destroy resources it does not own. Run the focused test when the GPU is available, report device/driver/native library versions, and explain any unavailable verification. Do not use device reset as cleanup.

## 3. Add a CPU-only CI check once the test branches are integrated

**Area:** CI/build. **Hardware:** none. **Dependency:** agreement on the Java compiler baseline and which pending test PRs to include.

Propose a minimal CI job that compiles the Java module and runs only explicitly identified no-CUDA tests. The three draft Pointer test classes in #49–#51 are candidate inputs; they are not yet in upstream `master`. The parent POM and Java compiler baseline need deliberate handling rather than an undocumented cache substitution.

**Acceptance:** Pin the build environment, resolve/install the actual parent, print tool versions, select only tests that do not load CUDA, and publish clear test results. A clean runner must reproduce the job. Do not silently skip native tests and describe the result as full CUDA coverage, or change the upstream compiler target incidentally. Discuss an upstream CI proposal before expanding it across all repositories.
