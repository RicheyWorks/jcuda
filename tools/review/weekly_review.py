#!/usr/bin/env python3
"""Read-only PR triage. Patches are data: never checkout or execute PR code."""
import argparse
import datetime as dt
import hashlib
import html
import json
import os
from pathlib import Path
import re
import unicodedata
import urllib.error
import urllib.request

REPOS = ("RicheyWorks/jcuda", "RicheyWorks/jcuda-common", "RicheyWorks/jcuda-parent")
SECRET = re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
SHA = re.compile(r"[0-9a-f]{40}\Z")


def safe_text(value):
    text = SECRET.sub("[credential marker redacted]", str(value))
    text = "".join(" " if unicodedata.category(c).startswith("C") else c for c in text)[:240]
    return re.sub(r"([\\`*_{}\[\]()#+.!|>~-])", r"\\\1", html.escape(text, quote=True))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args):
        return None


class GitHub:
    def __init__(self, token=None):
        self.token = token
        self.calls = 0

    def get(self, repo, suffix):
        if repo not in REPOS or not suffix.startswith("/") or ".." in suffix or "://" in suffix:
            raise ValueError("Only fixed user-owned repository GET paths are allowed")
        self.calls += 1
        if self.calls > 500:
            raise RuntimeError("Read budget exceeded; report is incomplete")
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "RicheyWorks-read-only-review", "X-GitHub-Api-Version": "2022-11-28"}
        # Do not expand an Actions token to sibling repositories: those public
        # repositories are read anonymously and rate-limit failures stay visible.
        if self.token and repo == REPOS[0]:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request("https://api.github.com/repos/" + repo + suffix, headers=headers, method="GET")
        try:
            with urllib.request.build_opener(NoRedirect()).open(request, timeout=20) as response:
                data = response.read(8 * 1024 * 1024 + 1)
            if len(data) > 8 * 1024 * 1024:
                raise RuntimeError("Response exceeds review limit")
            return json.loads(data)
        except urllib.error.HTTPError as error:
            # Do not print response bodies, headers, or authentication values.
            raise RuntimeError("GitHub GET returned HTTP " + str(error.code)) from None


def file_signals(file):
    path = str(file.get("filename", ""))
    patch = str(file.get("patch", ""))
    flags = set()
    for lower in (path.lower(), str(file.get("previous_filename", "")).lower()):
        if lower.startswith(".github/workflows/"):
            flags.add("workflow/permissions")
        if lower.endswith(("pom.xml", ".ps1", ".sh", ".bat", ".cmd", ".cmake")) or lower.endswith("cmakelists.txt") or lower.startswith("tools/"):
            flags.add("build-execution/dependencies")
        if lower.endswith((".cpp", ".hpp", ".h", ".cu", ".cuh")):
            flags.add("native-memory/lifetime: native/GPU checks required")
        if re.search(r"(^|/)(\.env($|\.)|credentials\.json|auth\.json|[^/]+\.(pem|p12|pfx|key))$", lower):
            flags.add("sensitive credential/configuration path")
    if "patch" not in file:
        flags.add("patch unavailable: manual inspection")
    if re.search(r"Runtime\s*\.\s*getRuntime|ProcessBuilder|subprocess|\beval\s*\(|\bexec\s*\(|curl\s|wget\s", patch):
        flags.add("process/network execution")
    if re.search(r"pull_request_target|write-all|contents:\s*write|id-token:\s*write|self-hosted|secrets\.", patch):
        flags.add("privileged workflow surface")
    if SECRET.search(patch):
        flags.add("potential credential marker: inspect privately")
    return {"path": safe_text(path), "previous_path": safe_text(file.get("previous_filename", "")), "status": safe_text(file.get("status", "unknown")),
            "changes": int(file.get("changes", 0)), "test_file": "/src/test/" in path.lower(),
            "patch_available": "patch" in file, "patch_sha256": hashlib.sha256(patch.encode()).hexdigest() if "patch" in file else None,
            "flags": sorted(flags)}


def summarize(pr, files, checks=None, stable=True, truncated=False):
    number = pr.get("number")
    sha = pr.get("head", {}).get("sha", "")
    if not isinstance(number, int) or number < 1 or not SHA.fullmatch(sha):
        raise ValueError("Invalid PR identity/head SHA")
    signals = [file_signals(file) for file in files]
    flags = sorted({flag for file in signals for flag in file["flags"]})
    changes = sum(file["changes"] for file in signals)
    tests = sum(file["test_file"] for file in signals)
    severe = any("workflow" in flag or "credential" in flag or "build-execution" in flag or "unavailable" in flag for flag in flags)
    score = (40 if tests else 0) + (20 if len(files) <= 10 and changes <= 400 else 0)
    score -= 100 if severe or not stable or truncated or pr.get("draft") else 0
    return {"number": number, "title": safe_text(pr.get("title", "")), "draft": bool(pr.get("draft")),
            "head_sha": sha, "base_sha": pr.get("base", {}).get("sha"), "updated_at": pr.get("updated_at"),
            "author": safe_text(pr.get("user", {}).get("login", "unknown")), "files": signals,
            "security_signals": flags, "tests_touched": tests, "changed_lines_observed": changes,
            "stable_snapshot": stable, "files_truncated": truncated, "review_priority": max(0, score),
            "checks": checks or [], "decision": "human review required; never approval"}


def collect(api):
    report = {"schema": 1, "captured_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
              "controller_sha": os.environ.get("GITHUB_SHA", "local run"), "repositories": [], "errors": [],
              "scope": "read-only heuristics and existing CI observations; not a security certification"}
    for repo in REPOS:
        entry = {"repository": repo, "pull_requests": [], "truncated": False}
        report["repositories"].append(entry)
        try:
            prs = api.get(repo, "/pulls?state=open&sort=updated&direction=desc&per_page=26")
            entry["truncated"] = len(prs) > 25
            if entry["truncated"]:
                report["errors"].append({"repository": repo, "stage": "PR-list", "error": "Open PR collection capped at25"})
            for item in prs[:25]:
                number = item.get("number")
                if not isinstance(number, int) or number < 1:
                    raise ValueError("Invalid PR number")
                pr = api.get(repo, "/pulls/" + str(number))
                files = []
                for page in range(1, 4):
                    batch = api.get(repo, f"/pulls/{number}/files?per_page=100&page={page}")
                    files.extend(batch)
                    if len(batch) < 100:
                        break
                sha = pr.get("head", {}).get("sha", "")
                if not SHA.fullmatch(sha):
                    raise ValueError("Invalid head SHA")
                checks = []
                try:
                    raw = api.get(repo, f"/commits/{sha}/check-runs?per_page=100")
                    checks = [{"name": safe_text(c.get("name", "unknown")), "status": c.get("status"),
                               "conclusion": c.get("conclusion")} for c in raw.get("check_runs", []) if c.get("head_sha") == sha]
                except RuntimeError as error:
                    report["errors"].append({"repository": repo, "pr": number, "stage": "checks", "error": str(error)})
                current = api.get(repo, "/pulls/" + str(number))
                entry["pull_requests"].append(summarize(pr, files, checks,
                    stable=current.get("head", {}).get("sha") == sha,
                    truncated=int(pr.get("changed_files", len(files))) > len(files)))
        except (RuntimeError, ValueError, TypeError, KeyError) as error:
            report["errors"].append({"repository": repo, "stage": "collection", "error": str(error)[:180]})
    return report


def markdown(report):
    lines = ["# JCuda contributor review", "", "Captured: " + report["captured_utc"], "",
             "Read-only triage. PR titles/patches are untrusted data. Signals are prompts for human review, not approval.", ""]
    candidates = []
    total = 0
    for repo in report["repositories"]:
        name = repo["repository"]
        lines += ["## " + name, ""]
        if repo["truncated"]:
            lines += ["Only the25 most recently updated open PRs were collected.", ""]
        if not repo["pull_requests"]:
            lines += ["No open PRs observed (see collection warnings if incomplete).", ""]
        for pr in repo["pull_requests"]:
            total += 1
            url = f"https://github.com/{name}/pull/{pr['number']}"
            lines += [f"- [#{pr['number']}]({url}) {pr['title']} {'(draft)' if pr['draft'] else ''}",
                      f"  - Head: `{pr['head_sha']}`; {len(pr['files'])} files observed; {pr['tests_touched']} test files touched.",
                      "  - Review signals: " + ("; ".join(pr["security_signals"]) or "none from these limited heuristics"),
                      "  - Existing checks: " + (", ".join(safe_text(c["conclusion"] or c["status"] or "unknown") for c in pr["checks"]) or "none observed"),
                      "  - Candidate code was not executed by this report."]
            if not pr["stable_snapshot"] or pr["files_truncated"]:
                lines += ["  - Incomplete/changed snapshot: recollect before deciding."]
            if pr["review_priority"] > 0:
                candidates.append((pr["review_priority"], name, pr))
        lines.append("")
    lines += ["## Promising changes to inspect", ""]
    for _, repo, pr in sorted(candidates, key=lambda v: -v[0])[:3]:
        lines += [f"- [{repo} #{pr['number']}](https://github.com/{repo}/pull/{pr['number']}): {pr['title']} — small/test-backed review candidate, not cleared for merge."]
    if not candidates:
        lines += ["No candidate selected from the current limited signals."]
    lines += ["", f"Observed {total} open PR snapshots. Native/GPU behavior and complete security review remain human work.",
              "Manual candidate verification requires an explicit PR number and exact expected head SHA; no automatic approval, comment, merge, deployment or release."]
    if report["errors"]:
        lines += ["", "## Collection warnings", "", "Report is incomplete:"]
        lines += ["- " + safe_text(json.dumps(error)) for error in report["errors"]]
    return "\n".join(lines) + "\n"


def write_report(report, out):
    out.mkdir(parents=True, exist_ok=False)
    (out / "review.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    text = markdown(report)
    (out / "highlights.md").write_text(text, encoding="utf-8")
    files = [{"path": p.name, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(out.iterdir())]
    (out / "audit-manifest.json").write_text(json.dumps(files, indent=2), encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write(text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    report = collect(GitHub(os.environ.get("GITHUB_TOKEN")))
    write_report(report, args.out)
    print("Read-only PR report written; collection warnings: " + str(len(report["errors"])))
    return 2 if report["errors"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
