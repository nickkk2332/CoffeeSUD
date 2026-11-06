#!/bin/sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/CoffeeMud/build.sh"
W="$(mktemp -d "${TMPDIR:-/tmp}/sud-combat-classes.XXXXXX")"
trap 'rm -rf "$W"' EXIT
javac -nowarn -Xlint:none --release 8 -cp "$ROOT/CoffeeMud:$ROOT/CoffeeMud/lib/js.jar" -d "$W" "$ROOT/tools/CombatRulesTest.java"
java -cp "$W:$ROOT/CoffeeMud:$ROOT/CoffeeMud/lib/js.jar" CombatRulesTest "$ROOT/docs/creator/examples/combat-profiles"
