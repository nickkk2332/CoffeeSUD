#!/bin/sh
# Single-player smoke test.  Plays two scripted sessions against a scratch
# copy of the database: the first creates a character and plays a little,
# the second loads that character again.  Fails if expected text is missing.
#   tools/smoke.sh            run the test
#   KEEP=1 tools/smoke.sh     keep the scratch folder and print its path
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudsmoke.XXXXXX")"
cp -r "$GAME/resources/fakedb" "$W/db"
FAIL=0

play()  # play OUTFILE SCRIPT
{
	printf "$2" > "$W/script"
	python3 "$ROOT/tools/sudplay.py" "$W/script" "DBSERVICE=jdbc:fakedb:$W/db" > "$W/$1" 2>&1
	echo "$1: exit $?" >> "$W/exits"
}

expect()  # expect OUTFILE TEXT
{
	if grep -qF -- "$2" "$W/$1"; then
		echo "  ok    $1: $2"
	else
		echo "  FAIL  $1: missing '$2'"
		FAIL=1
	fi
}

"$GAME/build.sh" > "$W/build.txt" 2>&1 || { cat "$W/build.txt"; exit 1; }

echo "Session 1: create a character and play"
play new.txt 'Hero\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nlook\ninventory\nscore\nnorth\nlook\nsouth\nget book\nread book\nquit\nY\n'
expect new.txt "Welcome to CoffeeSUD"
expect new.txt "Character creation complete!"
expect new.txt "You are carrying:"
expect new.txt "You are Hero the level 1 Apprentice."
expect new.txt "Rocky Room"
expect new.txt "The manual glows softly"
expect new.txt "Bye!"

echo "Session 2: load the saved character"
play load.txt 'Hero\nscore\nquit\nY\n'
expect load.txt "You are Hero the Archon."
expect load.txt "Bye!"

if grep -q "exit [^0]" "$W/exits"; then
	echo "  FAIL  a session did not exit cleanly:"; cat "$W/exits"; FAIL=1
fi
if grep -a "ConsoleShutdow\|HOST0" "$GAME/mud.log" | grep -qa " error "; then
	echo "  note  errors in mud.log:"; grep -a " error " "$GAME/mud.log" | head -5
fi

if [ "$KEEP" = "1" ] || [ $FAIL -ne 0 ]; then echo "Output kept in $W"; else rm -rf "$W"; fi
if [ $FAIL -eq 0 ]; then echo "PASS"; else echo "FAILED"; fi
exit $FAIL
