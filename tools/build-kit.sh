#!/bin/sh
# Assemble the read-only key-capture kit zip from kit/.
# usage: tools/build-kit.sh [output zip]   (default: dist/hkg-keycap-kit.zip)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-keycap-kit.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp "$ROOT/kit/NEMESIS.data" "$ROOT/kit/NEMESIS.sh" "$ROOT/kit/README.txt" "$STAGE/"
# fixed timestamps so the same sources always give the same zip
touch -t 202601010000 "$STAGE"/*
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE" && zip -qrX "$OUT" NEMESIS.data NEMESIS.sh README.txt)
echo "$OUT"
