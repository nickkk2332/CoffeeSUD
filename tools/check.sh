#!/bin/sh
# The checks for a commit: the fixes suite and the smoke test, side by side
# (about ten minutes).  With ENGINE=1, also the engine's own TEST ALL suite
# (about a quarter of an hour more, alongside), for the end of a step and for
# changes to the engine's core.  Builds once first, so that the suites do not
# build at the same time.  Prints each suite's result and the time taken, and
# keeps each suite's full output in a folder it names.
#   tools/check.sh            fixes suite and smoke test
#   ENGINE=1 tools/check.sh   and the engine suite
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d "${TMPDIR:-/tmp}/sudcheck.XXXXXX")"
START=$(date +%s)
"$ROOT/CoffeeMud/build.sh" > "$OUT/build.txt" 2>&1 || { cat "$OUT/build.txt"; exit 1; }

"$ROOT/tools/fixes.sh" > "$OUT/fixes.txt" 2>&1 &
F_PID=$!
"$ROOT/tools/smoke.sh" > "$OUT/smoke.txt" 2>&1 &
S_PID=$!
if [ "$ENGINE" = "1" ]; then
	"$ROOT/tools/engine-tests.sh" > "$OUT/engine.txt" 2>&1 &
	E_PID=$!
fi

FAIL=0
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
report fixes $F_PID
report smoke $S_PID
[ "$ENGINE" = "1" ] && report engine $E_PID

echo "took $(( ($(date +%s) - START) / 60 )) min $(( ($(date +%s) - START) % 60 )) s; output in $OUT"
exit $FAIL
