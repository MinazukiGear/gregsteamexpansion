# Repository tools

## Asset generation

All committed asset generators use Python 3.10 or newer. Install the pinned
image dependency once:

```bash
python -m pip install -r tools/requirements.txt
```

Run every generator from the repository root with:

```bash
python tools/generate_assets.py
```

Verify all committed outputs without changing the working tree with:

```bash
python tools/generate_assets.py --check
```

Gradle exposes the same operations as `genAssets` and `checkGeneratedAssets`.
Both use `python` by default; pass `-PassetPythonExecutable=/path/to/python` or
set `GSE_ASSET_PYTHON` when the interpreter has another name.

The runner executes all `tools/gen_*.py` files in filename order. This order is
part of the output contract: `gen_crafting_station_textures.py` supplies the
station side and GUI, then `gen_structure_textures.py` deliberately publishes
the authoritative station top and bottom. Individual generators may still be
run directly while editing one texture.

Generated PNG and GameTest structure files are committed. Gradle `check`,
`tools/verify.sh`, and CI run the isolated freshness check and reject missing or
stale outputs. PNG files are compared by decoded mode, dimensions and pixels so
equivalent zlib streams produced on Windows and Linux do not fail the gate;
non-image outputs such as GameTest NBT remain byte-exact.

## Design contract verification

Run the representative design-to-code numeric checks directly with:

```bash
python tools/check_design_contracts.py
```

The Gradle task `checkDesignContracts` runs the same check and is part of
`check`. It covers selected structure dimensions and parallel caps; intentional
changes must update the design document, implementation, and pinned assertion.

## Server restart persistence

Run the processor persistence check across two independent GameTest server JVMs
with:

```bash
python tools/verify_server_restart.py
```

The first process writes controller progress and pending item/fluid outputs to an
isolated End chunk. The second process loads the same world, verifies the saved
state, and removes the fixture. All regular GameTests run in both processes. Use
`--offline` when the Gradle dependencies are already cached.

## Configuration restart

Verify startup-only configuration with two independent GameTest server JVMs:

```bash
python tools/verify_config_restart.py
```

The script runs an Easy profile followed by an Expert profile and checks the
captured difficulty, all mapped GTCEu recipe switches, casing output, both
flagship-machine toggles, and both custom weight tables. It backs up the local
`run/config/gregsteamexpansion-common.toml` file and restores its exact bytes on
success or failure. Use `--offline` when the Gradle dependencies are cached.
