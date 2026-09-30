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
`check`. It covers selected structure dimensions and parallel caps, keeps the
current GameTest totals in the root README and design status pages aligned with
the annotated Java methods, and audits acquisition recipes. The recipe audit
rejects electrical or post-steel ingredients in early hand-crafting routes,
pins the large steam supply hatch to its HV upgrade recipe, and checks the
MV-UV electric ore crusher material progression. It also pairs every generated
assembler acquisition recipe with its hand-crafting route and checks the output,
difficulty condition, material relationship, fluid conversion, programming
circuit, duration, and EU/t. Intentional changes must update the related
documentation, implementation, and pinned assertions.

## Steam economy reports

Export the default Easy/Normal/Expert economy and disabled-difficulty baseline
with Python's standard library:

```bash
python tools/export_steam_economy.py
python tools/export_steam_economy.py --check
```

Committed Markdown and exact rational JSON are in `docs/balance/`. They cover
conditional acquisition recipes, assembler parallel economics, circuit
specialization, fluid drilling, blast-furnace reset cycles, boiler output and
furnace startup investment. These are ideal batch calculations, not measured
server throughput or a complete structure bill of materials. Formula guards
fail on changed implementation shapes; review the model before regenerating.
Source fingerprints make relevant source edits visible in the freshness check.

Gradle `checkSteamEconomy` runs `--check` as part of `check` and the existing
build/CI gate. It uses the same `assetPythonExecutable`/`GSE_ASSET_PYTHON`
interpreter selection as other Python verification tools.

## Server restart persistence

Run machine and terminal persistence checks across two independent GameTest server JVMs
with:

```bash
python tools/verify_server_restart.py
```

The first process writes controller progress and pending item/fluid outputs to an
isolated End chunk, along with blast-furnace module ownership, stored heat and
high-charge cycle state. The second process loads the same world, verifies the
saved state, and removes the fixtures. Two survival terminal jobs also persist
combined-module selections, cursor, escrow, exact rollback states, strict air and
pending refunds. One resumes to completion; the other encounters an air obstruction
after restart and must refund its materials exactly once, retaining overflow until
collected. Ordinary runs exercise actual terminal chunk unloading and resumption.
The runtime directory is
`build/verification/server-restart/`; developer saves and configs in `run/` are
not touched. All regular GameTests run in both processes. Use
`--offline` when the Gradle dependencies are already cached.

## Configuration restart

Verify startup-only configuration with three independent GameTest server JVMs:

```bash
python tools/verify_config_restart.py
```

The script runs Easy, Normal and Expert profiles and checks captured difficulty,
custom profile values, all mapped GTCEu recipe switches, casing output, external
module enablement, both flagship-machine toggles, and both custom weight tables.
It backs up `build/verification/config-restart/config/gregsteamexpansion-common.toml`
and restores its exact bytes on success or failure. Use `--offline` when the
Gradle dependencies are cached.

Fixtures retain Forge's generated comments and replace only values, avoiding a
startup correction/file-watcher race. The template comes from the isolated main
GameTest config. If that template does not exist, a standalone invocation first
boots a default GameTest server to generate it in the restart fixture directory.

## Optional dependency runtimes

```bash
python tools/verify_dependency_runtime.py
python tools/verify_dependency_runtime.py --offline --profile core
```

The profiles are `core` (required mods only), `ae2` (AE2 and GuideME), and `full`
(AE2, GuideME and GTM Things). Each starts a real dedicated GameTest server in
`build/verification/dependency-<profile>/` and runs only the dependency smoke
namespace. The test verifies the actual loaded mod set, core machine formation
and acquisition, terminal conditional recipes and missing-dependency behaviour.
Jade-linked regression classes are registered only in the ordinary full suite,
because Forge reflects GameTest holders before filtering namespaces.

Ordinary development launches keep all fixture dependencies and their startup
protection. The smoke exception requires a dedicated GameTest server and its
explicit isolated directory. GTM Things without AE2 is not a valid combination
because GTM Things itself requires AE2.

The default `runGameTestServer` task uses `build/verification/gametest/` and runs
all 187 tests. `-PgseVerificationRun=server-restart` and
`-PgseVerificationRun=config-restart` select the two persistence fixtures;
`-PgseDependencySmoke=core|ae2|full` selects only the smoke namespace and its
isolated dependency set.

## Complete release gate

`bash tools/verify.sh` mirrors CI: compilation, the complete GameTest suite,
server restart, configuration restart, optional dependency runtimes, datagen
freshness, and build checks. On Windows use Git Bash with JDK 17 and Python on
PATH; `GSE_ASSET_PYTHON` selects a specific Python executable. `--no-datagen`
is available for local iteration and does not replace the complete release gate.
Pass `--offline` to use cached dependencies for every Gradle process, including
the restart and dependency scripts.
