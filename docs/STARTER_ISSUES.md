# Potential starter issues

These are scoped proposals for discussion in the contributor fork. They have **not** been filed upstream, assigned, or approved by the upstream maintainer. Recheck current issues, PRs and comments before starting. Keep each proposal in its own branch; ask here if someone is already implementing it.

## 1. Heap-buffer pointer coverage — completed in this fork

PointerHeapBufferTest now covers seven primitive types and five heap layouts (35 cases) without CUDA. The cpu-tests Maven profile runs these plus 78 existing Java-only cases. See [CPU_TESTS.md](CPU_TESTS.md). This is coverage for existing correct behavior, not a new production binding fix or an unassigned proposal.

## 2. Primary-context test cleanup — completed in this fork

The retained reference is now released in finally. Two isolated ownership regressions fail before/pass after; five focused context checks pass together. See the [development log](DEVELOPMENT_LOG.md). This is no longer an unassigned starter proposal.

## 3. Add a CPU-only CI check for the integrated fork

**Area:** CI/build. **Hardware:** none. The Java8 parent baseline and focused Java-only test classes are already integrated in the RicheyWorks forks.

The local cpu-tests profile now supplies 113 verified Java-only cases. Propose a minimal job in this fork that obtains the actual parent and runs that profile on a clean runner. Dependency resolution still needs the declared Maven artifacts. Do not submit this work upstream.

**Status:** A separate direct-javac CPU harness and weekly workflow are now prepared locally. They avoid Maven/native dependencies and pass all 113 cases locally. Publishing the workflows is awaiting explicit approval after automatic review denied the push; a clean hosted run is still unverified. See [contributor automation](CONTRIBUTOR_AUTOMATION.md). The Maven parent/profile CI variant above remains a proposal. Do not call either variant full CUDA coverage.
