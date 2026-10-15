#!/usr/bin/env python3
"""Run the opt-in real OpenGL terminal client benchmark in its own scratch directory."""

from __future__ import annotations

import argparse
import contextlib
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
from uuid import uuid4

from verification_common import ROOT, gradle_command, run_checked


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", choices=("baseline", "optimized", "current"), default="current")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--checks-only", action="store_true", help="verify vertex parity and real logout without timed workloads")
    args = parser.parse_args()
    fixture = ROOT / "build/verification/terminal-client"
    report_dir = fixture / "reports" / (
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + args.label + "-" + uuid4().hex[:8])
    # Reject junctions/symlinks before any write, including a redirected ancestor.
    if report_dir.resolve() != report_dir.absolute():
        raise RuntimeError("Client benchmark scratch directory must not redirect")
    report_dir.mkdir(parents=True)
    report_path = report_dir / "measurements.json"
    print(f"Report: {report_path}", flush=True)
    config = fixture / "config"
    if config.resolve() != config.absolute() or (fixture / "options.txt").resolve() != (fixture / "options.txt").absolute():
        raise RuntimeError("Client benchmark config/options must not redirect")
    config.mkdir(exist_ok=True)
    if (config / "gregsteamexpansion-common.toml").resolve() != (config / "gregsteamexpansion-common.toml").absolute():
        raise RuntimeError("Client benchmark config file must not redirect")
    (config / "gregsteamexpansion-common.toml").write_text(
        "difficultyEnabled = false\ndifficultySetupCompleted = true\n", "utf-8")
    (fixture / "options.txt").write_text(
        "version:3465\nautoJump:false\nfullscreen:false\nforceUnicodeFont:false\n"
        "enableVsync:false\nmaxFps:120\nrenderDistance:4\nsimulationDistance:4\n"
        "graphicsMode:0\nrenderClouds:false\nparticles:2\nao:false\nbiomeBlendRadius:0\n"
        "mipmapLevels:0\nfov:0.0\nguiScale:2\npauseOnLostFocus:false\n"
        "onboardAccessibility:false\nshowGlDebugLabels:false\n", "utf-8")
    # The development client includes Modern UI, whose default 30 FPS background
    # throttle would change the workload when focus moves back to the editor.
    modern_ui = config / "ModernUI"
    modern_options = modern_ui / "client.toml"
    if modern_options.resolve() != modern_options.absolute():
        raise RuntimeError("Client benchmark Modern UI config must not redirect")
    modern_ui.mkdir(exist_ok=True)
    modern_options.write_text("[screen]\nframerateInactive = 0\nframerateMinimized = 0\n", "utf-8")
    inputs = [*sorted((ROOT / "src/main/java").rglob("*.java")), ROOT / "build.gradle", ROOT / "gradle.properties",
              ROOT / "tools/benchmark_terminal_client.py", ROOT / "src/main/resources/gregsteamexpansion.mixins.json"]
    hashes = {}
    for path in inputs:
        name = path.relative_to(ROOT).as_posix()
        data = path.read_bytes()
        hashes[name] = hashlib.sha256(data).hexdigest()
        destination = report_dir / "source-snapshot" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    receipt = {
        "label": args.label, "checks_only": args.checks_only, "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "git_status": subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True),
        "host": platform.platform(), "architecture": platform.machine(), "java_home": os.environ.get("JAVA_HOME"),
        "inputs_sha256": hashes,
        "options_sha256": hashlib.sha256((fixture / "options.txt").read_bytes()).hexdigest(),
        "modern_ui_options_sha256": hashlib.sha256(modern_options.read_bytes()).hexdigest(),
    }
    (report_dir / "source.json").write_text(json.dumps(receipt, indent=2) + "\n", "utf-8")
    try:
        command = gradle_command("-PgseClientBenchmark=true", f"-PgseClientBenchmarkOutput={report_path}",
                                 f"-PgseClientBenchmarkChecksOnly={str(args.checks_only).lower()}",
                                 "runClient", offline=args.offline)
        with (report_dir / "client.log").open("w", encoding="utf-8") as log, contextlib.redirect_stdout(log):
            run_checked("terminal real client benchmark", command, fixture="terminal-client")
        report = json.loads(report_path.read_text("utf-8"))
        assert report.get("complete") and report.get("clear_verified") and report.get("logout_verified"), "Client benchmark did not finish"
        assert report.get("vertex_parity_quads", 0) > 0 and not report.get("failure"), "Client correctness checks failed"
        assert len(report["cases"]) == (0 if args.checks_only else 30), "Unexpected client workload matrix"
        assert (report["width"], report["height"]) == (1280, 720), "Unexpected framebuffer size"
        for case in report["cases"]:
            assert all(len(case[name + "_raw"]) == report["samples"] for name in
                       ("frame", "draw", "ghost", "outline", "allocated_bytes")), "Missing raw samples"
            print(f"round={case['round']} fixture={case['fixture']} action={case['action']} "
                  f"frame-p95={case['frame_p95']:.3f}ms draw-p95={case['draw_p95']:.3f}ms "
                  f"allocated-p50={case['allocated_bytes_p50']:.0f}B")
        after = {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
        assert after == hashes, "Benchmark source changed during sampling"
    except (OSError, ValueError, RuntimeError, AssertionError, subprocess.CalledProcessError) as error:
        print(f"Client benchmark failed: {error}; evidence: {report_dir}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
