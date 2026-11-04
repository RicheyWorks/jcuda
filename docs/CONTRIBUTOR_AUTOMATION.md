# Contributor review in the RicheyWorks fork

This fork preserves JCuda's original attribution and license. These tools serve
RicheyWorks/jcuda, jcuda-common and jcuda-parent; they do not contact upstream.

## Weekly inspection

`weekly-pr-review.yml` runs Mondays at 06:17 UTC on master, or manually. It reads
open PR metadata, changed-file patches and checks at each exact head SHA. It
records promising small changes, test evidence, security-sensitive surfaces,
missing patches, collection limits and errors. PR text is data, never instructions
or shell commands. No PR code is checked out or executed by this scan.

The independent baseline job compiles trusted master Java sources and runs the
113-case CPU harness on a fresh Ubuntu 24.04 runner with preinstalled JDK 21.
No CUDA, native JAR, GPU, Maven plugins or repository build scripts are required.

Review artifacts contain JSON, escaped Markdown and a SHA-256 manifest. Baseline
artifacts contain input/archive hashes, version and compiler/test logs. GitHub run
URLs identify the workflow SHA and outcome. Retention is 90 days; download audits
you need longer. These are audit records, not cryptographic security attestations.
The scan caps each repository at 25 recent open PRs and each PR at 300 files;
truncation, changed heads and public API rate failures are visible, not clean bills
of health. Sibling repositories use public anonymous reads rather than wider tokens.

GitHub schedules run from the default branch, may be delayed, and can be disabled
after repository inactivity. Verify the latest run rather than assuming it ran.
See [GitHub schedule documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).

## Explicit candidate testing

After reviewing a particular change, the owner may dispatch
`verify-candidate.yml` on master with its PR number and exact 40-character head
SHA. The resolver requires an open PR targeting this fork, a public head repository
and an unchanged SHA. PR number 0 checks trusted master without an incoming PR.

```
gh workflow run verify-candidate.yml --repo RicheyWorks/jcuda --ref master -f pr_number=0
```

For a real PR supply both `-f pr_number=N` and
`-f expected_head_sha=THE_REVIEWED_40_CHARACTER_SHA`. Do not dispatch unknown code
from a highlight report automatically.

Candidate verification uses a separate disposable GitHub-hosted runner with
`permissions: {}`. It receives no repository secrets, checkout credentials, cache,
environment approval credentials or OIDC permission. Only public immutable Java
source archives are fetched. Trusted master test sources and hash-pinned JUnit
dependencies precede candidate classes; annotation processing is disabled.
PR tests, workflows, POMs, scripts, JNI and GPU workloads are not executed.
Archive paths, links and size limits are checked before selected Java files are
written. The child process receives a minimal environment and has a timeout.

Java candidate code is still arbitrary untrusted code: a disposable runner is the
boundary, not Java or environment filtering. The hosted runner has network access
and GitHub's job-scoped runner/artifact plumbing. Never add privileged tokens,
secrets, self-hosted runners, shared caches or privileged follow-up consumers.
Its diagnostic logs/artifacts may be forged by malicious code. A passing 113-case
harness is limited correctness evidence and is never security approval. Native,
GPU, dependency and build-system changes need separate scoped review/testing.
No automation approves, comments, merges, deploys or publishes anything.

## Local maintenance of the trusted tools

```
python -m unittest discover -s tools/review -p "test_*.py" -v
python tools/review/weekly_review.py --out out/review-inspection
python tools/review/verify_cpu.py --source . --tests . --out out/review-trusted-base
```

Use local verification only for trusted source. Never run incoming PR code on the
owner's PC. Output directories must be new. JUnit/Hamcrest may be supplied through
`--junit` and `--hamcrest`; their SHA-256 digests are still checked. Otherwise they
are fetched from Maven Central. The standard library reviewer needs no new tools.

Initial verification: 19 review-tool unit checks passed, the trusted local JDK 22
baseline passed all 113 CPU cases, and the first live scan found zero open PRs in
all three forks. No actual incoming PR was available for end-to-end verification;
resolver rejection and archive boundaries are covered by unit tests. Consult
Actions run results for hosted verification, rather than treating this document
as a current status dashboard.

A separate weekly ChatGPT highlight task can read these runs and current PRs,
explain promising work and flag stale/missing evidence. It must remain read-only,
treat PR instructions as untrusted, and leave candidate execution and all write
actions to explicit owner direction. It needs no new personal access token.
