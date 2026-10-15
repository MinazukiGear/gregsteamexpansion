#!/usr/bin/env python3
"""Measure real terminal previews in an isolated, opt-in GameTest server."""

from __future__ import annotations

import argparse
import contextlib
from datetime import datetime, timezone
import hashlib
import io
import json
import os
import platform
import subprocess
import sys
from uuid import uuid4

from verification_common import ROOT, archive_fixture_world, gradle_command, run_checked


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", choices=("baseline", "optimized", "current"), default="current")
    parser.add_argument("--mode", choices=("preview", "refresh", "materials"), default="preview")
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    args = parser.parse_args()
    fixture = "terminal-" + args.mode
    report_dir = ROOT / "build/verification" / fixture / "reports" / (
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + args.label + "-" + uuid4().hex[:8])
    report_dir.mkdir(parents=True)
    report_path = report_dir / "measurements.json"
    inputs = [ROOT / name for name in (
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateTerminalWorldData.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateStructurePlanner.java",
        "src/main/java/com/hoshino/gregsteamexpansion/benchmark/TerminalPreviewBenchmark.java",
        "src/main/java/com/hoshino/gregsteamexpansion/benchmark/TerminalRefreshBenchmark.java",
        "src/main/java/com/hoshino/gregsteamexpansion/benchmark/TerminalMaterialsBenchmark.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/compat/UltimateTerminalAECompat.java",
        "src/main/java/com/hoshino/gregsteamexpansion/gametest/GSETerminalRefreshTestSupport.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateTerminalMessages.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateTerminalEvents.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/UltimateTerminalSnapshot.java",
        "src/main/java/com/hoshino/gregsteamexpansion/terminal/TerminalPreviewMetrics.java",
        "build.gradle", "gradle.properties",
    )]
    receipt = {
        "label": args.label,
        "mode": args.mode,
        "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "git_status": subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True),
        "host": platform.platform(), "architecture": platform.machine(),
        "python": platform.python_version(), "java_home": os.environ.get("JAVA_HOME"),
        "inputs_sha256": {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                          for path in inputs},
        "scope": ("Simulated survival owners; materials mode compares backpack-only with an actual AE wireless grid and mounted item cell. "
                  "Refresh/materials modes use real server tick events and private encoded recipients. No sockets, compression or client FPS."),
    }
    (report_dir / "source.json").write_text(json.dumps(receipt, indent=2) + "\n", "utf-8")
    try:
        archive_fixture_world(fixture)
        command = gradle_command("-PgsePreviewBenchmark=true",
                                 f"-PgsePreviewBenchmarkMode={args.mode}",
                                 f"-PgsePreviewBenchmarkOutput={report_path}", "runGameTestServer",
                                 offline=args.offline)
        # Preserve the JVM/Gradle's original log bytes (Windows may emit a legacy code page).
        with (report_dir / "server.log").open("wb") as log:
            # Route the shared watchdog's console copy to the byte-preserving report log.
            with io.TextIOWrapper(log, encoding="utf-8", write_through=True) as console, contextlib.redirect_stdout(console):
                run_checked("terminal " + args.mode + " benchmark", command, fixture=fixture)
        report = json.loads(report_path.read_text("utf-8"))
        if args.mode in ("refresh", "materials"):
            assert len(report["cases"]) == (24 if args.mode == "materials" else 28), "Unexpected workload matrix"
            assert all(case["sample_ticks"] == 100 and case["selected_cells"] > 0 for case in report["cases"])
            stages = {"refresh", "preview", "plan", "requirements", "materials", "snapshot", "diff", "encode", "candidates",
                      "inventory_query", "ae_lookup", "ae_query"}
            assert all(set(case["stages"]) == stages and case["stages"]["plan"]["calls"] > 0
                       for case in report["cases"]), "Missing segmented refresh timings"
        else:
            assert len(report["cases"]) == 20, "Expected two rounds of the matrix and large single target"
            assert all(case["samples"] == 5 and case["selected_cells"] > 0 for case in report["cases"])
        print(f"Report: {report_path}")
        for case in report["cases"]:
            prefix = f"fixture={case['fixture']} round={case['round']} owners={case['owners']} targets={case['targets']} "
            if args.mode in ("refresh", "materials"):
                print(prefix + f"source={case.get('material_source', 'backpack')} phase={case['phase']} END-p95={case['end_p95_ms']:.3f}ms "
                      f"END-max={case['end_max_ms']:.3f}ms peak-packets={case['peak_packets_per_tick']}")
            else:
                print(prefix + f"p50={case['p50_batch_ms']:.3f}ms max={case['max_batch_ms']:.3f}ms")
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, AssertionError) as error:
        print(f"Benchmark failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
