# Further fork development

These entries follow the twenty-fix source integration. Test cleanup, build tooling and coverage are recorded separately rather than increasing that original binding/build milestone count. Development targets the RicheyWorks forks; no new upstream submissions or releases are part of these entries.

## 2026-11-04: release the primary-context test's retained reference

`JCudaDriverPrimaryContextTest.testPrimaryContextCreation` successfully retained a primary context but never released its reference. The test itself passed while keeping that process's primary context active. It now pairs a successful retain with `cuDevicePrimaryCtxRelease` in `finally`, preserving its original assertion and releasing only its own reference.

`JCudaPrimaryContextLifetimeTest` verifies this in two isolated child JVMs with `-Xcheck:jni`: a fresh owner-free process and a process holding a separate caller-owned reference. Each invokes the original test three times. The fresh process must return to an inactive primary context after each invocation; the caller-owned case must stay active until the caller releases its own reference, then become inactive. Both fail before the cleanup fix and pass afterward. These are test-resource lifetime checks, not a new production binding behavior.

Five focused cases pass together: the two new lifetime cases, original primary-context creation, and two neighboring current-context cases. Verification used Windows x86_64, RTX4090, driver617.42, Temurin22 and the integrated CUDA12.6 JNI library. JVM library logging and its DLL hash confirmed the fork binary. The process-local CUDA12.6 runtime path must be present: otherwise a failed native load can fall back to the old Maven native resource and invalidate a combined-fork check.

The check creates process-owned context resources only. It launches no kernel, allocates no explicit device data buffer, invokes no context/device reset, and does not pause another workload. It follows NVIDIA's [primary-context release contract](https://docs.nvidia.com/cuda/archive/12.6.0/cuda-driver-api/group__CUDA__PRIMARY__CTX.html). Assertion-failure cleanup is provided by `finally`; no artificial failure was injected into the original assertion. Other platforms/drivers and the complete CUDA suite were not run for this change. Production Java/JNI sources are unchanged, so the existing packaged build remains a valid historical artifact of the same production code.

Reproduce with matching dependent runtime DLLs available to this Maven process:

```powershell
mvn -f JCudaJava/pom.xml '-Dtest=JCudaPrimaryContextLifetimeTest,JCudaDriverPrimaryContextTest,JCudaDriverCurrentContextTest' '-DargLine=-Xcheck:jni -Djava.library.path=<rebuilt-JNI-directory>' test
```

Use [WINDOWS_BUILD.md](WINDOWS_BUILD.md) for prerequisites and matching fork artifact loading. This focused Maven invocation also needs its declared dependencies and integrated parent resolved normally.

## 2026-11-04: one-command CPU suite and heap-buffer coverage

The cpu-tests Maven profile selects 113 Java-only checks, including 35 new heap-buffer cases across seven primitive types and five layouts. All pass on Temurin 22.0.2 and Oracle 21.0.4 with Maven 3.9.9. A deliberate ignored-array-offset mutation caused 21 expected failures and was restored; no production defect is claimed. JDK 21 library traces found no JCuda/NVIDIA loads across Maven and child JVMs. See [CPU_TESTS.md](CPU_TESTS.md) for commands, scope and limits. A remote CI job remains unimplemented. This usability/coverage contribution does not increase the original twenty-fix count.
