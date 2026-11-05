#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
market_variant="${1:-release}"
case "$market_variant" in release) market_task=assembleRelease;; debug) market_task=assembleDebug;; *) printf 'Choose release or debug\n' >&2; exit 2;; esac
bash scripts/gradle.sh ":app:$market_task"
mkdir -p releases
cp "app/build/outputs/apk/$market_variant/app-$market_variant.apk" "releases/MarketLens-Studio-1.0.0-$market_variant.apk"
printf 'Built releases/MarketLens-Studio-1.0.0-%s.apk\n' "$market_variant"
