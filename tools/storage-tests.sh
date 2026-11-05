#!/bin/sh
# Isolated filesystem/process tests: never reads a real campaign save folder.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/CoffeeMud/build.sh"
W="$(mktemp -d "${TMPDIR:-/tmp}/sud-storage-classes.XXXXXX")"
trap 'rm -rf "$W"' EXIT
javac -nowarn -Xlint:none --release 8 -cp "$ROOT/CoffeeMud:$ROOT/CoffeeMud/lib/js.jar" -d "$W" "$ROOT/tools/SaveStorageTest.java"
java -cp "$W:$ROOT/CoffeeMud:$ROOT/CoffeeMud/lib/js.jar" SaveStorageTest
