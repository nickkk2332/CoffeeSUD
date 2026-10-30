#!/bin/sh
# Single-player smoke test.  Plays scripted sessions against a scratch copy
# of the database, with saves in a scratch folder: one creates a character,
# plays a little and saves, the next restores the save, then come pause,
# turn-based time, the random seed, saving and restoring during play, a
# changed world coming back as it was saved, and, in a world of their own,
# a running quest, a follower, a lit torch and a wolf's rolled stats.
# Fails if expected text is missing, or if play wrote to the content.
#   tools/smoke.sh            run the test
#   KEEP=1 tools/smoke.sh     keep the scratch folder and print its path
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudsmoke.XXXXXX")"
cp -r "$GAME/resources/fakedb" "$W/db"
(cd "$W/db" && cksum * > "$W/content.sums")
cp -r "$GAME/resources/fakedb" "$W/qdb"
# the quest sessions load their quest and their helper script from here
mkdir -p "$GAME/resources/quests/_smoke"
cp "$ROOT/tools/fixtures/smoke/"* "$GAME/resources/quests/_smoke/"
trap 'rm -rf "$GAME/resources/quests/_smoke"' EXIT
FAIL=0

play()  # play OUTFILE SCRIPT [NAME=VALUE ...]
{
	OUTFILE="$1"; printf "$2" > "$W/$OUTFILE.script"; shift 2
	python3 "$ROOT/tools/sudplay.py" "$W/$OUTFILE.script" "DBSERVICE=jdbc:fakedb:$W/db" "SAVEDIR=$W/saves" "$@" > "$W/$OUTFILE" 2>&1
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
play new.txt 'Hero\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\ntimemode realtime\nlook\ninventory\nscore\nnorth\nlook\nsouth\nget book\nread book\nquit\nY\n' RANDOMSEED=42 TIMEMODE=TURNS RESTORE=NEW
expect new.txt "Welcome to CoffeeSUD"
expect new.txt "Character creation complete!"
expect new.txt "You are carrying:"
expect new.txt "You are Hero the level 1 Apprentice."
expect new.txt "Rocky Room"
expect new.txt "The manual glows softly"
expect new.txt "Game saved as 'hero'."
expect new.txt "Bye!"

echo "Session 2: restore the save from the list at startup"
play load.txt '1\nscore\nquit\nY\n'
expect load.txt "1) hero"
expect load.txt "Restored 'hero'."
expect load.txt "You are Hero the Archon."
expect load.txt "Bye!"

echo "Session 3: pause and resume (one mud hour per 4-second tick)"
play pause.txt 'time\n#sleep 9\ntime\npause\ntime\n#sleep 9\ntime\nnorth\nlook\nresume\n#sleep 2\nquit\nY\n' MILLISPERMUDHOUR=4000 RESTORE=hero
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
play turns.txt 'timemode turns\ntime\n#sleep 6\ntime\nwait\ntime\nwait 3\ntime\nsouth\npause\ncreate mob minotaur\ncreate mob goblin\npossess goblin\nkill minotaur\nwait 50\nquit\ntimemode realtime\n#sleep 9\ntime\nquit\nY\n' MILLISPERMUDHOUR=4000 RESTORE=hero
expect turns.txt "Time is now turn-based."
expect turns.txt "New Room"
expect turns.txt "the world already waits for you"
expect turns.txt "You stop waiting."
expect turns.txt "Time now runs in real time."
# the five TIME answers: before and after 6 idle seconds, after WAIT, after
# WAIT 3, and after 9 seconds of real time; TIME shows the hour as H/L, where
# L is the day's last hour, numbered from 0.  The hour turns on an area's
# tick, so four ticks of waiting turn it three or four times, depending on
# where in the hour the restored game was.
grep -ao "(Hour: [0-9]*/[0-9]*)" "$W/turns.txt" | tr -d '()' | sed 's/Hour: //' > "$W/hours"
if awk -F/ '{h[NR]=$1; n=$2+1} END {
	moved=(h[4]-h[2]+n)%n
	ok=(NR==5) && (h[1]==h[2]) && (moved==3 || moved==4) && (h[5]!=h[4])
	exit !ok }' "$W/hours"; then
	echo "  ok    turns.txt: the clock waited for the player, moved an hour a tick while WAITing, then ran"
else
	echo "  FAIL  turns.txt: clock readings were $(tr '\n' ' ' < "$W/hours")"; FAIL=1
fi

echo "Session 5: the same RANDOMSEED rolls the same new character"
printf 'Zorb\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nscore\nquit\nY\n' > "$W/seed.script"
for i in 1 2; do
	cp -r "$GAME/resources/fakedb" "$W/seeddb$i"
	python3 "$ROOT/tools/sudplay.py" "$W/seed.script" "DBSERVICE=jdbc:fakedb:$W/seeddb$i" "SAVEDIR=$W/seedsaves$i" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS > "$W/seed$i.txt" 2>&1 &
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

echo "Session 6: save, move, and restore during play"
play restore.txt 'north\nsave second\nsouth\nrestore second\ny\nsaves\nquit\nn\n' RESTORE=hero
expect restore.txt "Game saved as 'second'."
expect restore.txt "Restoring 'second'..."
expect restore.txt "Restored 'second'."
expect restore.txt "second       Hero"
# the restored game puts Hero back where 'second' was saved
if sed -n "/Restored 'second'/,\$p" "$W/restore.txt" | grep -q "^Rocky Room"; then
	echo "  ok    restore.txt: back in Rocky Room after restoring"
else
	echo "  FAIL  restore.txt: not back in Rocky Room after restoring"; FAIL=1
fi

echo "Session 7: change the world, put timed effects on things, and save"
play world.txt 'timemode turns\nsouth\ndrop waterskin\nnorth\ncreate mob wolf\ncreate mob rabbit\nkill rabbit\nwait 3\nlook\nfreeze wolf\nhush hero\nscore\nsave world\nquit\nn\n' RESTORE=hero
expect world.txt "A wolf"
expect world.txt "Game saved as 'world'."

echo "Session 8: restore the changed world"
play world2.txt 'score\nlook\nsouth\nlook\nnorth\nwait 100\nlook\nquit\nn\n' RESTORE=world
expect world2.txt "Restored 'world'."
# the wolf is back, the rabbit stays dead until its time to come back, and
# the waterskin lies where it was dropped
sed -n "/Restored 'world'/,/You wait/p" "$W/world2.txt" > "$W/before"
if grep -q "A wolf" "$W/before" && grep -qi "waterskin" "$W/before" && ! grep -q "A rabbit hops by" "$W/before"; then
	echo "  ok    world2.txt: the wolf and the waterskin came back, and the dead rabbit did not"
else
	echo "  FAIL  world2.txt: the restored world is not as it was saved"; FAIL=1
fi
if sed -n "/You wait/,\$p" "$W/world2.txt" | grep -q "A rabbit hops by"; then
	echo "  ok    world2.txt: the rabbit came back on its time"
else
	echo "  FAIL  world2.txt: the rabbit did not come back after its time"; FAIL=1
fi
# a timed effect on the player comes back (the first SCORE shows it), and
# so do their hit points, mana and movement
expect world2.txt "(Hushed)"
if [ -n "$(grep -a "movement\." "$W/world.txt" | tail -1)" ] \
&& [ "$(grep -a "movement\." "$W/world.txt" | tail -1)" = "$(grep -a "movement\." "$W/world2.txt" | head -1)" ]; then
	echo "  ok    world2.txt: hit points, mana and movement came back as saved"
else
	echo "  FAIL  world2.txt: hit points, mana or movement changed across the save"; FAIL=1
fi

echo "Session 9: in builder mode, add a quest to a world of its own"
play qbuild.txt 'Builder\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nget book\nread book\ncreate quest LOAD=quests/_smoke/smoke.quest\nsave quests\nquit\nY\n' "DBSERVICE=jdbc:fakedb:$W/qdb" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
expect qbuild.txt "Quest 'smoketest' added."
expect qbuild.txt "Quest list saved."

echo "Session 10: start the quest, gain a follower, light the torch, and save"
play quest.txt 'Builder\ntimemode turns\nmodify quest smoketest start\ncreate mob dog\ncreate mob wolf\nstat wolf\njrun /resources/quests/_smoke/followfx.js\ngroup\nsave quests1\nquit\nn\n' "DBSERVICE=jdbc:fakedb:$W/qdb" RESTORE=NEW
expect quest.txt "This game plays on a copy of the world."
expect quest.txt "Quest 'smoketest' started."
expect quest.txt "] a dog"

echo "Session 11: restore the quest, the follower, the torch and the wolf"
play quest2.txt 'list quests\ngroup\nstat wolf\nsave quests2\nquit\nn\n' "DBSERVICE=jdbc:fakedb:$W/qdb" RESTORE=quests1
expect quest2.txt "Restored 'quests1'."
expect quest2.txt "] a dog"
if grep -aq "smoketest *running" "$W/quest2.txt"; then
	echo "  ok    quest2.txt: the quest is still running"
else
	echo "  FAIL  quest2.txt: the quest is not running after restoring"; FAIL=1
fi
if [ -n "$(grep -a "Weight:" "$W/quest.txt")" ] && [ "$(grep -a "Weight:" "$W/quest.txt")" = "$(grep -a "Weight:" "$W/quest2.txt")" ]; then
	echo "  ok    quest2.txt: the wolf kept the weight it rolled"
else
	echo "  FAIL  quest2.txt: the wolf's rolled stats changed"; FAIL=1
fi
# saved again after restoring: the torch is still lit, and the quest's
# rabbit is where it was
if grep -q "<ID>Spell_Light</ID>" "$W/saves/quests2/world.xml"; then
	echo "  ok    quests2: the carried torch's timed light came back"
else
	echo "  FAIL  quests2: the carried torch's timed light was lost"; FAIL=1
fi
if [ -n "$(grep -o "<QMOB[^/]*</ROOM>" "$W/saves/quests1/world.xml")" ] \
&& [ "$(grep -o "<QMOB[^/]*</ROOM>" "$W/saves/quests1/world.xml")" = "$(grep -o "<QMOB[^/]*</ROOM>" "$W/saves/quests2/world.xml")" ]; then
	echo "  ok    quests2: the quest's rabbit came back where it was"
else
	echo "  FAIL  quests2: the quest's rabbit is not where it was"; FAIL=1
fi

if (cd "$W/db" && cksum * | cmp -s - "$W/content.sums"); then
	echo "  ok    the content database was not written"
else
	echo "  FAIL  play wrote to the content database"; FAIL=1
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
