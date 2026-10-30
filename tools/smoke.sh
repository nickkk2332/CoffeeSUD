#!/bin/sh
# Single-player smoke test.  Plays scripted sessions against a scratch copy
# of the database: one creates a character and plays a little, the next
# loads that character again, then come pause, turn-based time, and the
# random seed.  Fails if expected text is missing.
#   tools/smoke.sh            run the test
#   KEEP=1 tools/smoke.sh     keep the scratch folder and print its path
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudsmoke.XXXXXX")"
cp -r "$GAME/resources/fakedb" "$W/db"
FAIL=0

play()  # play OUTFILE SCRIPT [NAME=VALUE ...]
{
	OUTFILE="$1"; printf "$2" > "$W/$OUTFILE.script"; shift 2
	python3 "$ROOT/tools/sudplay.py" "$W/$OUTFILE.script" "DBSERVICE=jdbc:fakedb:$W/db" "$@" > "$W/$OUTFILE" 2>&1
	set -- "$OUTFILE"
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
# the seed, with the clock stopped while the character is made, fixes the
# stat rolls, which can otherwise rule out the Apprentice class
play new.txt 'Hero\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\ntimemode realtime\nlook\ninventory\nscore\nnorth\nlook\nsouth\nget book\nread book\nquit\nY\n' RANDOMSEED=42 TIMEMODE=TURNS
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

echo "Session 3: pause and resume (one mud hour per 4-second tick)"
play pause.txt 'Hero\ntime\n#sleep 9\ntime\npause\ntime\n#sleep 9\ntime\nnorth\nlook\nresume\n#sleep 2\nquit\nY\n' MILLISPERMUDHOUR=4000
expect pause.txt "The world holds still."
expect pause.txt "(paused) "
expect pause.txt "The world moves again."
# the four TIME answers: running, running, paused, paused
grep -ao "(Hour: [0-9]*/[0-9]*)" "$W/pause.txt" > "$W/hours"
if [ "$(sed -n 1p "$W/hours")" != "$(sed -n 2p "$W/hours")" ] && [ "$(sed -n 3p "$W/hours")" = "$(sed -n 4p "$W/hours")" ]; then
	echo "  ok    pause.txt: the clock ran, then stood still while paused"
else
	echo "  FAIL  pause.txt: clock readings were $(tr '\n' ' ' < "$W/hours")"; FAIL=1
fi
# NORTH waited for RESUME: no Rocky Room before the world moved again
if sed -n '/(paused)/,/The world moves again/p' "$W/pause.txt" | grep -q "^Rocky Room"; then
	echo "  FAIL  pause.txt: a command that takes time ran while paused"; FAIL=1
else
	echo "  ok    pause.txt: NORTH waited for RESUME"
fi
expect pause.txt "Rocky Room"

echo "Session 4: turn-based time, and a fight one WAIT at a time"
play turns.txt 'Hero\ntimemode turns\ntime\n#sleep 6\ntime\nwait\ntime\nwait 3\ntime\nsouth\npause\ncreate mob minotaur\ncreate mob goblin\npossess goblin\nkill minotaur\nwait 50\nquit\ntimemode realtime\n#sleep 9\ntime\nquit\nY\n' MILLISPERMUDHOUR=4000
expect turns.txt "Time is now turn-based."
expect turns.txt "New Room"
expect turns.txt "the world already waits for you"
expect turns.txt "You stop waiting."
expect turns.txt "Time now runs in real time."
# the five TIME answers: before and after 6 idle seconds, after WAIT, after
# WAIT 3, and after 9 seconds of real time; TIME shows the hour as H/L, where
# L is the day's last hour, numbered from 0
grep -ao "(Hour: [0-9]*/[0-9]*)" "$W/turns.txt" | tr -d '()' | sed 's/Hour: //' > "$W/hours"
if awk -F/ '{h[NR]=$1; n=$2+1} END {
	ok=(NR==5) && (h[1]==h[2]) && ((h[3]-h[2]+n)%n==1) && ((h[4]-h[3]+n)%n==3) && (h[5]!=h[4])
	exit !ok }' "$W/hours"; then
	echo "  ok    turns.txt: the clock waited for the player, moved one hour per WAITed tick, then ran"
else
	echo "  FAIL  turns.txt: clock readings were $(tr '\n' ' ' < "$W/hours")"; FAIL=1
fi

echo "Session 5: the same RANDOMSEED rolls the same new character"
printf 'Zorb\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nscore\nquit\nY\n' > "$W/seed.script"
for i in 1 2; do
	cp -r "$GAME/resources/fakedb" "$W/seeddb$i"
	python3 "$ROOT/tools/sudplay.py" "$W/seed.script" "DBSERVICE=jdbc:fakedb:$W/seeddb$i" RANDOMSEED=42 TIMEMODE=TURNS > "$W/seed$i.txt" 2>&1 &
done
wait
for i in 1 2; do
	grep -aE "^(Strength|Intelligence|Dexterity|Constitution|Charisma|Wisdom) *: *[0-9]+ */|tall and weigh" "$W/seed$i.txt" > "$W/rolls$i"
done
if [ -s "$W/rolls1" ] && cmp -s "$W/rolls1" "$W/rolls2"; then
	echo "  ok    seed: both characters rolled $(tr -s ' ' < "$W/rolls1" | tr '\n' ' ' | cut -c1-60)..."
else
	echo "  FAIL  seed: the rolls differ or are missing"; FAIL=1
fi

if grep -q "exit [^0]" "$W/exits"; then
	echo "  FAIL  a session did not exit cleanly:"; cat "$W/exits"; FAIL=1
fi
if grep -a "ConsoleShutdow\|HOST0" "$GAME/mud.log" | grep -qa " error "; then
	echo "  note  errors in mud.log:"; grep -a " error " "$GAME/mud.log" | head -5
fi

if [ "$KEEP" = "1" ] || [ $FAIL -ne 0 ]; then echo "Output kept in $W"; else rm -rf "$W"; fi
if [ $FAIL -eq 0 ]; then echo "PASS"; else echo "FAILED"; fi
exit $FAIL
