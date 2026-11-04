# jcuda
JCuda - Java bindings for CUDA

This is the **RicheyWorks contributor fork** of [upstream JCuda](https://github.com/jcuda/jcuda). We develop focused patches for upstream review; this fork is not an official release or replacement for the original project.

Want to help with Java, CUDA, JNI, testing or build setup? Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the [scoped starter proposals](docs/STARTER_ISSUES.md). Please check upstream issues and draft PRs before starting duplicate work.

## 🏆 Fix log

**17 distinct fixes implemented and verified, proposed upstream; 0 merged.** This includes 16 runtime/API fixes (including diagnostic formatting) and 1 reproduced build fix. These are draft contributions from this fork, not released JCuda behavior or upstream endorsement. Status checked 2026-11-04. Companion tests and documentation do not increase the fix count.

| # | Fix | Upstream review |
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

See the [verified inventory](docs/CONTRIBUTION_INVENTORY.md) for before/after evidence and testing limits. At 20 distinct verified fixes: the Beautiful Java contributor report, then [original JCuda GPU art](docs/BEAUTIFUL_JAVA_ROADMAP.md). Join in with a focused, reproducible contribution; upstream review and release decisions remain with the original project.

Refer to [jcuda-main](https://github.com/jcuda/jcuda-main) for further
information and build instructions.
