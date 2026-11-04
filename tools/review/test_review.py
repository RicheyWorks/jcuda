import io
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import resolve_candidate
import verify_cpu
import weekly_review

SHA = "a" * 40
OTHER = "b" * 40


def pr():
    return {"number": 7, "title": "Preserve pointer offsets", "draft": False, "state": "open",
            "head": {"sha": SHA, "repo": {"private": False}},
            "base": {"sha": OTHER, "repo": {"full_name": "RicheyWorks/jcuda"}}, "user": {"login": "contributor"}}


def archive(name, kind=None):
    data = io.BytesIO()
    with tarfile.open(fileobj=data, mode="w:gz") as tar:
        entry = tarfile.TarInfo(name)
        if kind:
            entry.type = kind
            entry.linkname = "/outside"
            tar.addfile(entry)
        else:
            body = b"class Example {}"
            entry.size = len(body)
            tar.addfile(entry, io.BytesIO(body))
    return data.getvalue()


class ReviewTests(unittest.TestCase):
    def test_small_test_backed_change_selected_for_review(self):
        result = weekly_review.summarize(pr(), [{"filename": "JCudaJava/src/test/java/X.java", "patch": "+assertEquals(1, value);", "changes": 2}])
        self.assertGreater(result["review_priority"], 0)
        self.assertIn("never approval", result["decision"])

    def test_workflow_and_credential_surfaces_not_selected(self):
        result = weekly_review.summarize(pr(), [{"filename": ".github/workflows/run.yml", "patch": "+pull_request_target\n+permissions: write-all\n+secrets.TOKEN\n+ghp_" + "A" * 36}])
        self.assertEqual(0, result["review_priority"])
        self.assertTrue(any("credential" in flag for flag in result["security_signals"]))
        self.assertNotIn("patch", result["files"][0])

    def test_native_memory_requires_separate_checks(self):
        result = weekly_review.file_signals({"filename": "JCudaDriverJNI/src/Copy.cpp", "patch": "+memcpy(out,in,n);"})
        self.assertIn("native-memory/lifetime: native/GPU checks required", result["flags"])

    def test_titles_never_become_html_or_commands(self):
        title = '<script>$(touch PWNED)</script> [run](javascript:alert(1)) ghp_' + "A" * 36
        safe = weekly_review.safe_text(title)
        self.assertNotIn("<script>", safe)
        self.assertNotIn("ghp_", safe)
        self.assertIn("&lt;script", safe)
        self.assertIn("\\[run\\]", safe)

    def test_stale_or_truncated_snapshot_not_selected(self):
        files = [{"filename": "JCudaJava/src/test/java/X.java", "changes": 1, "patch": "+test"}]
        self.assertEqual(0, weekly_review.summarize(pr(), files, stable=False)["review_priority"])
        self.assertEqual(0, weekly_review.summarize(pr(), files, truncated=True)["review_priority"])

    def test_repository_allowlist_rejects_upstream_and_ssrf(self):
        api = weekly_review.GitHub()
        with self.assertRaises(ValueError):
            api.get("jcuda/jcuda", "/pulls")
        with self.assertRaises(ValueError):
            api.get("RicheyWorks/jcuda", "https://example.com/")

    def test_no_pr_snapshot_and_audit_files(self):
        class Empty:
            def get(self, *_args):
                return []
        report = weekly_review.collect(Empty())
        self.assertEqual([], report["errors"])
        with tempfile.TemporaryDirectory() as root:
            out = Path(root) / "audit"
            weekly_review.write_report(report, out)
            self.assertTrue((out / "audit-manifest.json").is_file())
            self.assertIn("No open PRs", (out / "highlights.md").read_text())

    def test_collection_failure_visible(self):
        class Fail:
            def get(self, *_args):
                raise RuntimeError("HTTP403")
        report = weekly_review.collect(Fail())
        self.assertEqual(3, len(report["errors"]))
        self.assertIn("incomplete", weekly_review.markdown(report))

    def test_manual_exact_sha_required(self):
        self.assertEqual(SHA, resolve_candidate.resolve("7", SHA, OTHER, lambda _: pr())["candidate_sha"])
        with self.assertRaises(ValueError):
            resolve_candidate.resolve("7", OTHER, SHA, lambda _: pr())
        with self.assertRaises(ValueError):
            resolve_candidate.resolve("7;echo bad", SHA, OTHER, lambda _: pr())
        with self.assertRaises(ValueError):
            resolve_candidate.resolve("7", "", OTHER, lambda _: pr())

    def test_manual_base_does_not_call_api(self):
        result = resolve_candidate.resolve("0", "", SHA, lambda _: self.fail("No PR API needed for base"))
        self.assertEqual("trusted-base", result["kind"])

    def test_private_or_closed_pr_rejected(self):
        item = pr()
        item["head"]["repo"]["private"] = True
        with self.assertRaises(ValueError):
            resolve_candidate.resolve("7", SHA, OTHER, lambda _: item)
        item = pr()
        item["state"] = "closed"
        with self.assertRaises(ValueError):
            resolve_candidate.resolve("7", SHA, OTHER, lambda _: item)

    def test_archive_traversal_drive_and_control_names_rejected(self):
        for name in ("../outside.java", "jcuda-" + SHA + "/../outside.java",
                     "jcuda-" + SHA + "/JCudaJava/src/main/java/C:/outside.java",
                     "jcuda-" + SHA + "/JCudaJava/src/main/java/Bad\nName.java"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as root:
                with self.assertRaises(ValueError):
                    verify_cpu.extract_java(archive(name), Path(root) / "out", SHA)

    def test_archive_links_rejected(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as root:
                with self.assertRaises(ValueError):
                    verify_cpu.extract_java(archive("jcuda-" + SHA + "/link", kind), Path(root) / "out", SHA)

    def test_only_java_inputs_extracted(self):
        with tempfile.TemporaryDirectory() as root:
            out = Path(root) / "out"
            verify_cpu.extract_java(archive("jcuda-" + SHA + "/malicious.sh"), out, SHA)
            self.assertEqual([], list(out.rglob("*")))
        with tempfile.TemporaryDirectory() as root:
            out = Path(root) / "out"
            verify_cpu.extract_java(archive("jcuda-" + SHA + "/JCudaJava/src/main/java/Example.java"), out, SHA)
            self.assertTrue((out / "JCudaJava/src/main/java/Example.java").is_file())

    def test_child_environment_strips_tokens(self):
        with patch.dict(os.environ, {"GITHUB_TOKEN": "dummy", "OPENAI_API_KEY": "dummy", "ACTIONS_RUNTIME_TOKEN": "dummy", "AWS_SECRET_ACCESS_KEY": "dummy"}):
            env = verify_cpu.child_environment(Path("fresh-home"))
        self.assertFalse(any(key in env for key in ("GITHUB_TOKEN", "OPENAI_API_KEY", "ACTIONS_RUNTIME_TOKEN", "AWS_SECRET_ACCESS_KEY")))
        self.assertEqual("fresh-home", env["HOME"])

    def test_renamed_workflow_still_flagged(self):
        result = weekly_review.file_signals({"filename": "README.md", "previous_filename": ".github/workflows/old.yml", "patch": "-old"})
        self.assertIn("workflow/permissions", result["flags"])

    def test_missing_patch_is_incomplete_review(self):
        result = weekly_review.summarize(pr(), [{"filename": "JCudaJava/src/test/java/Test.java"}])
        self.assertEqual(0, result["review_priority"])
        self.assertTrue(any("unavailable" in flag for flag in result["security_signals"]))

    def test_collect_correlates_exact_head_and_checks(self):
        class One:
            def get(self, repo, suffix):
                if repo != "RicheyWorks/jcuda":
                    return []
                if suffix.startswith("/pulls?"):
                    return [{"number": 7}]
                if "/files?" in suffix:
                    return [{"filename": "JCudaJava/src/test/java/Test.java", "patch": "+test", "changes": 1}]
                if "/check-runs?" in suffix:
                    return {"check_runs": [{"head_sha": SHA, "name": "CPU", "status": "completed", "conclusion": "success"},
                                           {"head_sha": OTHER, "name": "stale", "status": "completed", "conclusion": "success"}]}
                return pr()
        result = weekly_review.collect(One())["repositories"][0]["pull_requests"][0]
        self.assertEqual(1, len(result["checks"]))
        self.assertEqual(SHA, result["head_sha"])

    def test_workflow_policy_and_action_pins(self):
        folder = Path(__file__).resolve().parents[2] / ".github/workflows"
        for path in folder.glob("*.yml"):
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("pull_request_target:", text)
            self.assertNotIn("self-hosted", text)
            self.assertNotIn("secrets.", text)
            self.assertNotIn("workflow_run:", text)
            self.assertNotIn("cache:", text)
            for line in text.splitlines():
                if "uses:" in line:
                    self.assertRegex(line, r"uses: actions/[a-z-]+@[0-9a-f]{40}")
        manual = (folder / "verify-candidate.yml").read_text()
        self.assertNotIn("github.token", manual)
        self.assertGreaterEqual(manual.count("permissions: {}"), 3)


if __name__ == "__main__":
    unittest.main()
