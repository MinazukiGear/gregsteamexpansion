#!/usr/bin/env python3
"""Verify core-only, AE2-only and complete optional runtimes in isolated worlds."""

from __future__ import annotations

import argparse
import subprocess
import sys

from verification_common import ROOT, archive_fixture_world, gradle_command

PROFILES = ("core", "ae2", "full")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    parser.add_argument("--profile", choices=PROFILES, help="verify only one runtime combination")
    args = parser.parse_args()
    try:
        for profile in (args.profile,) if args.profile else PROFILES:
            archive_fixture_world(f"dependency-{profile}")
            command = gradle_command(f"-PgseDependencySmoke={profile}", "runGameTestServer",
                                     offline=args.offline)
            print(f"\n=== Optional dependency runtime: {profile} ===", flush=True)
            subprocess.run(command, cwd=ROOT, check=True)
    except subprocess.CalledProcessError as error:
        print(f"Dependency runtime verification failed (exit code {error.returncode}).", file=sys.stderr)
        return error.returncode or 1
    print("\nDependency runtime verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
