# jcuda
JCuda - Java bindings for CUDA

This is the **RicheyWorks contributor fork** of [upstream JCuda](https://github.com/jcuda/jcuda). The verified changes are now combined in this fork for its own development. This is not an official JCuda release; the original project and MIT attribution are preserved.

Want to help with Java, CUDA, JNI, testing or build setup? Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the [scoped starter proposals](docs/STARTER_ISSUES.md). Please check upstream issues and draft PRs before starting duplicate work.

## Build and run on Windows

See [the repeatable Windows build](docs/WINDOWS_BUILD.md) for matching fork Java/native JARs, a hash-checked launcher, and a tiny real GPU smoke test. It uses existing toolchain prerequisites and publishes no release.

For Java-only verification, install the sibling parent and run `mvn -f JCudaJava/pom.xml -Pcpu-tests test`: **113 checks without a GPU**. See [CPU test prerequisites and scope](docs/CPU_TESTS.md).

## 🏆 Fix log

**20 distinct fixes integrated in the RicheyWorks forks:** 19 runtime/API fixes (including diagnostic formatting) and 1 build fix. The combined stack passed 198 focused regression cases plus 9 optional round-trip checks; a tiny GPU kernel also passed. Historical upstream submissions remain unmerged; those links are records of individual patches, not a promise of upstream review. This source integration is not a binary release. See [integration and testing details](docs/FORK_INTEGRATION.md). Companion tests, documentation and the separately credited community dependency do not increase our count.

| # | Fix | Historical upstream draft |
| --- | --- | --- |
| 1 | Preserve direct-buffer marks when creating position-aware pointers | [Draft #49](https://github.com/jcuda/jcuda/pull/49) |
| 2 | Honor stored pointer offsets in both `getByteBuffer` views | [Draft #50](https://github.com/jcuda/jcuda/pull/50) |
| 3 | Support character buffers in `Pointer.toBuffer` | [Draft #51](https://github.com/jcuda/jcuda/pull/51) |
| 4 | Allow `cuCtxSetCurrent(null)` to unbind the calling thread's context | [Draft #52](https://github.com/jcuda/jcuda/pull/52) |
| 5 | Preserve native array output under JNI checking by honoring copy-back mode | [jcuda-common draft #4](https://github.com/jcuda/jcuda-common/pull/4), [companion tests #53](https://github.com/jcuda/jcuda/pull/53) |
| 6 | Remove incomplete native-library resources after failed extraction so retry can succeed | [Draft #54](https://github.com/jcuda/jcuda/pull/54) |
| 7 | Avoid pointer-size initialization failure when the data-model property is missing; infer known architectures | [Draft #55](https://github.com/jcuda/jcuda/pull/55) |
| 8 | **Build:** replace rejected Java 7 compiler options with an explicit Java 8 baseline | [jcuda-parent draft #2](https://github.com/jcuda/jcuda-parent/pull/2) |
| 9 | Preserve device-pointer offsets when creating and querying driver texture resource descriptors | [Draft #56](https://github.com/jcuda/jcuda/pull/56) |
| 10 | **Diagnostics:** name combined memory-handle and graphics-registration flags instead of reporting INVALID | [Draft #57](https://github.com/jcuda/jcuda/pull/57) |
| 11 | Fix JNI field/handle errors and missing location values in the virtual-memory allocation/property workflow | [Draft #58](https://github.com/jcuda/jcuda/pull/58) |
| 12 | Return sparse-array tile dimensions in fresh result objects for plain and mipmapped queries | [Draft #59](https://github.com/jcuda/jcuda/pull/59) |
| 13 | Preserve native size counts in managed-memory advice/prefetch instead of truncating them on Win64 | [Draft #60](https://github.com/jcuda/jcuda/pull/60) |
| 14 | Preserve device name, UUID and LUID outputs when JNI uses copied array storage | [Draft #61](https://github.com/jcuda/jcuda/pull/61) |
| 15 | Reject invalid device-name output lengths before native array access | [Draft #62](https://github.com/jcuda/jcuda/pull/62) |
| 16 | Read virtual-memory access descriptor locations from the correct nested object | [Draft #63](https://github.com/jcuda/jcuda/pull/63) |
| 17 | Honor caller capacities and bound native output in five driver graph-list queries | [Draft #64](https://github.com/jcuda/jcuda/pull/64) |
| 18 | Return stream access-policy windows through the correct nested object and preserve their field values | [Draft #65](https://github.com/jcuda/jcuda/pull/65) |
| 19 | Return NVRTC lowered-name errors without reading an invalid output pointer or reporting false out-of-memory | [Draft #66](https://github.com/jcuda/jcuda/pull/66) |
| 20 | Reject undersized NVRTC binary-output arrays before native writes | [Draft #67](https://github.com/jcuda/jcuda/pull/67) |

See the [verified inventory](docs/CONTRIBUTION_INVENTORY.md) for before/after evidence and testing limits. The 20-fix report was prepared privately; the next milestone is [original JCuda GPU art](docs/BEAUTIFUL_JAVA_ROADMAP.md). Join in with a focused, reproducible contribution; upstream review and release decisions remain with the original project.

Further fork work is recorded in the [development log](docs/DEVELOPMENT_LOG.md), including the primary-context test leak fix and its ownership regressions.

The fork also has [weekly contributor inspection and explicit CPU verification](docs/CONTRIBUTOR_AUTOMATION.md), with read-only reports and no automatic PR execution or merging.

Refer to [jcuda-main](https://github.com/jcuda/jcuda-main) for further
information and build instructions.
