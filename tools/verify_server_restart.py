#!/usr/bin/env python3
"""Verify machine and terminal transaction persistence across two independent server processes."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

from verification_common import archive_fixture_world, gradle_command as wrapper_command


ROOT = Path(__file__).resolve().parent.parent


def gradle_command(offline: bool) -> list[str]:
    return wrapper_command("-PgseVerificationRun=server-restart", "runGameTestServer", offline=offline)


def run_phase(phase: str, command: list[str]) -> None:
    environment = os.environ.copy()
    environment["GSE_RESTART_TEST_PHASE"] = phase
    print(f"\n=== Server restart persistence: {phase} phase ===", flush=True)
    subprocess.run(command, cwd=ROOT, env=environment, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run the GameTest server twice to verify state across a full JVM restart."
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="pass --offline to Gradle (dependencies must already be cached)",
    )
    args = parser.parse_args()
    command = gradle_command(args.offline)

    try:
        archive_fixture_world("server-restart")
        run_phase("seed", command)
        run_phase("verify", command)
    except subprocess.CalledProcessError as error:
        print(
            f"Server restart persistence verification failed during a Gradle run "
            f"(exit code {error.returncode}).",
            file=sys.stderr,
        )
        return error.returncode or 1

    print("\nServer restart persistence verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
