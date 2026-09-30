#!/usr/bin/env bash
# Local mirror of the CI gate (.github/workflows/build.yml).
#
# Runs the same checks in the same order; remote CI confirms its own runner
# environment and dependency downloads. `runGameTestServer` boots
# a real dedicated server and takes minutes; the datagen step rewrites
# src/generated/resources in place and fails if that produces a diff.
#
# Usage: tools/verify.sh [--offline] [--no-datagen]

set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_ARGS=(--no-daemon)
RESTART_ARGS=()
RUN_DATAGEN=1
for arg in "$@"; do
    case "$arg" in
        --offline) GRADLE_ARGS+=(--offline); RESTART_ARGS=(--offline) ;;
        --no-datagen) RUN_DATAGEN=0 ;;
        -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

if [ -n "${GSE_ASSET_PYTHON:-}" ]; then
    ASSET_PYTHON="$GSE_ASSET_PYTHON"
elif command -v python3 >/dev/null 2>&1; then
    ASSET_PYTHON=python3
elif command -v python >/dev/null 2>&1; then
    ASSET_PYTHON=python
elif command -v py >/dev/null 2>&1; then
    ASSET_PYTHON=py
else
    echo "Python 3.10+ is required; see tools/README.md." >&2
    exit 1
fi
if ! "$ASSET_PYTHON" -c 'import PIL, sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' >/dev/null 2>&1; then
    echo "Python 3.10+ and the pinned Pillow dependency are required; see tools/README.md." >&2
    exit 1
fi
GRADLE_ARGS+=("-PassetPythonExecutable=$ASSET_PYTHON")

# Windows checkouts use gradlew.bat; everything else uses gradlew.
if [[ "${OS:-}" == "Windows_NT" ]]; then
    GRADLE=./gradlew.bat
else
    GRADLE=./gradlew
fi

step() { printf '\n=== %s ===\n' "$1"; }

step "compile"
"$GRADLE" "${GRADLE_ARGS[@]}" compileJava

step "gametest"
"$GRADLE" "${GRADLE_ARGS[@]}" -PgseVerificationRun=gametest runGameTestServer

step "server restart persistence"
"$ASSET_PYTHON" tools/verify_server_restart.py "${RESTART_ARGS[@]}"

step "configuration restart (Easy, Normal, Expert)"
"$ASSET_PYTHON" tools/verify_config_restart.py "${RESTART_ARGS[@]}"

step "optional dependency runtimes (core, AE2, complete)"
"$ASSET_PYTHON" tools/verify_dependency_runtime.py "${RESTART_ARGS[@]}"

if [ "$RUN_DATAGEN" -eq 1 ]; then
    step "datagen freshness"
    "$GRADLE" "${GRADLE_ARGS[@]}" runData
    if [ -n "$(git status --porcelain src/generated)" ]; then
        echo "FAIL: src/generated/resources is stale; run runData and commit the result." >&2
        git --no-pager diff --stat src/generated
        exit 1
    fi
fi

step "build (includes asset, language, Jade, and design-contract checks)"
"$GRADLE" "${GRADLE_ARGS[@]}" build

printf '\nAll gates passed.\n'
