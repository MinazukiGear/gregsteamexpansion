#!/usr/bin/env python3
"""Audit local resources and an optional release JAR; this is not a platform runtime test."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import zipfile


ROOT = Path(__file__).resolve().parent.parent
MOD_ID = "gregsteamexpansion"
RESOURCE_ROOTS = (Path("src/main/resources"), Path("src/generated/resources"))
NATIVE_SUFFIXES = (".dll", ".so", ".dylib", ".jnilib", ".exe")


def unique_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(content: bytes) -> object:
    return json.loads(content.decode("utf-8"), object_pairs_hook=unique_keys)


def check_names(names: list[str], errors: list[str]) -> None:
    folded: dict[str, str] = {}
    for name in names:
        if name.casefold() in folded:
            errors.append(f"resource path collision: {folded[name.casefold()]} / {name}")
        else:
            folded[name.casefold()] = name
        path = PurePosixPath(name)
        if "\\" in name or path.is_absolute() or ".." in path.parts:
            errors.append(f"non-portable resource path: {name}")
        if name.startswith(("assets/", "data/")) and not re.fullmatch(r"[a-z0-9_./-]+", name):
            errors.append(f"resource path must use lowercase ASCII: {name}")


def check_model_references(name: str, value: object, names: set[str], errors: list[str]) -> None:
    if isinstance(value, dict):
        for key, entry in value.items():
            if key in ("model", "parent") and isinstance(entry, str) and entry.startswith(f"{MOD_ID}:"):
                target = f"assets/{MOD_ID}/models/{entry.split(':', 1)[1]}.json"
                if target not in names:
                    errors.append(f"{name}: missing or wrong-case model {target}")
            elif key == "textures" and isinstance(entry, dict):
                for texture in entry.values():
                    if isinstance(texture, str) and texture.startswith(f"{MOD_ID}:"):
                        target = f"assets/{MOD_ID}/textures/{texture.split(':', 1)[1]}.png"
                        if target not in names:
                            errors.append(f"{name}: missing or wrong-case texture {target}")
            check_model_references(name, entry, names, errors)
    elif isinstance(value, list):
        for entry in value:
            check_model_references(name, entry, names, errors)


def check_resources(resources: dict[str, bytes], errors: list[str]) -> int:
    check_names(list(resources), errors)
    json_count = 0
    names = set(resources)
    for name, content in resources.items():
        if name.endswith((".json", ".mcmeta")):
            try:
                data = load_json(content)
                json_count += 1
                if "/models/" in name or "/blockstates/" in name:
                    check_model_references(name, data, names, errors)
            except (UnicodeError, ValueError) as error:
                errors.append(f"{name}: invalid UTF-8/JSON: {error}")
    return json_count


def source_resources(root: Path, errors: list[str]) -> dict[str, bytes]:
    resources: dict[str, bytes] = {}
    for relative_root in RESOURCE_ROOTS:
        for path in sorted((root / relative_root).rglob("*")):
            if not path.is_file():
                continue
            name = path.relative_to(root / relative_root).as_posix()
            if name.startswith(".cache/"):
                continue
            if name in resources:
                errors.append(f"duplicate main/generated resource: {name}")
            resources[name] = path.read_bytes()
    return resources


def check_sources(root: Path, errors: list[str]) -> dict[str, bytes]:
    resources = source_resources(root, errors)
    json_count = check_resources(resources, errors)
    for path in (root / "src/main/java").rglob("*.java"):
        text = path.read_text("utf-8")
        if re.search(r"\.to(?:Lower|Upper)Case\(\s*\)", text):
            errors.append(f"{path.relative_to(root)}: case conversion needs an explicit locale")
    for relative in ("gradlew", "tools/verify.sh"):
        if b"\r" in (root / relative).read_bytes():
            errors.append(f"{relative}: POSIX shell script must have LF line endings")
    print(f"Source audit: {len(resources)} resources, {json_count} UTF-8 JSON/metadata files; "
          "case-sensitive model/texture references and explicit Java locales checked.")
    return resources


def check_jar(path: Path, sources: dict[str, bytes], errors: list[str]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        check_names(names, errors)
        packaged = {name: archive.read(name) for name in names}
        check_resources({name: content for name, content in packaged.items()
                         if not name.endswith(".class")}, errors)
        class_count = 0
        for name, content in packaged.items():
            if name.startswith('.cache/'):
                errors.append(f"datagen cache must not be packaged: {name}")
            if name.lower().endswith(NATIVE_SUFFIXES) or name.lower().endswith(".jar"):
                errors.append(f"unexpected native or bundled dependency in addon JAR: {name}")
            if name.endswith(".class"):
                class_count += 1
                if len(content) < 8 or content[:4] != b"\xca\xfe\xba\xbe":
                    errors.append(f"invalid class file: {name}")
                elif struct.unpack(">H", content[6:8])[0] > 61:
                    errors.append(f"class requires Java newer than 17: {name}")
        if not class_count:
            errors.append("JAR contains no compiled classes")
        for name in ("META-INF/mods.toml", "pack.mcmeta", f"{MOD_ID}.mixins.json", f"{MOD_ID}.refmap.json"):
            if name not in packaged:
                errors.append(f"missing runtime metadata: {name}")
        for name in ("META-INF/mods.toml", "pack.mcmeta"):
            if b"${" in packaged.get(name, b""):
                errors.append(f"unexpanded runtime metadata: {name}")
        for name, content in sources.items():
            if name not in packaged:
                errors.append(f"source resource omitted from JAR: {name}")
            elif name not in ("META-INF/mods.toml", "pack.mcmeta") and packaged[name] != content:
                errors.append(f"packaged resource differs from source: {name}")
        print(f"JAR inspected: {class_count} classes and {len(sources)} source resources.")
    print(f"SHA-256: {hashlib.sha256(path.read_bytes()).hexdigest()}  {path.resolve()}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="also inspect a built/reobfuscated release JAR")
    args = parser.parse_args()
    errors: list[str] = []
    try:
        sources = check_sources(ROOT, errors)
        if args.jar:
            check_jar(args.jar, sources, errors)
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(str(error))
    if errors:
        print("Portability audit failed:\n - " + "\n - ".join(errors), file=sys.stderr)
        return 1
    print("Static portability audit passed; OS/architecture and game runtime remain separate checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
