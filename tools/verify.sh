#!/usr/bin/env bash
# Compatibility entry point; tools/verify.py is the local and CI release gate.
# Usage: bash tools/verify.sh [--offline] [--no-datagen]
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${GSE_ASSET_PYTHON:-}" ]; then
    ASSET_PYTHON="$GSE_ASSET_PYTHON"
else
    ASSET_PYTHON=""
    if [[ "${OS:-}" == "Windows_NT" ]]; then
        CANDIDATES=(python py python3)
    else
        CANDIDATES=(python3 python)
    fi
    for candidate in "${CANDIDATES[@]}"; do
        if command -v "$candidate" >/dev/null 2>&1 &&
                "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' >/dev/null 2>&1; then
            ASSET_PYTHON="$candidate"
            break
        fi
    done
    if [ -z "$ASSET_PYTHON" ]; then
        echo "Python 3.10+ is required; see tools/README.md." >&2
        exit 1
    fi
fi
exec "$ASSET_PYTHON" tools/verify.py "$@"
