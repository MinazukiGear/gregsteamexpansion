"""Negative fixtures for portability gates, run with Python's standard-library unittest."""

import contextlib
import io
from pathlib import Path, PurePosixPath
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import check_portability as audit
import generate_assets as assets
import verification_common as invocation
import verify as gate


class PortabilityTests(unittest.TestCase):
    def test_animation_newlines_match_but_content_and_binary_changes_do_not(self):
        with tempfile.TemporaryDirectory(prefix="gse-animation-") as directory:
            root = Path(directory)
            generated = root / "generated.mcmeta"
            committed = root / "committed.mcmeta"
            metadata = b'{\n  "animation": {"frametime": 6}\n}\n'
            generated.write_bytes(metadata.replace(b"\n", b"\r\n"))
            committed.write_bytes(metadata)
            self.assertTrue(assets.generated_asset_matches(generated, committed))
            committed.write_bytes(metadata.replace(b"6", b"7"))
            self.assertFalse(assets.generated_asset_matches(generated, committed))
            generated = root / "generated.nbt"
            committed = root / "committed.nbt"
            generated.write_bytes(b"binary\r\n")
            committed.write_bytes(b"binary\n")
            self.assertFalse(assets.generated_asset_matches(generated, committed))

    def test_fresh_fixture_archives_only_its_world_and_keeps_configs(self):
        with tempfile.TemporaryDirectory(prefix="gse-world-") as directory, \
                patch.object(invocation, "ROOT", Path(directory)), \
                contextlib.redirect_stdout(io.StringIO()):
            fixture = Path(directory) / "build" / "verification" / "gametest"
            world = fixture / "world"
            world.mkdir(parents=True)
            (world / "state.dat").write_bytes(b"previous failure evidence")
            config = fixture / "config.toml"
            config.write_bytes(b"preserved config")
            backup = invocation.archive_fixture_world("gametest")
            self.assertFalse(world.exists())
            self.assertEqual(b"previous failure evidence", (backup / "state.dat").read_bytes())
            self.assertEqual(b"preserved config", config.read_bytes())
            self.assertIsNone(invocation.archive_fixture_world("gametest"))
            with self.assertRaises(ValueError):
                invocation.archive_fixture_world("../../run")

    def test_datagen_freshness_preserves_existing_edits_but_rejects_new_changes(self):
        with tempfile.TemporaryDirectory(prefix="gse-datagen-") as directory:
            root = Path(directory)
            resource = root / "edited.json"
            resource.write_text('{"already_edited":true}', encoding="utf-8")
            before = gate.generated_state(root)
            gate.verify_generated_unchanged(before, root)
            cache = root / ".cache"
            cache.mkdir()
            (cache / "host-paths").write_text("local bookkeeping", encoding="utf-8")
            gate.verify_generated_unchanged(before, root)
            resource.write_text('{"datagen_changed":true}', encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "edited.json"):
                gate.verify_generated_unchanged(before, root)
            resource.unlink()
            with self.assertRaisesRegex(RuntimeError, "edited.json"):
                gate.verify_generated_unchanged(before, root)
            resource.write_text('{"already_edited":true}', encoding="utf-8")
            (root / "new.json").write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "new.json"):
                gate.verify_generated_unchanged(before, root)

    def test_case_collision_and_traversal_are_rejected(self):
        errors = []
        audit.check_names(["assets/gse/A.json", "assets/gse/a.json", "../outside"], errors)
        self.assertTrue(any("collision" in error for error in errors))
        self.assertTrue(any("lowercase" in error for error in errors))
        self.assertTrue(any("non-portable" in error for error in errors))

    def test_json_duplicates_and_non_utf8_are_rejected(self):
        errors = []
        audit.check_resources({"a.json": b'{"x":1,"x":2}', "b.json": b'"\xff"'}, errors)
        self.assertEqual(2, len(errors))

    def test_exact_case_model_and_texture_resolution(self):
        resources = {
            "assets/gregsteamexpansion/models/item/test.json":
                b'{"parent":"gregsteamexpansion:block/case",'
                b'"textures":{"all":"gregsteamexpansion:block/missing"}}',
            "assets/gregsteamexpansion/models/block/Case.json": b'{}',
        }
        errors = []
        audit.check_resources(resources, errors)
        self.assertTrue(any("wrong-case model" in error for error in errors))
        self.assertTrue(any("wrong-case texture" in error for error in errors))

    def test_jar_rejects_new_bytecode_native_and_omitted_resources(self):
        with tempfile.TemporaryDirectory(prefix="gse-portability-") as directory:
            jar = Path(directory) / "fixture.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("Example.class", b"\xca\xfe\xba\xbe\x00\x00\x00\x41")
                archive.writestr("wrong-platform.dll", b"native")
                archive.writestr(".cache/checkout-paths", b"local paths")
            errors = []
            with contextlib.redirect_stdout(io.StringIO()):
                audit.check_jar(jar, {"assets/gse/missing.json": b"{}"}, errors)
            self.assertTrue(any("newer than 17" in error for error in errors))
            self.assertTrue(any("unexpected native" in error for error in errors))
            self.assertTrue(any("omitted from JAR" in error for error in errors))
            self.assertTrue(any("missing runtime metadata" in error for error in errors))
            self.assertTrue(any("datagen cache" in error for error in errors))

    def test_posix_wrapper_needs_no_executable_bit_and_keeps_paths_intact(self):
        with patch.object(invocation.os, "name", "posix"), \
                patch.object(invocation.shutil, "which", return_value="/bin/sh"), \
                patch.object(invocation, "ROOT", PurePosixPath("space and 中文")), \
                patch.object(invocation.sys, "executable", "python path/中文/python3"):
            command = invocation.gradle_command("build", offline=True)
        self.assertEqual(["/bin/sh", "space and 中文/gradlew"], command[:2])
        self.assertIn("-PassetPythonExecutable=python path/中文/python3", command)
        self.assertIn("--offline", command)

    def test_windows_wrapper_keeps_paths_intact(self):
        with patch.object(invocation.os, "name", "nt"):
            command = invocation.gradle_command("runGameTestServer")
        self.assertTrue(command[0].endswith("gradlew.bat"))
        self.assertIn("runGameTestServer", command)


if __name__ == "__main__":
    unittest.main()
