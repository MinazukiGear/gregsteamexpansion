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
stale outputs.

## Design contract verification

Run the representative design-to-code numeric checks directly with:

```bash
python tools/check_design_contracts.py
```

The Gradle task `checkDesignContracts` runs the same check and is part of
`check`. It covers selected structure dimensions and parallel caps; intentional
changes must update the design document, implementation, and pinned assertion.
