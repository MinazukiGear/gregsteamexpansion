"""Shared, shell-independent Gradle invocation for repository verification."""

from __future__ import annotations

import os
from pathlib import Path
import shutil
import sys
from uuid import uuid4


ROOT = Path(__file__).resolve().parent.parent


def archive_fixture_world(fixture: str) -> Path | None:
    """Start a fresh scratch world while retaining the previous failure/persistence evidence."""
    if fixture not in {"gametest", "server-restart", "config-restart",
                       "dependency-core", "dependency-ae2", "dependency-full"}:
        raise ValueError(f"Unknown verification fixture: {fixture}")
    verification = ROOT.resolve() / "build" / "verification"
    world = verification / fixture / "world"
    history = verification / "world-history"
    if not world.exists() and not world.is_symlink():
        return None
    # Directory moves must stay in the repository's known scratch area, including
    # when a checkout contains an unexpected symlink or Windows junction.
    if world.resolve() != world or history.resolve() != history:
        raise RuntimeError("Verification world/history must not redirect outside its scratch path")
    backup = history / str(uuid4()) / fixture / "world"
    backup.parent.mkdir(parents=True)
    world.rename(backup)
    print(f"Archived previous verification world: {backup}", flush=True)
    return backup


def gradle_command(*tasks: str, offline: bool = False) -> list[str]:
    if os.name == "nt":
        wrapper = [str(ROOT / "gradlew.bat")]
    else:
        shell = shutil.which("sh")
        if shell is None:
            raise RuntimeError("A POSIX sh is required to run the Gradle wrapper.")
        # Source ZIPs and Windows checkouts may not preserve the executable bit.
        wrapper = [shell, str(ROOT / "gradlew")]
    command = [*wrapper, "--no-daemon", "--console=plain",
               f"-PassetPythonExecutable={sys.executable}", *tasks]
    if offline:
        command.append("--offline")
    return [*command, "--stacktrace"]
