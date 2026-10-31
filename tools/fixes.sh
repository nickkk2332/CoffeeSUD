#!/bin/sh
# Tests for the engine fixes of DEVPLAN.md step 2a.  Each file in
# tools/fixtures/fixes/ (but prelude.js) is a JavaScript test, run with JRUN in
# a builder-mode game on a scratch copy of the world, after prelude.js.  A test
# reports "FIXTEST ok FILE/NAME" or "FIXTEST FAIL FILE/NAME: why", and ends with
# "FIXTEST done FILE".  Fails if any check fails, a file does not reach its
# end, or a script errs.  Played sessions for what needs real play follow.
#   tools/fixes.sh            run the tests
#   KEEP=1 tools/fixes.sh     keep the scratch folder and print its path
#   tools/fixes.sh NAME ...   run only these test files (without .js)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
FX="$ROOT/tools/fixtures/fixes"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudfixes.XXXXXX")"
mkdir -p "$W/db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/db/"
cp -r "$GAME/world" "$W/world"
mkdir -p "$GAME/resources/_fixes"
cp "$FX/setup/"*.js "$GAME/resources/_fixes/"
trap 'rm -rf "$GAME/resources/_fixes"' EXIT
export JAVA_TOOL_OPTIONS="-Djava.awt.headless=true $JAVA_TOOL_OPTIONS"
FAIL=0

"$GAME/build.sh" > "$W/build.txt" 2>&1 || { cat "$W/build.txt"; exit 1; }

if [ $# -gt 0 ]; then TESTS="$*"; else
	TESTS="$(cd "$FX" && ls *.js | sed 's/\.js$//' | grep -v '^prelude$')"
fi
SCRIPT='Fixer\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
for T in $TESTS; do
	cat "$FX/prelude.js" "$FX/$T.js" > "$GAME/resources/_fixes/$T.js"
	SCRIPT="${SCRIPT}jrun /resources/_fixes/$T.js\n"
done
SCRIPT="${SCRIPT}quit\nY\n"
printf "$SCRIPT" > "$W/jrun.script"
echo "JavaScript tests: $(echo $TESTS | wc -w) files"
python3 "$ROOT/tools/sudplay.py" "$W/jrun.script" "DBSERVICE=jdbc:fakedb:$W/db" "WORLDDIR=$W/world" "SAVEDIR=$W/saves" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS > "$W/jrun.txt" 2>&1
grep -a "FIXTEST ok" "$W/jrun.txt" | sed 's/.*FIXTEST ok /  ok    /'
if grep -aq "FIXTEST FAIL" "$W/jrun.txt"; then
	grep -a "FIXTEST FAIL" "$W/jrun.txt" | sed 's/.*FIXTEST FAIL /  FAIL  /'
	FAIL=1
fi
for T in $TESTS; do
	if ! grep -aq "FIXTEST done $T\$" "$W/jrun.txt" && ! grep -aq "FIXTEST done $T[^A-Za-z0-9_-]" "$W/jrun.txt"; then
		echo "  FAIL  $T: did not run to its end"; FAIL=1
	fi
done
# a test's own script error (JS2 names the file: "JS error in JRUN ...")
if grep -aq "JavaScript error\|JS error in JRUN" "$W/jrun.txt"; then
	grep -a "JavaScript error\|JS error in JRUN" "$W/jrun.txt" | sed 's/^/  FAIL  /'; FAIL=1
fi

expect()  # expect OUTFILE TEXT
{
	if grep -aqF -- "$2" "$W/$1"; then
		echo "  ok    $1: $2"
	else
		echo "  FAIL  $1: missing '$2'"
		FAIL=1
	fi
}
play()  # play OUTFILE DB WORLD SCRIPT [NAME=VALUE ...]
{
	OUT="$1"; DB="$2"; WD="$3"; printf "$4" > "$W/$OUT.script"; shift 4
	python3 "$ROOT/tools/sudplay.py" "$W/$OUT.script" "DBSERVICE=jdbc:fakedb:$W/$DB" "WORLDDIR=$W/$WD" "SAVEDIR=$W/saves" "$@" > "$W/$OUT" 2>&1
}

if [ $# -eq 0 ]; then
	echo "Played: ST1, a quiet room hears logins, in a new game and after a restore"
	mkdir -p "$W/pdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/pdb/"
	cp -r "$GAME/world" "$W/pworld"
	play st1-build.txt pdb pworld 'Builder\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\njrun /resources/_fixes/st1-room.js\nquit\nY\n' BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	expect st1-build.txt "FIXSETUP st1 room"
	play st1-new.txt pdb pworld 'Greeter\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nlook\nwait\nsave st1\nquit\nn\n' RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect st1-new.txt "FIXGREET the quiet room heard Greeter log in."
	play st1-restore.txt pdb pworld 'look\nwait\nquit\nn\n' RESTORE=st1 TIMEMODE=TURNS
	expect st1-restore.txt "Restored 'st1'."
	expect st1-restore.txt "FIXGREET the quiet room heard Greeter log in."

	echo "Played: PR5, the game's name, the world's title screen and prologue, and the menu"
	mkdir -p "$W/tdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/tdb/"
	cp -r "$GAME/world" "$W/tworld"
	mkdir -p "$W/tworld/text"
	printf 'FIXTITLE ^G@MudInfo?NAME@^N\n' > "$W/tworld/text/title.txt"
	printf 'FIXPAGE ONE\n---\nFIXPAGE TWO\n' > "$W/tworld/text/prologue.txt"
	play pr5-new.txt tdb tworld '\nTitler\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nsave pr5\nquit\nn\n' GAMENAME=Testland RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect pr5-new.txt "FIXTITLE"
	expect pr5-new.txt "Testland"
	expect pr5-new.txt "FIXPAGE ONE"
	expect pr5-new.txt "[Enter]"
	expect pr5-new.txt "FIXPAGE TWO"
	expect pr5-new.txt "Welcome to Testland"
	expect pr5-new.txt "Game saved as 'pr5'."
	if grep -aq "Powered By CoffeeMud" "$W/pr5-new.txt"; then
		echo "  FAIL  pr5-new.txt: the stock intro was shown as well as the world's title"; FAIL=1
	else
		echo "  ok    pr5-new.txt: only the world's title was shown"
	fi
	play pr5-menu.txt tdb tworld 'C\nscore\nquit\nn\n' GAMENAME=Testland TIMEMODE=TURNS
	expect pr5-menu.txt "C) Continue - pr5"
	expect pr5-menu.txt "N) New game"
	expect pr5-menu.txt "1) pr5"
	expect pr5-menu.txt "Restored 'pr5'."
fi

if [ "$FAIL" = 0 ]; then
	echo "PASS"
	[ -n "$KEEP" ] && echo "kept: $W" || rm -rf "$W"
	exit 0
fi
echo "FAILED.  Output kept in $W"
exit 1
