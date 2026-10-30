#!/bin/sh
# Runs the engine's own TEST ALL suite through the single-player console and
# compares each test's result with tools/baseline/test-all.txt, recorded on
# the unmodified server.  Takes about a quarter of an hour.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudtests.XXXXXX")"
cp -r "$GAME/resources/fakedb" "$W/db"
"$GAME/build.sh" > "$W/build.txt" 2>&1 || { cat "$W/build.txt"; exit 1; }

printf 'Tester\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nget book\nread book\ntest all\n#expect ZapperAnyclasslevel:\nquit\nY\n' > "$W/script"
SUDPLAY_LIMIT=2400 python3 "$ROOT/tools/sudplay.py" "$W/script" "DBSERVICE=jdbc:fakedb:$W/db" > "$W/out.txt" 2>&1

# one "Name : result" line per test, as in the baseline
sed -n '/^AreaTrails *:/,/^ZapperAnyclasslevel *:/p' "$W/out.txt" | grep -a "^[A-Za-z0-9]* *: " > "$W/results.txt"
grep -v '^#' "$ROOT/tools/baseline/test-all.txt" > "$W/baseline.txt"
if diff "$W/baseline.txt" "$W/results.txt" > "$W/diff.txt"; then
	echo "PASS: $(grep -c Passed "$W/results.txt") passed, same results as the baseline"
	rm -rf "$W"
	exit 0
fi
echo "FAILED: results differ from the baseline (< baseline, > now):"
cat "$W/diff.txt"
echo "Output kept in $W"
exit 1
