#!/usr/bin/env python3
"""Measure real terminal previews in an isolated, opt-in GameTest server."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
import platform
import subprocess
import sys
from uuid import uuid4

from verification_common import ROOT, archive_fixture_world, gradle_command


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", choices=("baseline", "optimized", "current"), default="current")
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    args = parser.parse_args()
    report_dir = ROOT / "build/verification/terminal-preview/reports" / (
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + args.label + "-" + uuid4().hex[:8])
    report_dir.mkdir(parents=True)
    report_path = report_dir / "measurements.json"
    inputs = [ROOT / name for name in (
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateTerminalWorldData.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateStructurePlanner.java",
        "src/main/java/com/hoshino/gregsteamexpansion/benchmark/TerminalPreviewBenchmark.java",
        "build.gradle", "gradle.properties",
    )]
    receipt = {
        "label": args.label,
        "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "git_status": subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True),
        "host": platform.platform(), "architecture": platform.machine(),
        "python": platform.python_version(), "java_home": os.environ.get("JAVA_HOME"),
        "inputs_sha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in inputs},
        "scope": "Sequential server preview batches, simulated survival owners, AE disabled; not MSPT/FPS or real concurrent clients.",
    }
    (report_dir / "source.json").write_text(json.dumps(receipt, indent=2) + "\n", "utf-8")
    try:
        archive_fixture_world("terminal-preview")
        command = gradle_command("-PgsePreviewBenchmark=true",
                                 f"-PgsePreviewBenchmarkOutput={report_path}", "runGameTestServer",
                                 offline=args.offline)
        # Preserve the JVM/Gradle's original log bytes (Windows may emit a legacy code page).
        with (report_dir / "server.log").open("wb") as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            print(f"Benchmark failed ({result.returncode}); see {report_dir / 'server.log'}", file=sys.stderr)
            return result.returncode
        report = json.loads(report_path.read_text("utf-8"))
        assert len(report["cases"]) == 20, "Expected two rounds of the matrix and large single target"
        assert all(case["samples"] == 5 and case["selected_cells"] > 0 for case in report["cases"])
        print(f"Report: {report_path}")
        for case in report["cases"]:
            print(f"fixture={case['fixture']} round={case['round']} owners={case['owners']} targets={case['targets']} "
                  f"p50={case['p50_batch_ms']:.3f}ms max={case['max_batch_ms']:.3f}ms")
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, AssertionError) as error:
        print(f"Benchmark failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
