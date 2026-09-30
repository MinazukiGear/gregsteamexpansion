#!/usr/bin/env python3
"""Verify core-only, AE2-only and complete optional runtimes in isolated worlds."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
PROFILES = ("core", "ae2", "full")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    parser.add_argument("--profile", choices=PROFILES, help="verify only one runtime combination")
    args = parser.parse_args()
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    try:
        for profile in (args.profile,) if args.profile else PROFILES:
            command = [str(wrapper), "--no-daemon", f"-PgseDependencySmoke={profile}", "runGameTestServer"]
            if args.offline:
                command.append("--offline")
            command.append("--stacktrace")
            print(f"\n=== Optional dependency runtime: {profile} ===", flush=True)
            subprocess.run(command, cwd=ROOT, check=True)
    except subprocess.CalledProcessError as error:
        print(f"Dependency runtime verification failed (exit code {error.returncode}).", file=sys.stderr)
        return error.returncode or 1
    print("\nDependency runtime verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
