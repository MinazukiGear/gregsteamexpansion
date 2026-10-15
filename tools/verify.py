#!/usr/bin/env python3
"""Run the complete repository gate on Windows, Linux or macOS without Bash."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys

from generate_assets import verify_environment
from verification_common import ROOT, archive_fixture_world, gradle_command, run_checked
import verify_config_restart
import verify_dependency_runtime
import verify_server_restart


GENERATED_ROOT = ROOT / "src" / "generated" / "resources"


def generated_state(directory: Path = GENERATED_ROOT) -> dict[str, str]:
    return {
        path.relative_to(directory).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in directory.rglob("*")
        if path.is_file() and ".cache" not in path.relative_to(directory).parts
    }


def verify_generated_unchanged(before: dict[str, str], directory: Path = GENERATED_ROOT) -> None:
    after = generated_state(directory)
    changed = sorted(name for name in before.keys() | after.keys() if before.get(name) != after.get(name))
    if changed:
        raise RuntimeError("Datagen changed generated resources: " + ", ".join(changed[:20])
                           + ". Review the output; no changes were reverted.")


def verify_java() -> None:
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) \
        if java_home else shutil.which("java")
    if not java:
        raise RuntimeError("JDK 17 is required. Set JAVA_HOME to an existing JDK 17.")
    result = subprocess.run([java, "-version"], capture_output=True, text=True, check=True, timeout=30)
    version = re.search(r'version "([^"]+)"', result.stdout + result.stderr)
    if version is None or version.group(1).split(".")[0] != "17":
        raise RuntimeError("This Forge 1.20.1 gate requires JDK 17, including the Gradle JVM. "
                           "Set JAVA_HOME to JDK 17; a Java toolchain alone is insufficient.")
    print(f"Host: {platform.platform()} / {platform.machine()}; Java {version.group(1)}; "
          f"Python {platform.python_version()} ({sys.executable})", flush=True)


def run_step(label: str, command: list[str], environment: dict[str, str], *, fixture: str | None = None) -> None:
    print(f"\n=== {label} ===", flush=True)
    run_checked(label, command, environment=environment, fixture=fixture)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    parser.add_argument("--no-datagen", action="store_true", help="local iteration only; skips datagen")
    args = parser.parse_args()
    environment = os.environ.copy()
    environment["GSE_ASSET_PYTHON"] = sys.executable
    environment["PYTHONUTF8"] = "1"
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    try:
        verify_java()
        verify_environment()
        run_step("portability tooling negative fixtures", [sys.executable, "tools/test_portability.py"],
                 environment)
        run_step("verification watchdog fixtures", [sys.executable, "tools/test_verification_watchdog.py"],
                 environment)
        run_step("compile", gradle_command("compileJava", offline=args.offline), environment)
        archive_fixture_world("gametest")
        run_step("gametest", gradle_command("-PgseVerificationRun=gametest", "runGameTestServer",
                                            offline=args.offline), environment, fixture="gametest")
        for module, label in (
            (verify_server_restart, "server restart persistence"),
            (verify_config_restart, "configuration restart (Easy, Normal, Expert)"),
            (verify_dependency_runtime, "optional dependencies (core, AE2, complete)"),
        ):
            print(f"\n=== {label} ===", flush=True)
            result = module.main(["--offline"] if args.offline else [], environment=environment)
            if result:
                raise RuntimeError(f"{label} failed (exit code {result})")
        if not args.no_datagen:
            before = generated_state()
            run_step("datagen freshness", gradle_command("runData", offline=args.offline), environment)
            verify_generated_unchanged(before)
        run_step("build and portability checks", gradle_command("build", offline=args.offline), environment)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Verification failed: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError):
            return error.returncode or 1
        return 1
    print("\nAll gates passed." if not args.no_datagen else "\nAll selected gates passed (datagen skipped).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
