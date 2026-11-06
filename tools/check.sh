#!/bin/sh
# The checks for a commit: the creator-docs check and the composition, combat
# rules and item modifier tests, then the fixes suite and the smoke test, run
# in parallel (measured on one container: fixes about 30 minutes, smoke about
# 15, engine about 13, so expect 10 to 30 minutes in all, the slowest suite
# deciding).  With ENGINE=1, the engine's own TEST ALL suite runs too, in
# parallel with them, for the end of a step and for changes to the engine's
# core.  It does not run tools/storage-tests.sh.  Builds once first, so that
# the suites do not build at the same time.  Prints each suite's result and
# the time taken, and keeps each suite's full output in a folder it names.
#   tools/check.sh            fixes suite and smoke test
#   ENGINE=1 tools/check.sh   and the engine suite
#   CHECKS_SERIAL=1 FIXES_JOBS=2 ENGINE=1 tools/check.sh   on small CI machines
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/sudcheck.XXXXXX")"
START=$(date +%s)
"$ROOT/CoffeeMud/build.sh" > "$OUT/build.txt" 2>&1 || { cat "$OUT/build.txt"; exit 1; }

FAIL=0
# the creator handbook's generated references and links, against the sources
python3 "$ROOT/tools/creator_docs.py" --check > "$OUT/creator-docs.txt" 2>&1
if [ $? -eq 0 ]; then
	echo "creator docs: PASS ($(tail -1 "$OUT/creator-docs.txt"))"
else
	echo "creator docs: FAILED (see $OUT/creator-docs.txt)"
	FAIL=1
fi
"$ROOT/tools/composition-tests.sh" > "$OUT/composition.txt" 2>&1
if [ $? -eq 0 ]; then
	echo "composition: PASS ($(grep -c '^ok ' "$OUT/composition.txt") checks)"
else
	echo "composition: FAILED (see $OUT/composition.txt)"
	FAIL=1
fi
"$ROOT/tools/combat-rules-tests.sh" > "$OUT/combat-rules.txt" 2>&1
if [ $? -eq 0 ]; then
	echo "combat rules: PASS ($(grep -c '^ok ' "$OUT/combat-rules.txt") checks)"
else
	echo "combat rules: FAILED (see $OUT/combat-rules.txt)"
	FAIL=1
fi
"$ROOT/tools/item-modifier-tests.sh" > "$OUT/item-modifiers.txt" 2>&1
if [ $? -eq 0 ]; then
	echo "item modifiers: PASS ($(grep -c '^ok ' "$OUT/item-modifiers.txt") checks)"
else
	echo "item modifiers: FAILED (see $OUT/item-modifiers.txt)"
	FAIL=1
fi
report()  # report NAME PID
{
	wait "$2"
	STATUS=$?
	OKS=$(grep -c "^  ok" "$OUT/$1.txt")
	if [ $STATUS -eq 0 ]; then
		echo "$1: PASS ($OKS checks)$(grep -a '^PASS: ' "$OUT/$1.txt" | sed 's/^PASS:/ -/')"
	else
		echo "$1: FAILED"
		grep -a "^  FAIL\|^FAILED" "$OUT/$1.txt" | sed 's/^/  /'
		FAIL=1
	fi
}
# Serial suites avoid combining the fixes' game instances with the smoke
# chains and the engine suite. Useful with CHECKS_SERIAL=1 FIXES_JOBS=2.
"$ROOT/tools/fixes.sh" > "$OUT/fixes.txt" 2>&1 &
F_PID=$!
if [ "$CHECKS_SERIAL" = "1" ]; then report fixes $F_PID; fi
"$ROOT/tools/smoke.sh" > "$OUT/smoke.txt" 2>&1 &
S_PID=$!
if [ "$CHECKS_SERIAL" = "1" ]; then report smoke $S_PID; fi
if [ "$ENGINE" = "1" ]; then
	"$ROOT/tools/engine-tests.sh" > "$OUT/engine.txt" 2>&1 &
	E_PID=$!
fi
if [ "$CHECKS_SERIAL" != "1" ]; then
	report fixes $F_PID
	report smoke $S_PID
fi
[ "$ENGINE" = "1" ] && report engine $E_PID

echo "took $(( ($(date +%s) - START) / 60 )) min $(( ($(date +%s) - START) % 60 )) s; output in $OUT"
exit $FAIL
