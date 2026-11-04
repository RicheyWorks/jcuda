#!/usr/bin/env python3
"""Resolve only a manually requested, SHA-pinned public PR; never execute it."""
import json
import os
from pathlib import Path
import re
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args):
        return None


def resolve(number, expected, trusted, get):
    if not re.fullmatch(r"[0-9]{1,8}", number) or not re.fullmatch(r"[0-9a-f]{40}", trusted):
        raise ValueError("A numeric PR and exact trusted base SHA are required")
    if int(number) == 0:
        if expected and expected != trusted:
            raise ValueError("Base verification must match the trusted SHA")
        return {"kind": "trusted-base", "pr": 0, "candidate_sha": trusted, "trusted_sha": trusted}
    if not re.fullmatch(r"[0-9a-f]{40}", expected):
        raise ValueError("Explicit expected PR head SHA is required")
    pr = get("https://api.github.com/repos/RicheyWorks/jcuda/pulls/" + str(int(number)))
    if pr.get("state") != "open" or pr.get("number") != int(number):
        raise ValueError("PR must still be open in this fork")
    if pr.get("base", {}).get("repo", {}).get("full_name") != "RicheyWorks/jcuda":
        raise ValueError("PR targets a different repository")
    if pr.get("head", {}).get("repo", {}).get("private", True):
        raise ValueError("Only public candidate sources are supported without credentials")
    if pr.get("head", {}).get("sha") != expected:
        raise ValueError("PR head changed; inspect and explicitly authorize the new SHA")
    return {"kind": "untrusted-pr", "pr": int(number), "candidate_sha": expected, "trusted_sha": trusted}


def public_get(url):
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "RicheyWorks-manual-verification"})
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=20) as stream:
        data = stream.read(1024 * 1024 + 1)
    if len(data) > 1024 * 1024:
        raise ValueError("Response too large")
    return json.loads(data)


def main():
    result = resolve(os.environ.get("PR_NUMBER", ""), os.environ.get("EXPECTED_SHA", ""), os.environ.get("TRUSTED_SHA", ""), public_get)
    Path("resolution.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
        stream.write("candidate_sha=" + result["candidate_sha"] + "\n")
        stream.write("trusted_sha=" + result["trusted_sha"] + "\n")
    print("Pinned candidate resolved; execution requires the separately defined isolated job.")


if __name__ == "__main__":
    main()
