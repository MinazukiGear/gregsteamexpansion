#!/usr/bin/env python3
"""Verify machine and terminal transaction persistence across two independent server processes."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

from verification_common import archive_fixture_world, gradle_command as wrapper_command, run_checked


ROOT = Path(__file__).resolve().parent.parent


def gradle_command(offline: bool) -> list[str]:
    return wrapper_command("-PgseVerificationRun=server-restart", "runGameTestServer", offline=offline)


def run_phase(phase: str, command: list[str], environment: dict[str, str] | None = None) -> None:
    environment = dict(os.environ if environment is None else environment)
    environment["GSE_RESTART_TEST_PHASE"] = phase
    print(f"\n=== Server restart persistence: {phase} phase ===", flush=True)
    run_checked(f"server restart {phase}", command, environment=environment, fixture="server-restart")


def main(argv: list[str] | None = None, *, environment: dict[str, str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Run the GameTest server twice to verify state across a full JVM restart."
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="pass --offline to Gradle (dependencies must already be cached)",
    )
    args = parser.parse_args(argv)
    command = gradle_command(args.offline)

    try:
        archive_fixture_world("server-restart")
        run_phase("seed", command, environment)
        run_phase("verify", command, environment)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(
            f"Server restart persistence verification failed during a Gradle run "
            f"({error}).",
            file=sys.stderr,
        )
        return error.returncode or 1 if isinstance(error, subprocess.CalledProcessError) else 1

    print("\nServer restart persistence verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
