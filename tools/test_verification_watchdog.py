"""Real subprocess fixtures for watchdog failure, ownership and config recovery."""

from __future__ import annotations

import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import verification_common as common
import verification_watchdog as watchdog
import verify as gate
import verify_config_restart as config_restart
import verify_dependency_runtime as dependency_runtime
import verify_server_restart as server_restart


class WatchdogTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="gse 诊断 ")
        self.root = Path(self.directory.name).resolve()

    def tearDown(self):
        self.directory.cleanup()

    def command(self, code, *args):
        return [sys.executable, "-u", "-c", code, *map(str, args)]

    def run_guarded(self, code, *args, limits=None, **kwargs):
        return watchdog.run_command(self.root, "fixture stage", self.command(code, *args),
                                    limits=limits or watchdog.Limits(timeout=5, idle=1, poll=0.02, exit_grace=0.3),
                                    console=io.StringIO(), **kwargs)

    def records(self):
        return [json.loads(p.read_text("utf-8"))
                for p in sorted((self.root / "build/verification/diagnostics").glob("*/metadata.json"))]

    def assert_heartbeat_stopped(self, path):
        self.assertTrue(path.exists(), "The descendant fixture never became active")
        before = path.read_bytes()
        time.sleep(0.3)
        self.assertEqual(before, path.read_bytes(), "A managed descendant survived cleanup")

    def test_success_preserves_raw_output_and_exit(self):
        result = self.run_guarded("import os; os.write(1,'中文 stdout\\n'.encode()); os.write(2,b'stderr\\n')")
        self.assertEqual(0, result.returncode)
        record = self.records()[0]
        self.assertEqual("passed", record["status"])
        folder = next((self.root / "build/verification/diagnostics").iterdir())
        output = (folder / "stage.log").read_bytes()
        self.assertIn("中文 stdout".encode(), output)
        self.assertIn(b"stderr", output)

    def test_nonzero_exit_preserves_failure_and_current_config(self):
        config = self.root / "build/verification/gametest/config/gregsteamexpansion-common.toml"
        config.parent.mkdir(parents=True)
        config.write_bytes(b"difficulty = 'EASY'\n")
        environment = dict(os.environ, GSE_EXPECTED_DIFFICULTY="EASY", PRIVATE_FIXTURE_SECRET="not evidence")
        with self.assertRaises(subprocess.CalledProcessError) as result:
            self.run_guarded("import sys; print('last test event',flush=True); sys.exit(7)",
                             fixture="gametest", environment=environment)
        self.assertEqual(7, result.exception.returncode)
        record = self.records()[0]
        self.assertEqual("nonzero_exit", record["reason"])
        self.assertEqual({"GSE_EXPECTED_DIFFICULTY": "EASY"}, record["configuration"])
        folder = next((self.root / "build/verification/diagnostics").iterdir())
        self.assertEqual(config.read_bytes(), (folder / "config" / config.name).read_bytes())
        self.assertIn(b"last test event", (folder / "last-output.log").read_bytes())

    def test_silent_process_fails_on_idle_limit(self):
        with self.assertRaises(watchdog.WatchdogError):
            self.run_guarded("import time; print('ready',flush=True); time.sleep(60)")
        record = self.records()[0]
        self.assertEqual("output_stalled", record["reason"])
        self.assertEqual("failed", record["status"])

    def test_output_without_newlines_resets_idle_but_not_absolute_limit(self):
        with self.assertRaises(watchdog.WatchdogError):
            self.run_guarded("import os,time\nwhile True: os.write(1,b'progress'); time.sleep(.04)",
                             limits=watchdog.Limits(timeout=2, idle=1, poll=0.02))
        record = self.records()[0]
        self.assertEqual("stage_timeout", record["reason"])
        self.assertGreater(record["output_bytes"], 40)

    def descendant_code(self):
        return ("import pathlib,sys,time\np=pathlib.Path(sys.argv[1])\n"
                "while True: p.write_text(str(time.monotonic())); time.sleep(.05)")

    def parent_code(self, exit_after_ready=False):
        ending = "sys.exit(0)" if exit_after_ready else "time.sleep(60)"
        return ("import pathlib,subprocess,sys,time\n"
                "p=pathlib.Path(sys.argv[1])\n"
                "child=subprocess.Popen([sys.executable,'-u','-c',sys.argv[2],str(p)])\n"
                "while not p.exists(): time.sleep(.02)\n"
                "print('descendant ready',flush=True)\n" + ending)

    def test_timeout_cleans_descendant_and_leaves_unrelated_process_alive(self):
        unrelated = subprocess.Popen(self.command("import time; time.sleep(60)"),
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        heartbeat = self.root / "owned heartbeat 中文.txt"
        try:
            with self.assertRaises(watchdog.WatchdogError):
                self.run_guarded(self.parent_code(), heartbeat, self.descendant_code())
            self.assert_heartbeat_stopped(heartbeat)
            self.assertIsNone(unrelated.poll(), "Cleanup reached an unrelated process")
            self.assertGreaterEqual(len(self.records()[0]["diagnostics"]["processes"]), 2)
        finally:
            unrelated.terminate()
            unrelated.wait(timeout=5)

    def test_launcher_exit_with_live_descendant_fails_and_cleans_it(self):
        heartbeat = self.root / "orphan heartbeat.txt"
        with self.assertRaises(watchdog.WatchdogError):
            self.run_guarded(self.parent_code(True), heartbeat, self.descendant_code())
        self.assertEqual("orphaned_children", self.records()[0]["reason"])
        self.assert_heartbeat_stopped(heartbeat)

    def test_invalid_limits_fail_before_command_starts(self):
        marker = self.root / "must-not-start.txt"
        code = "import pathlib,sys; pathlib.Path(sys.argv[1]).write_text('started')"
        for value in ("0", "-1", "nan", "inf", "not a number"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                watchdog.Limits(timeout=float(value))
        for value in ("0", "-1", "nan", "inf", "not a number"):
            with self.subTest(environment=value), self.assertRaises(ValueError):
                watchdog.run_command(self.root, "invalid", self.command(code, marker),
                                     environment=dict(os.environ, GSE_VERIFY_TIMEOUT_SECONDS=value),
                                     console=None)
        self.assertFalse(marker.exists())

    def test_large_output_is_bounded_but_retains_recent_tail(self):
        with patch.object(watchdog, "MAX_LOG_BYTES", 32):
            self.run_guarded("import os; os.write(1,b'a'*1000+b'latest marker')")
        record = self.records()[0]
        self.assertTrue(record["log_truncated"])
        folder = next((self.root / "build/verification/diagnostics").iterdir())
        self.assertEqual(32, (folder / "stage.log").stat().st_size)
        self.assertIn(b"latest marker", (folder / "last-output.log").read_bytes())

    def test_scratch_boundary_rejects_outside_and_redirected_evidence(self):
        with self.assertRaises(ValueError):
            watchdog._scratch_path(self.root, self.root / "outside")
        scratch = self.root / "build/verification"
        scratch.mkdir(parents=True)
        elsewhere = self.root / "elsewhere"
        elsewhere.mkdir()
        try:
            (scratch / "diagnostics").symlink_to(elsewhere, target_is_directory=True)
        except OSError:
            return  # The non-symlink boundary assertion still ran on restricted Windows.
        marker = self.root / "must-not-start.txt"
        with self.assertRaises(ValueError):
            self.run_guarded("import pathlib,sys; pathlib.Path(sys.argv[1]).write_text('started')", marker)
        self.assertFalse(marker.exists())
        self.assertFalse(list(elsewhere.iterdir()))

    def test_configuration_timeout_restores_existing_or_absent_original(self):
        for existing in (True, False):
            with self.subTest(existing=existing):
                config = self.root / "build/verification/config-restart/config/gregsteamexpansion-common.toml"
                config.parent.mkdir(parents=True, exist_ok=True)
                original = b"original bytes\r\n" if existing else None
                if existing:
                    config.write_bytes(original)
                else:
                    config.unlink(missing_ok=True)
                template = self.root / "template.toml"
                template.write_text("template", encoding="utf-8")
                environment = dict(os.environ, GSE_VERIFY_TIMEOUT_SECONDS="5", GSE_VERIFY_IDLE_SECONDS="1")
                with patch.object(common, "ROOT", self.root), \
                        patch.object(config_restart, "CONFIG_PATH", config), \
                        patch.object(config_restart, "TEMPLATE_PATH", template), \
                        patch.object(config_restart, "archive_fixture_world", return_value=None), \
                        patch.object(config_restart, "PHASES", (config_restart.PHASES[0],)), \
                        patch.object(config_restart, "write_config", side_effect=lambda *_: config.write_bytes(b"EASY override")), \
                        patch.object(config_restart, "gradle_command", return_value=self.command("import time; print('ready',flush=True); time.sleep(60)")), \
                        contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(1, config_restart.main(["--offline"], environment=environment))
                self.assertEqual(original, config.read_bytes() if config.exists() else None)
        self.assertTrue(all(r["configuration"]["GSE_EXPECTED_DIFFICULTY"] == "EASY" for r in self.records()))

    def test_restart_failure_does_not_start_verify_phase(self):
        with patch.object(common, "ROOT", self.root), \
                patch.object(server_restart, "archive_fixture_world", return_value=None), \
                patch.object(server_restart, "gradle_command", return_value=self.command("import time; print('seed ready',flush=True); time.sleep(60)")), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            result = server_restart.main([], environment=dict(os.environ, GSE_VERIFY_TIMEOUT_SECONDS="5",
                                                              GSE_VERIFY_IDLE_SECONDS="1"))
        self.assertEqual(1, result)
        self.assertEqual(["server restart seed"], [r["stage"] for r in self.records()])
        self.assertEqual("seed", self.records()[0]["configuration"]["GSE_RESTART_TEST_PHASE"])

    def test_gate_stops_after_failed_in_process_phase_and_passes_offline(self):
        with patch.object(sys, "argv", ["verify.py", "--offline", "--no-datagen"]), \
                patch.object(gate, "verify_java"), patch.object(gate, "verify_environment"), \
                patch.object(gate, "archive_fixture_world"), patch.object(gate, "run_step") as steps, \
                patch.object(gate.verify_server_restart, "main", return_value=0) as restart, \
                patch.object(gate.verify_config_restart, "main", return_value=1) as config, \
                patch.object(gate.verify_dependency_runtime, "main", return_value=0) as dependencies, \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, gate.main())
        self.assertEqual(["--offline"], restart.call_args.args[0])
        self.assertEqual(["--offline"], config.call_args.args[0])
        self.assertEqual("1", config.call_args.kwargs["environment"]["PYTHONUTF8"])
        dependencies.assert_not_called()
        self.assertFalse(any(c.args[0] == "build and portability checks" for c in steps.call_args_list))

    def test_dependency_timeout_reports_failure_and_stops_other_profiles(self):
        with patch.object(common, "ROOT", self.root), \
                patch.object(dependency_runtime, "archive_fixture_world", return_value=None), \
                patch.object(dependency_runtime, "gradle_command", return_value=self.command("import time; print('core ready',flush=True); time.sleep(60)")), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as errors:
            result = dependency_runtime.main(["--offline"], environment=dict(
                os.environ, GSE_VERIFY_TIMEOUT_SECONDS="5", GSE_VERIFY_IDLE_SECONDS="1"))
        self.assertEqual(1, result)
        self.assertIn("output_stalled", errors.getvalue())
        self.assertEqual(["optional dependency core"], [r["stage"] for r in self.records()])

    def test_guarded_posix_gradle_keeps_daemon_in_group_without_mutating_environment(self):
        environment = {"JAVA_TOOL_OPTIONS": "-Dfixture.existing=kept", "GSE_EXPECTED_DIFFICULTY": "EASY"}
        command = ["sh", str(common.ROOT / "gradlew"), "--no-daemon", "compileJava"]
        with patch.object(common.os, "name", "posix"), patch.object(common, "run_command") as run:
            common.run_checked("compile", command, environment=environment)
        self.assertEqual("-Dfixture.existing=kept -Dorg.gradle.native=false",
                         run.call_args.kwargs["environment"]["JAVA_TOOL_OPTIONS"])
        self.assertEqual("-Dfixture.existing=kept", environment["JAVA_TOOL_OPTIONS"])
        self.assertEqual(command, run.call_args.args[2])
        with patch.object(common.os, "name", "nt"), patch.object(common, "run_command") as run:
            common.run_checked("compile", command, environment=environment)
        self.assertEqual(environment, run.call_args.kwargs["environment"])
        with patch.object(common.os, "name", "posix"), patch.object(common, "run_command") as run:
            common.run_checked("other command", ["java", "-version"], environment=environment)
        self.assertEqual(environment, run.call_args.kwargs["environment"])

    def test_posix_denied_cleanup_requires_proof_that_no_live_members_remain(self):
        with patch.object(watchdog.shutil, "which", return_value="/usr/bin/ps"):
            tree = watchdog._PosixTree(314159)
        denied = PermissionError(1, "Operation not permitted")
        with patch.object(watchdog.os, "killpg", side_effect=denied, create=True) as kill, \
                patch.object(watchdog.signal, "SIGKILL", 9, create=True), \
                patch.object(tree, "processes", return_value=[]) as members:
            tree.close()
            kill.assert_called_once_with(314159, 9)
            members.assert_called_once_with()
        with patch.object(watchdog.os, "killpg", side_effect=denied, create=True), \
                patch.object(watchdog.signal, "SIGKILL", 9, create=True), \
                patch.object(tree, "processes", return_value=[{"pid": 314160, "image": "java"}]):
            with self.assertRaises(PermissionError):
                tree.close()
        with patch.object(watchdog.os, "killpg", side_effect=denied, create=True), \
                patch.object(watchdog.signal, "SIGKILL", 9, create=True), \
                patch.object(tree, "processes", side_effect=subprocess.SubprocessError("ps failed")):
            with self.assertRaises(subprocess.SubprocessError):
                tree.close()

    def test_jvm_thread_dump_is_captured_before_owned_jvm_is_killed(self):
        java_home = os.environ.get("JAVA_HOME")
        suffix = ".exe" if os.name == "nt" else ""
        executables = {name: Path(java_home) / "bin" / (name + suffix) if java_home else
                       Path(shutil.which(name) or "missing") for name in ("java", "javac", "jcmd")}
        if not all(path.is_file() for path in executables.values()):
            self.skipTest("JDK java/javac/jcmd are required for the real attach fixture")
        source = self.root / "WatchdogJvmFixture.java"
        source.write_text('public class WatchdogJvmFixture { public static void main(String[] a) throws Exception {'
                          'Thread.currentThread().setName("gse-watchdog-fixture");'
                          'System.out.println("JVM fixture ready"); Thread.sleep(60000); }}', encoding="utf-8")
        # Keep the real Unicode/space working directory, but avoid passing it
        # through JDK 17's Windows launcher argument encoding.
        compiled = subprocess.run([str(executables["javac"]), "-encoding", "UTF-8", source.name],
                                  cwd=self.root, timeout=30,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.assertEqual(0, compiled.returncode, compiled.stdout.decode("utf-8", errors="replace"))
        with self.assertRaises(watchdog.WatchdogError):
            watchdog.run_command(self.root, "real JVM fixture",
                                 [str(executables["java"]), "-cp", ".", "WatchdogJvmFixture"],
                                 limits=watchdog.Limits(timeout=10, idle=3, poll=0.05), console=io.StringIO())
        record = self.records()[0]
        self.assertEqual("output_stalled", record["reason"])
        self.assertEqual(1, len(record["diagnostics"]["jvms"]))
        entry = record["diagnostics"]["jvms"][0]
        self.assertEqual(0, entry.get("returncode"), entry)
        folder = next((self.root / "build/verification/diagnostics").iterdir())
        self.assertIn("gse-watchdog-fixture", (folder / entry["file"]).read_text("utf-8", errors="replace"))


if __name__ == "__main__":
    unittest.main()
