#!/bin/sh
# Runs the engine's own TEST ALL suite through the single-player console and
# compares each test's result with tools/baseline/test-all.txt, recorded on
# the unmodified server.  Takes about a quarter of an hour.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudtests.XXXXXX")"
"$GAME/build.sh" > "$W/build.txt" 2>&1 || { cat "$W/build.txt"; exit 1; }
# play a copy, so the source tree can be rebuilt while this runs
cp -r "$GAME" "$W/game"
# an empty database, as a fresh checkout has: the game builds the world
# from the world files
mkdir "$W/db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/db/"

printf 'Zorblax\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nget book\nread book\ntest all\n#expect ZapperAnyclasslevel:\nquit\nY\n' > "$W/script"
SUD_GAME_DIR="$W/game" SUDPLAY_LIMIT=2400 python3 "$ROOT/tools/sudplay.py" "$W/script" "DBSERVICE=jdbc:fakedb:$W/db" "SAVEDIR=$W/saves" RESTORE=NEW > "$W/out.txt" 2>&1

# one "Name: PASS" or "Name: FAIL" line per test; failure messages are left
# out because some tests report random numbers.  Game messages can arrive
# between a test's name and its result, so a name with no result yet waits
# for the next line that is exactly "Passed".
summarize()
{
	sed 's/^<[^>]*> *//' | awk '
		/^[A-Za-z0-9]+ *: / {
			name=$1; res=$0; sub(/^[^:]*: */, "", res)
			if (res == "") { pending=name; next }
			pending=""
			print name ": " (res == "Passed" ? "PASS" : "FAIL"); next
		}
		pending != "" && $0 == "Passed" { print pending ": PASS"; pending="" }
	' | sed -n '/^AreaTrails:/,/^ZapperAnyclasslevel:/p'
}
summarize < "$W/out.txt" > "$W/results.txt"
grep -v '^#' "$ROOT/tools/baseline/test-all.txt" | summarize > "$W/baseline.txt"
if [ ! -s "$W/results.txt" ]; then
	echo "FAILED: the test suite did not run.  Output kept in $W"
	exit 1
fi
if diff "$W/baseline.txt" "$W/results.txt" > "$W/diff.txt"; then
	echo "PASS: $(grep -c ': PASS' "$W/results.txt") passed and $(grep -c ': FAIL' "$W/results.txt") failed, as in the baseline"
	rm -rf "$W"
	exit 0
fi
echo "FAILED: results differ from the baseline (< baseline, > now):"
cat "$W/diff.txt"
echo "Output kept in $W"
exit 1
