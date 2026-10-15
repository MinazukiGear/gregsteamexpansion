"""Bounded verification commands and diagnostics for their own process trees.

Windows: suspended launch, private kill-on-close Job Object, then ResumeThread.
POSIX: a private session/process group, including ordinary orphaned children.
Verification phase loops run in the caller so configuration finally blocks run.
"""

from __future__ import annotations

import ctypes
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
from uuid import uuid4


MAX_LOG_BYTES = 64 * 1024 * 1024
TAIL_BYTES = 64 * 1024


class WatchdogError(RuntimeError):
    pass


@dataclass(frozen=True)
class Limits:
    timeout: float = 1_200
    idle: float = 600
    poll: float = 0.1
    diagnostic_timeout: float = 5
    exit_grace: float = 10

    def __post_init__(self) -> None:
        for value in (self.timeout, self.idle, self.poll, self.diagnostic_timeout, self.exit_grace):
            if not math.isfinite(value) or value <= 0:
                raise ValueError("Verification limits must be finite positive seconds")

    @classmethod
    def from_environment(cls, environment: dict[str, str]) -> Limits:
        timeout = float(environment.get("GSE_VERIFY_TIMEOUT_SECONDS", "1200"))
        idle = float(environment.get("GSE_VERIFY_IDLE_SECONDS", "600"))
        return cls(timeout=timeout, idle=idle)


def _utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def _scratch_path(root: Path, path: Path) -> Path:
    scratch = root.resolve() / "build" / "verification"
    absolute = path.absolute()
    if not absolute.is_relative_to(scratch) or absolute.resolve() != absolute:
        raise ValueError("Verification evidence/config paths must stay in the real scratch directory")
    return absolute


def _interrupt_python(signum, frame) -> None:
    raise KeyboardInterrupt


def _install_windows_interrupt() -> None:
    if os.name == "nt" and threading.current_thread() is threading.main_thread():
        if signal.getsignal(signal.SIGBREAK) == signal.SIG_DFL:
            signal.signal(signal.SIGBREAK, _interrupt_python)


class _WindowsTree:
    """Only the processes assigned to this newly created, unnamed Job Object."""

    def __init__(self) -> None:
        from ctypes import wintypes as w

        class Basic(ctypes.Structure):
            _fields_ = [("process_time", ctypes.c_int64), ("job_time", ctypes.c_int64),
                        ("flags", w.DWORD), ("min_ws", ctypes.c_size_t), ("max_ws", ctypes.c_size_t),
                        ("active_limit", w.DWORD), ("affinity", ctypes.c_size_t),
                        ("priority", w.DWORD), ("scheduling", w.DWORD)]

        class Counters(ctypes.Structure):
            _fields_ = [(name, ctypes.c_uint64) for name in
                        ("read_ops", "write_ops", "other_ops", "read_bytes", "write_bytes", "other_bytes")]

        class Extended(ctypes.Structure):
            _fields_ = [("basic", Basic), ("io", Counters),
                        ("process_memory", ctypes.c_size_t), ("job_memory", ctypes.c_size_t),
                        ("peak_process", ctypes.c_size_t), ("peak_job", ctypes.c_size_t)]

        class ThreadEntry(ctypes.Structure):
            _fields_ = [("size", w.DWORD), ("usage", w.DWORD), ("tid", w.DWORD), ("pid", w.DWORD),
                        ("base_priority", w.LONG), ("delta_priority", w.LONG), ("flags", w.DWORD)]

        self.ThreadEntry = ThreadEntry
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        signatures = {
            "CreateJobObjectW": ([ctypes.c_void_p, w.LPCWSTR], w.HANDLE),
            "SetInformationJobObject": ([w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD], w.BOOL),
            "AssignProcessToJobObject": ([w.HANDLE, w.HANDLE], w.BOOL),
            "QueryInformationJobObject": ([w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD,
                                            ctypes.c_void_p], w.BOOL),
            "TerminateJobObject": ([w.HANDLE, w.UINT], w.BOOL),
            "OpenProcess": ([w.DWORD, w.BOOL, w.DWORD], w.HANDLE),
            "QueryFullProcessImageNameW": ([w.HANDLE, w.DWORD, w.LPWSTR, ctypes.c_void_p], w.BOOL),
            "CreateToolhelp32Snapshot": ([w.DWORD, w.DWORD], w.HANDLE),
            "Thread32First": ([w.HANDLE, ctypes.POINTER(ThreadEntry)], w.BOOL),
            "Thread32Next": ([w.HANDLE, ctypes.POINTER(ThreadEntry)], w.BOOL),
            "OpenThread": ([w.DWORD, w.BOOL, w.DWORD], w.HANDLE),
            "ResumeThread": ([w.HANDLE], w.DWORD),
            "CloseHandle": ([w.HANDLE], w.BOOL),
        }
        for name, (args, result) in signatures.items():
            function = getattr(self.api, name)
            function.argtypes, function.restype = args, result
        self.handle = self.api.CreateJobObjectW(None, None)
        if not self.handle:
            raise ctypes.WinError(ctypes.get_last_error())
        limits = Extended()
        limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not self.api.SetInformationJobObject(self.handle, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
            error = ctypes.WinError(ctypes.get_last_error())
            self.close()
            raise error

    def attach_and_resume(self, pid: int) -> None:
        process = self.api.OpenProcess(0x0101, False, pid)  # SET_QUOTA | TERMINATE
        if not process:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            if not self.api.AssignProcessToJobObject(self.handle, process):
                raise ctypes.WinError(ctypes.get_last_error())
        finally:
            self.api.CloseHandle(process)
        snapshot = self.api.CreateToolhelp32Snapshot(4, 0)  # TH32CS_SNAPTHREAD
        if snapshot == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        resumed = 0
        try:
            entry = self.ThreadEntry()
            entry.size = ctypes.sizeof(entry)
            found = self.api.Thread32First(snapshot, ctypes.byref(entry))
            while found:
                if entry.pid == pid:
                    thread = self.api.OpenThread(2, False, entry.tid)  # THREAD_SUSPEND_RESUME
                    if not thread:
                        raise ctypes.WinError(ctypes.get_last_error())
                    try:
                        if self.api.ResumeThread(thread) == 0xFFFFFFFF:
                            raise ctypes.WinError(ctypes.get_last_error())
                        resumed += 1
                    finally:
                        self.api.CloseHandle(thread)
                entry.size = ctypes.sizeof(entry)
                found = self.api.Thread32Next(snapshot, ctypes.byref(entry))
        finally:
            self.api.CloseHandle(snapshot)
        if not resumed:
            raise RuntimeError("No suspended verification thread could be resumed")

    def processes(self) -> list[dict]:
        capacity = 64
        while True:
            buffer = ctypes.create_string_buffer(8 + capacity * ctypes.sizeof(ctypes.c_size_t))
            if self.api.QueryInformationJobObject(self.handle, 3, buffer, len(buffer), None):
                break
            if ctypes.get_last_error() != 234 or capacity >= 8_192:  # ERROR_MORE_DATA
                raise ctypes.WinError(ctypes.get_last_error())
            capacity *= 2
        count = ctypes.c_uint32.from_buffer(buffer, 4).value
        pids = (ctypes.c_size_t * count).from_buffer(buffer, 8)
        result = []
        for pid in pids:
            image = ""
            process = self.api.OpenProcess(0x1000, False, pid)  # QUERY_LIMITED_INFORMATION
            if process:
                try:
                    value = ctypes.create_unicode_buffer(32_768)
                    size = ctypes.c_uint32(len(value))
                    if self.api.QueryFullProcessImageNameW(process, 0, value, ctypes.byref(size)):
                        image = value.value
                finally:
                    self.api.CloseHandle(process)
            result.append({"pid": pid, "image": image})
        return result

    def close(self) -> None:
        if self.handle:
            self.api.CloseHandle(self.handle)
            self.handle = None


class _PosixTree:
    def __init__(self, pid: int) -> None:
        self.pid = pid
        self.ps = shutil.which("ps")
        if not self.ps:
            raise RuntimeError("POSIX verification needs ps for private-session cleanup")

    def processes(self) -> list[dict]:
        listing = subprocess.run([self.ps, "-axo", "pid=,stat=,comm="], capture_output=True,
                                 text=True, check=True, timeout=5)
        result = []
        for line in listing.stdout.splitlines():
            fields = line.strip().split(None, 2)
            if len(fields) != 3 or fields[1].startswith("Z"):
                continue
            pid = int(fields[0])
            try:
                belongs = os.getsid(pid) == self.pid and os.getpgid(pid) == self.pid
            except (ProcessLookupError, PermissionError):
                continue
            if belongs:
                result.append({"pid": pid, "image": fields[2]})
        return result

    def close(self) -> None:
        # No name matching, killall, or signalling of the caller's session.
        # The private group persists if the launcher exits before its children.
        # Attempt the signal even if a diagnostic ps query previously failed.
        try:
            os.killpg(self.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        except PermissionError:
            # Darwin can retain a completed group after its last live member
            # exits. Do not hide a denied signal to a still-active group, or
            # a failed membership query: both must remain cleanup failures.
            if self.processes():
                raise


def _launch(command: list[str], root: Path, environment: dict[str, str]):
    kwargs = dict(cwd=root, env=environment, stdout=subprocess.PIPE,
                  stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, bufsize=0)
    if os.name == "nt":
        tree = _WindowsTree()
        process = None
        try:
            process = subprocess.Popen(command, creationflags=0x4 | subprocess.CREATE_NEW_PROCESS_GROUP,
                                       **kwargs)
            tree.attach_and_resume(process.pid)
            return process, tree
        except BaseException:
            tree.close()
            if process is not None:
                process.kill()
                process.wait(timeout=5)
                process.stdout.close()
            raise
    if not shutil.which("ps"):
        raise RuntimeError("POSIX verification needs ps for private-session cleanup")
    process = None
    try:
        process = subprocess.Popen(command, start_new_session=True, **kwargs)
        return process, _PosixTree(process.pid)
    except BaseException:
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                process.kill()
            process.wait(timeout=5)
            process.stdout.close()
        raise


class _Output:
    def __init__(self, process, destination: Path, console) -> None:
        self.process, self.destination, self.console = process, destination, console
        self.last_activity = time.monotonic()
        self.count = 0
        self.tail = b""
        self.error = None
        self.console_error = None
        self.thread = threading.Thread(target=self._read, name="verification-output", daemon=True)
        self.thread.start()

    def _read(self) -> None:
        try:
            with self.destination.open("wb") as log:
                while True:
                    data = os.read(self.process.stdout.fileno(), 8_192)
                    if not data:
                        break
                    self.last_activity = time.monotonic()
                    before = self.count
                    self.count += len(data)
                    self.tail = (self.tail + data)[-TAIL_BYTES:]
                    kept = data[:max(0, MAX_LOG_BYTES - before)]
                    if kept:
                        log.write(kept)
                        log.flush()
                    if self.console is not None and kept and self.console_error is None:
                        try:
                            # Direct fd writes avoid holding Python's buffered stdout lock
                            # if a downstream console stops draining the pipe.
                            if hasattr(self.console, "fileno"):
                                try:
                                    fd = self.console.fileno()
                                except (OSError, ValueError):
                                    fd = None
                            else:
                                fd = None
                            if fd is not None:
                                view = memoryview(kept)
                                while view:
                                    view = view[os.write(fd, view):]
                            else:
                                self.console.write(kept.decode("utf-8", "replace"))
                                self.console.flush()
                        except (OSError, ValueError) as error:
                            self.console_error = str(error)
        except (OSError, ValueError) as error:
            self.error = str(error)


def _copy_context(root: Path, fixture: str | None, destination: Path) -> list[dict]:
    if fixture is None:
        return []
    directory = _scratch_path(root, root / "build" / "verification" / fixture)
    result = []
    paths = list((directory / "config").glob("gregsteamexpansion-*.toml"))
    paths += [directory / "logs" / "latest.log", directory / "logs" / "debug.log"]
    for path in paths:
        if not path.is_file():
            continue
        try:
            _scratch_path(root, path)
            limit = 512 * 1024 if path.suffix == ".toml" else 16 * 1024 * 1024
            size = path.stat().st_size
            with path.open("rb") as stream:
                stream.seek(max(0, size - limit))
                data = stream.read(limit)
            target = destination / ("config" if path.suffix == ".toml" else "minecraft-logs") / path.name
            target.parent.mkdir(exist_ok=True)
            target.write_bytes(data)
            result.append({"path": path.relative_to(root).as_posix(), "size": size,
                           "truncated": size > limit, "sha256": hashlib.sha256(data).hexdigest()})
        except (OSError, ValueError) as error:
            result.append({"path": str(path), "error": str(error)})
    return result


def _diagnose(root: Path, fixture: str | None, folder: Path, tree, environment, limits) -> dict:
    result = {"jvms": []}
    try:
        result["context"] = _copy_context(root, fixture, folder)
    except (OSError, ValueError) as error:
        result["context_error"] = str(error)
    try:
        processes = tree.processes()
        result["processes"] = processes
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        result["process_query_error"] = str(error)
        processes = []
    executable = "jcmd.exe" if os.name == "nt" else "jcmd"
    java_home = environment.get("JAVA_HOME")
    jcmd = str(Path(java_home) / "bin" / executable) if java_home else shutil.which(executable)
    if not jcmd or not Path(jcmd).is_file():
        result["jcmd_unavailable"] = True
        return result
    diagnostic_deadline = time.monotonic() + 20
    for process in reversed(processes):
        if Path(process["image"]).name.lower() not in {"java", "java.exe", "javaw.exe"}:
            continue
        remaining = diagnostic_deadline - time.monotonic()
        if remaining <= 0:
            result["jcmd_budget_exhausted"] = True
            break
        path = folder / f"jvm-{process['pid']}-threads.txt"
        entry = {"pid": process["pid"], "file": path.name}
        try:
            with path.open("wb") as output:
                completed = subprocess.run([jcmd, str(process["pid"]), "Thread.print", "-l"],
                                           stdout=output, stderr=subprocess.STDOUT,
                                           timeout=min(limits.diagnostic_timeout, remaining), env=environment)
            entry["returncode"] = completed.returncode
        except (OSError, subprocess.TimeoutExpired) as error:
            entry["error"] = str(error)
        result["jvms"].append(entry)
    return result


def run_command(root: Path, stage: str, command: list[str], *, environment: dict[str, str] | None = None,
                fixture: str | None = None, limits: Limits | None = None,
                console=sys.stdout):
    environment = dict(os.environ if environment is None else environment)
    limits = limits or Limits.from_environment(environment)
    if fixture is not None and fixture not in {"gametest", "server-restart", "config-restart",
                                               "dependency-core", "dependency-ae2", "dependency-full"}:
        raise ValueError(f"Unknown guarded verification fixture: {fixture}")
    _install_windows_interrupt()
    root = root.resolve()
    if fixture is not None:
        _scratch_path(root, root / "build" / "verification" / fixture)
    slug = re.sub(r"[^a-z0-9]+", "-", stage.lower()).strip("-")[:80] or "stage"
    folder = _scratch_path(root, root / "build" / "verification" / "diagnostics" /
                           f"{datetime.now(timezone.utc):%Y%m%dT%H%M%SZ}-{slug}-{uuid4().hex[:8]}")
    folder.mkdir(parents=True)
    metadata = {"schema": 1, "stage": stage, "fixture": fixture, "command": command,
                "started_utc": _utc(), "status": "running",
                "limits_seconds": {"timeout": limits.timeout, "idle": limits.idle},
                "configuration": {key: value for key, value in environment.items()
                                  if key.startswith("GSE_EXPECTED_") or key == "GSE_RESTART_TEST_PHASE"}}
    metadata_path = folder / "metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    process = tree = output = None
    started = time.monotonic()
    reason = None
    failure = None
    try:
        process, tree = _launch(command, root, environment)
        metadata["pid"] = process.pid
        output = _Output(process, folder / "stage.log", console)
        while process.poll() is None:
            now = time.monotonic()
            if output.error:
                reason = "output_read_error"
            elif now - started >= limits.timeout:
                reason = "stage_timeout"
            elif now - output.last_activity >= limits.idle:
                reason = "output_stalled"
            if reason:
                break
            time.sleep(limits.poll)
        if reason is None and process.returncode == 0:
            # --no-daemon may finish its client just before the single-use JVM
            # flushes/closes its caches. Allow normal shutdown before cleanup.
            deadline = time.monotonic() + limits.exit_grace
            while tree.processes():
                if time.monotonic() >= deadline:
                    reason = "orphaned_children"
                    break
                time.sleep(limits.poll)
        if reason or process.returncode:
            metadata["diagnostics"] = _diagnose(root, fixture, folder, tree, environment, limits)
        if reason:
            failure = WatchdogError(f"{stage}: {reason}; diagnostics: {folder}")
        elif process.returncode:
            reason = "nonzero_exit"
            failure = subprocess.CalledProcessError(process.returncode, command)
    except BaseException as error:
        failure = error
        reason = "interrupted" if isinstance(error, KeyboardInterrupt) else "supervisor_error"
        if tree is not None and "diagnostics" not in metadata:
            metadata["diagnostics"] = _diagnose(root, fixture, folder, tree, environment, limits)
    finally:
        if tree is not None:
            try:
                tree.close()
            except (OSError, subprocess.SubprocessError, ValueError) as error:
                metadata["cleanup_error"] = str(error)
                if "diagnostics" not in metadata:
                    metadata["diagnostics"] = _diagnose(root, fixture, folder, tree, environment, limits)
                failure = failure or WatchdogError(f"{stage}: process cleanup failed; diagnostics: {folder}")
                reason = reason or "cleanup_error"
        if process is not None:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            metadata["returncode"] = process.returncode
        if output is not None:
            output.thread.join(timeout=5)
            (folder / "last-output.log").write_bytes(output.tail)
            metadata.update(output_bytes=output.count, log_truncated=output.count > MAX_LOG_BYTES,
                            idle_seconds=round(time.monotonic() - output.last_activity, 3),
                            output_read_error=output.error, console_error=output.console_error)
            if output.error or output.thread.is_alive():
                failure = failure or WatchdogError(f"{stage}: output collection failed; diagnostics: {folder}")
                reason = reason or "output_collection_error"
            if not output.thread.is_alive():
                process.stdout.close()
        metadata.update(status="failed" if failure else "passed", reason=reason,
                        finished_utc=_utc(), elapsed_seconds=round(time.monotonic() - started, 3))
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    if failure is not None:
        if console is not None:
            print(f"Verification stage failed: {stage}; diagnostics: {folder}", file=console, flush=True)
        raise failure
    return subprocess.CompletedProcess(command, 0)
