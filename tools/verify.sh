#!/usr/bin/env bash
# Local mirror of the CI gate (.github/workflows/build.yml).
#
# Runs the same steps in the same order so a green local run implies a green
# CI run, short of dependency-download differences. `runGameTestServer` boots
# a real dedicated server and takes minutes; the datagen step rewrites
# src/generated/resources in place and fails if that produces a diff.
#
# Usage: tools/verify.sh [--no-datagen]

set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_ARGS=(--no-daemon)
RUN_DATAGEN=1
for arg in "$@"; do
    case "$arg" in
        --no-datagen) RUN_DATAGEN=0 ;;
        -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

# Windows checkouts use gradlew.bat; everything else uses gradlew.
if [ -f ./gradlew.bat ]; then
    GRADLE=./gradlew.bat
else
    GRADLE=./gradlew
fi

step() { printf '\n=== %s ===\n' "$1"; }

step "compile"
"$GRADLE" "${GRADLE_ARGS[@]}" compileJava

step "gametest"
"$GRADLE" "${GRADLE_ARGS[@]}" runGameTestServer

if [ "$RUN_DATAGEN" -eq 1 ]; then
    step "datagen freshness"
    "$GRADLE" "${GRADLE_ARGS[@]}" runData
    if [ -n "$(git status --porcelain src/generated)" ]; then
        echo "FAIL: src/generated/resources is stale; run runData and commit the result." >&2
        git --no-pager diff --stat src/generated
        exit 1
    fi
fi

step "lang key parity"
python - <<'PY'
import json, pathlib, sys
en = json.loads(pathlib.Path('src/generated/resources/assets/gregsteamexpansion/lang/en_us.json').read_text('utf-8'))
zh = json.loads(pathlib.Path('src/main/resources/assets/gregsteamexpansion/lang/zh_cn.json').read_text('utf-8'))
missing, extra = sorted(set(en) - set(zh)), sorted(set(zh) - set(en))
if missing or extra:
    print(f'FAIL: lang key mismatch: {len(missing)} missing in zh_cn, {len(extra)} extra in zh_cn', file=sys.stderr)
    for k in missing[:20]:
        print('  missing:', k, file=sys.stderr)
    for k in extra[:20]:
        print('  extra:  ', k, file=sys.stderr)
    sys.exit(1)
print(f'ok: lang keys aligned ({len(en)} keys)')
PY

step "build"
"$GRADLE" "${GRADLE_ARGS[@]}" build

printf '\nAll gates passed.\n'
