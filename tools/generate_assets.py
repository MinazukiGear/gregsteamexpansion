#!/usr/bin/env python3
"""Generate committed assets or verify that they are up to date."""

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
RESOURCE_ROOT = Path("src/main/resources")


def verify_environment() -> None:
    if sys.version_info < (3, 10):
        raise SystemExit("asset generation requires Python 3.10 or newer")

    requirement = next(
        (line.strip() for line in (TOOLS / "requirements.txt").read_text("utf-8").splitlines()
         if line.strip().lower().startswith("pillow==")),
        None,
    )
    if requirement is None:
        raise SystemExit("tools/requirements.txt must pin Pillow with Pillow==<version>")
    required_version = requirement.split("==", 1)[1]
    try:
        import PIL
    except ModuleNotFoundError as error:
        raise SystemExit(
            "Pillow is not installed; run: python -m pip install -r tools/requirements.txt"
        ) from error
    if PIL.__version__ != required_version:
        raise SystemExit(
            f"Pillow {required_version} is required, found {PIL.__version__}; "
            "run: python -m pip install -r tools/requirements.txt"
        )


def find_generators(tools: Path) -> list[Path]:
    generators = sorted(tools.glob("gen_*.py"))
    if not generators:
        raise SystemExit("no gen_*.py asset generators found")
    return generators


def run_generators(tools: Path, root: Path) -> int:
    generators = find_generators(tools)
    for generator in generators:
        print(f"\n=== {generator.name} ===", flush=True)
        subprocess.run([sys.executable, str(generator)], cwd=root, check=True)
    return len(generators)


def check_generated_assets() -> int:
    with tempfile.TemporaryDirectory(prefix="gse-assets-") as temporary_directory:
        sandbox = Path(temporary_directory)
        sandbox_tools = sandbox / "tools"
        sandbox_tools.mkdir()
        for generator in find_generators(TOOLS):
            shutil.copy2(generator, sandbox_tools / generator.name)

        generator_count = run_generators(sandbox_tools, sandbox)
        generated_root = sandbox / RESOURCE_ROOT
        generated_files = sorted(path for path in generated_root.rglob("*") if path.is_file())
        if not generated_files:
            raise SystemExit("asset generators produced no files")

        missing: list[Path] = []
        stale: list[Path] = []
        committed_root = ROOT / RESOURCE_ROOT
        for generated in generated_files:
            relative = generated.relative_to(generated_root)
            committed = committed_root / relative
            if not committed.is_file():
                missing.append(relative)
            elif generated.read_bytes() != committed.read_bytes():
                stale.append(relative)

        if missing or stale:
            print("Generated asset freshness check failed:", file=sys.stderr)
            for relative in missing:
                print(f"  missing: {RESOURCE_ROOT / relative}", file=sys.stderr)
            for relative in stale:
                print(f"  stale:   {RESOURCE_ROOT / relative}", file=sys.stderr)
            print("Run: python tools/generate_assets.py", file=sys.stderr)
            return 1

        print(
            f"\nGenerated assets are current: {len(generated_files)} files "
            f"from {generator_count} scripts."
        )
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="generate in a temporary directory and compare with committed outputs",
    )
    arguments = parser.parse_args()
    verify_environment()
    if arguments.check:
        return check_generated_assets()

    generator_count = run_generators(TOOLS, ROOT)
    print(f"\nGenerated assets with {generator_count} scripts.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
