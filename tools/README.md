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
Gradle probes Python 3.10+ at task execution time: `python3`, then `python` on
Unix; `python`, `py`, then `python3` on Windows. Pass
`-PassetPythonExecutable=/path/to/python` or set `GSE_ASSET_PYTHON` for an explicit
interpreter. An invalid override fails without silently selecting another one.

The runner executes all `tools/gen_*.py` files in filename order. This order is
part of the output contract: `gen_crafting_station_textures.py` supplies the
station side and GUI, then `gen_structure_textures.py` deliberately publishes
the authoritative station top and bottom. Individual generators may still be
run directly while editing one texture.

Generated PNG and GameTest structure files are committed. Gradle `check`,
`tools/verify.sh`, and CI run the isolated freshness check and reject missing or
stale outputs. PNG files are compared by decoded mode, dimensions and pixels so
equivalent zlib streams produced on Windows and Linux do not fail the gate.
Animation `.mcmeta` compares UTF-8 text with normalized line endings so Git's
CRLF-to-LF checkout does not cause false stale reports; animation parameters
must still match. Other outputs, including GameTest NBT, remain byte-exact.

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

Acquisition materials now include all four modes. The disabled profile assumes
GTCEu 7.5.3's default `recipes.casingsPerCraft: 2`; it does not use an enabled
Normal tier to override a server's actual upstream setting.

Gradle `checkSteamEconomy` runs `--check` as part of `check` and the existing
build/CI gate. It uses the same `assetPythonExecutable`/`GSE_ASSET_PYTHON`
interpreter selection as other Python verification tools.

## Isolated client acceptance

Launch a new acceptance client with the full ordinary development mod set, JDK 17
and a separate game directory:

```powershell
.\gradlew.bat -PgseAcceptanceClient=true runClient
```

On Linux/macOS use `./gradlew` with the same arguments. Saves, options, configs
and logs go to `build/verification/client-acceptance/`; ordinary `runClient`
still uses `run/`. This launch has a visible game window. It does not select,
copy or upgrade an existing player world. Start a new survival world for the
default disabled-difficulty route, or copy a backed-up old world into this
directory separately when testing upgrades. Check the actual configuration
before reusing an acceptance directory from an earlier run.

The acquisition GameTest also runs one complete upstream primitive blast-furnace
batch (iron + two charcoal → one steel) without shortening its recipe. Runtime
logs tagged `[Progression]` record the loaded coke/creosote fuel quantities and
the bootstrap result. Fixture-supplied materials and structures do not measure
survival procurement time or replace client/multiplayer acceptance.

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
all 192 tests. `-PgseVerificationRun=server-restart` and
`-PgseVerificationRun=config-restart` select the two persistence fixtures;
`-PgseDependencySmoke=core|ae2|full` selects only the smoke namespace and its
isolated dependency set.

## Terminal preview benchmark

```bash
python tools/benchmark_terminal_preview.py --offline --label current
```

Use JDK 17. This opt-in command starts only the `gregsteamexpansion_preview_benchmark`
namespace in `build/verification/terminal-preview/`, preserving previous scratch
worlds under `world-history`. Timestamped `reports/` directories retain raw server
logs, source hashes and 20 measurements: two rounds of 1/2/8 simulated survival
owners and 1/4/16 compact steam-crusher targets, plus a large 15x15 furnace
single-target case per round. Each case has one warmup batch and five timed batches,
yielding between batches. AE is disabled; owners are called sequentially on the
server thread. Median and maximum batch times describe preview work only, not real
multiplayer MSPT, networking, heap allocation or client FPS. See
[the performance record](../docs/terminal-preview-performance-2026-10-10.md).

The benchmark namespace is excluded from the 192 ordinary regression tests,
configuration restart and dependency-smoke runs. Timings have no speed threshold.

## Complete release gate

`python tools/verify.py` is the shared local/CI gate: portability-tool negative
fixtures, compilation, the complete GameTest suite, server restart, configuration
restart, optional dependency runtimes, datagen freshness, and build checks.
Before each fixture sequence, its previous scratch `world/` is archived under
`build/verification/world-history/` to avoid stale blocks/claims when the test
layout changes. Configs are preserved; seed and verify share the same new world.
It verifies JDK 17 before starting Gradle and uses the invoking Python executable
throughout, including paths containing spaces. Windows does not need Bash.
On Unix the tools invoke `sh gradlew` so ZIP exports or checkouts without the
executable bit work too. No commands are joined into a shell string.

`bash tools/verify.sh` remains a compatibility entry point, and
`GSE_ASSET_PYTHON` selects its interpreter. `--no-datagen` is available for local
iteration and does not replace the complete release gate. Pass `--offline` to
use cached dependencies for every Gradle process, including restart and
dependency scripts. Datagen freshness compares resource hashes before and after
generation, excluding `.cache` bookkeeping. Existing uncommitted generated-resource
edits are preserved and must be reviewed separately.

## Portability audits

```bash
python tools/check_portability.py
python tools/check_portability.py --jar build/libs/gregsteamexpansion-0.1.0-beta.2.jar
python tools/test_portability.py
```

Gradle `checkPortableResources` and `checkPortableJar` run as part of `check`.
The latter waits for production reobfuscation. Audits cover exact resource case,
duplicate main/generated paths, strict UTF-8 JSON without duplicate keys,
internal model/texture references, explicit Java locales, LF shell scripts,
Java 17 class versions, required metadata/refmap and packaged-resource fidelity.
Native binaries, nested dependency JARs and datagen `.cache` files are rejected
in the addon JAR. These checks describe the addon only; they do not verify the
host's upstream libraries, graphics drivers or game runtime.

The nine standard-library Python tests inject wrong-case references, collisions,
invalid encoding/JSON, newer bytecode, natives and missing resources, and inspect
wrapper arguments for Windows/POSIX and Unicode/space paths. They also check
datagen freshness against existing edits and changed, added or deleted resources.
The animation metadata fixture accepts LF/CRLF checkout differences while
rejecting changed frame timing and binary NBT differences.
The fixture archive test verifies world evidence and configs are preserved and
unrecognized fixture paths are rejected. Mocked platform
selection is a tooling regression check, not a test performed on that platform.
See [platform compatibility](../docs/platform-compatibility.md) for manual
validation and the optional NixOS environment.
