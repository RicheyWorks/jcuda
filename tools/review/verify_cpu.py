#!/usr/bin/env python3
"""Trusted harness. Only run unknown candidate code on disposable hosted runners."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import urllib.request

TESTS = ("PointerBufferStateTest", "PointerByteBufferOffsetTest", "PointerCharBufferTest", "PointerHeapBufferTest",
         "LibUtilsExtractionTest", "SizeofPointerTest", "FlagCombinationStringTest")
SHA = re.compile(r"[0-9a-f]{40}\Z")
DEPENDENCIES = {
    "junit": ("https://repo.maven.apache.org/maven2/junit/junit/4.13.1/junit-4.13.1.jar", "c30719db974d6452793fe191b3638a5777005485bae145924044530ffa5f6122"),
    "hamcrest": ("https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar", "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9")}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args):
        return None


def download(url):
    with urllib.request.build_opener(NoRedirect()).open(url, timeout=30) as stream:
        data = stream.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024:
        raise ValueError("Download exceeds limit")
    return data


def extract_java(data, destination, sha, tests=False):
    """Reject archive traversal/links; write only Java inputs, never PR scripts."""
    if not SHA.fullmatch(sha):
        raise ValueError("Invalid source SHA")
    destination.mkdir(parents=True, exist_ok=False)
    total = 0
    seen = set()
    with tarfile.open(fileobj=io.BytesIO(data), mode="r|gz") as archive:
        for index, member in enumerate(archive, 1):
            if index > 5000:
                raise ValueError("Archive entry limit exceeded")
            path = PurePosixPath(member.name)
            if path.is_absolute() or ".." in path.parts or "\\" in member.name or ":" in member.name or any(ord(c) < 32 or c == '"' for c in member.name):
                raise ValueError("Unsafe archive path")
            if not path.parts or path.parts[0] != "jcuda-" + sha:
                raise ValueError("Unexpected archive root")
            if not member.isfile() and not member.isdir():
                raise ValueError("Archive links/special entries are not permitted")
            total += member.size
            if member.size > 8 * 1024 * 1024 or total > 50 * 1024 * 1024:
                raise ValueError("Uncompressed archive limit exceeded")
            relative = PurePosixPath(*path.parts[1:])
            allowed = str(relative).startswith("JCudaJava/src/main/java/") and relative.suffix == ".java"
            if tests:
                allowed = str(relative) in {"JCudaJava/src/test/java/jcuda/test/" + name + ".java" for name in TESTS}
            if not member.isfile() or not allowed:
                continue
            if str(relative) in seen:
                raise ValueError("Duplicate archive entry")
            seen.add(str(relative))
            target = destination.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as source:
                target.write_bytes(source.read())
    return hashlib.sha256(data).hexdigest()


def child_environment(home):
    # Extra hygiene, not a security sandbox. Unknown code requires a disposable VM.
    keep = ("PATH", "JAVA_HOME", "SystemRoot", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT")
    env = {key: os.environ[key] for key in keep if key in os.environ}
    env.update(HOME=str(home), USERPROFILE=str(home), TMP=str(home), TEMP=str(home), TMPDIR=str(home), LANG="C.UTF-8")
    return env


def run(command, out, name, env):
    result = subprocess.run([str(arg) for arg in command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            env=env, timeout=180, check=False)
    text = result.stdout.decode("utf-8", errors="replace")
    (out / (name + ".log")).write_text(text, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(name + " failed; inspect its untrusted diagnostic log")
    return text


def java_inputs(root, tests=False):
    if tests:
        files = [root / "JCudaJava/src/test/java/jcuda/test" / (name + ".java") for name in TESTS]
    else:
        folder = root / "JCudaJava/src/main/java"
        if any(p.is_symlink() for p in folder.rglob("*")):
            raise ValueError("Symlink source is not accepted")
        files = sorted(folder.rglob("*.java"))
    if not files or any(not p.is_file() or p.is_symlink() or any(c in str(p) for c in ('"', '\n', '\r')) for p in files):
        raise ValueError("Missing or unsafe Java inputs")
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--candidate-sha")
    parser.add_argument("--trusted-sha")
    parser.add_argument("--source", type=Path, help="Trusted local baseline only; never run unknown PR code on your PC")
    parser.add_argument("--tests", type=Path)
    parser.add_argument("--junit", type=Path)
    parser.add_argument("--hamcrest", type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    audit = {"schema": 1, "candidate_sha": args.candidate_sha, "trusted_harness_sha": args.trusted_sha,
             "status": "NOT_COMPLETED", "scope": "113 trusted CPU harness cases; no native/GPU/security approval"}
    try:
        if args.source:
            if not args.tests or args.candidate_sha:
                raise ValueError("Local baseline requires --source/--tests only")
            source, tests = args.source.resolve(), args.tests.resolve()
        else:
            if not SHA.fullmatch(args.candidate_sha or "") or not SHA.fullmatch(args.trusted_sha or ""):
                raise ValueError("Two exact Git SHAs are required")
            source, tests = out / "candidate", out / "trusted"
            for sha, folder, is_tests in ((args.candidate_sha, source, False), (args.trusted_sha, tests, True)):
                data = download("https://codeload.github.com/RicheyWorks/jcuda/tar.gz/" + sha)
                audit["trusted_archive_sha256" if is_tests else "candidate_archive_sha256"] = extract_java(data, folder, sha, is_tests)
        main_files, test_files = java_inputs(source), java_inputs(tests, True)
        audit["inputs"] = [{"kind": kind, "path": str(p.relative_to(root)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
                           for kind, root, files in (("candidate", source, main_files), ("trusted-test", tests, test_files)) for p in files]
        deps = []
        for name, provided in (("junit", args.junit), ("hamcrest", args.hamcrest)):
            url, expected = DEPENDENCIES[name]
            data = provided.read_bytes() if provided else download(url)
            if hashlib.sha256(data).hexdigest() != expected:
                raise ValueError("Dependency digest mismatch: " + name)
            path = out / (name + ".jar")
            path.write_bytes(data)
            deps.append(path)
        home, classes, test_classes = out / "child-home", out / "classes", out / "test-classes"
        for folder in (home, classes, test_classes):
            folder.mkdir()
        env = child_environment(home)
        javac, java = "javac", "java"
        if os.environ.get("JAVA_HOME"):
            suffix = ".exe" if os.name == "nt" else ""
            javac = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("javac" + suffix))
            java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java" + suffix))
        run([java, "-version"], out, "java-version", env)
        for name, files, target, cp in (("main", main_files, classes, None), ("tests", test_files, test_classes, os.pathsep.join(map(str, deps + [classes])))):
            argfile = out / (name + "-inputs.txt")
            argfile.write_text("\n".join('"' + p.as_posix() + '"' for p in files), encoding="utf-8")
            command = [javac, "-proc:none", "--release", "8", "-encoding", "UTF-8", "-d", target]
            if cp:
                command += ["-cp", cp]
            run(command + ["@" + str(argfile)], out, "compile-" + name, env)
        # Trusted test/dependency classes precede candidate classes: a PR cannot
        # replace org.junit.Assert merely by adding that class to main sources.
        cp = os.pathsep.join(map(str, [test_classes] + deps + [classes]))
        text = run([java, "-cp", cp, "org.junit.runner.JUnitCore"] + ["jcuda.test." + name for name in TESTS], out, "cpu-tests", env)
        if not re.search(r"OK \(113 tests\)", text):
            raise RuntimeError("Unexpected test count; inspect coverage rather than silently accepting it")
        audit.update(status="PASS", tests=113, native_jar_on_classpath=False)
    except Exception as error:
        audit.update(status="FAILED", error=str(error)[:300])
        raise
    finally:
        (out / "verification.json").write_text(json.dumps(audit, indent=2), encoding="utf-8")
    print("CPU harness completed:113 cases; passing is not security approval.")


if __name__ == "__main__":
    main()
