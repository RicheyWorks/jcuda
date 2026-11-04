# jcuda
JCuda - Java bindings for CUDA

This is the **RicheyWorks contributor fork** of [upstream JCuda](https://github.com/jcuda/jcuda). We develop focused patches for upstream review; this fork is not an official release or replacement for the original project.

Want to help with Java, CUDA, JNI, testing or build setup? Start with [CONTRIBUTING.md](CONTRIBUTING.md) and the [scoped starter proposals](docs/STARTER_ISSUES.md). Please check upstream issues and draft PRs before starting duplicate work.

## 🏆 Fix log

**5 distinct fixes implemented and verified, proposed upstream; 0 merged.** These are draft contributions from this fork, not released JCuda behavior or upstream endorsement. Status checked 2026-11-04. Companion tests and documentation do not increase the fix count.

| # | Fix | Upstream review |
| --- | --- | --- |
| 1 | Preserve direct-buffer marks when creating position-aware pointers | [Draft #49](https://github.com/jcuda/jcuda/pull/49) |
| 2 | Honor stored pointer offsets in both `getByteBuffer` views | [Draft #50](https://github.com/jcuda/jcuda/pull/50) |
| 3 | Support character buffers in `Pointer.toBuffer` | [Draft #51](https://github.com/jcuda/jcuda/pull/51) |
| 4 | Allow `cuCtxSetCurrent(null)` to unbind the calling thread's context | [Draft #52](https://github.com/jcuda/jcuda/pull/52) |
| 5 | Preserve native array output under JNI checking by honoring copy-back mode | [jcuda-common draft #4](https://github.com/jcuda/jcuda-common/pull/4), [companion tests #53](https://github.com/jcuda/jcuda/pull/53) |

See the [verified inventory](docs/CONTRIBUTION_INVENTORY.md) for before/after evidence and testing limits. At 20 distinct verified fixes: the Beautiful Java contributor report, then [original JCuda GPU art](docs/BEAUTIFUL_JAVA_ROADMAP.md). Join in with a focused, reproducible contribution; upstream review and release decisions remain with the original project.

Refer to [jcuda-main](https://github.com/jcuda/jcuda-main) for further
information and build instructions.
