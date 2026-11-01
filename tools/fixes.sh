#!/bin/sh
# Tests for the engine changes of DEVPLAN.md step 2.  Each file in
# tools/fixtures/fixes/ (but prelude.js) is a JavaScript test, run with JRUN in
# a builder-mode game on a scratch copy of the world, after prelude.js.  A test
# reports "FIXTEST ok FILE/NAME" or "FIXTEST FAIL FILE/NAME: why", and ends with
# "FIXTEST done FILE".  Fails if any check fails, a file does not reach its
# end, or a script errs.  Played sessions for what needs real play run
# alongside, each group with its own world, database and saves.
#   tools/fixes.sh            run the tests
#   KEEP=1 tools/fixes.sh     keep the scratch folder and print its path
#   tools/fixes.sh NAME ...   run only these test files (without .js)
#   tools/fixes.sh saves      run only the played save tests (step 2b)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GAME="$ROOT/CoffeeMud"
FX="$ROOT/tools/fixtures/fixes"
W="$(mktemp -d "${TMPDIR:-/tmp}/sudfixes.XXXXXX")"
mkdir -p "$W/db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/db/"
cp -r "$GAME/world" "$W/world"
mkdir -p "$GAME/resources/_fixes"
# the played tests' files: their scripts after prelude.js, the rest as they are
for F in "$FX/setup/"*; do
	case "$F" in
	*.js) cat "$FX/prelude.js" "$F" > "$GAME/resources/_fixes/$(basename "$F")" ;;
	*) cp "$F" "$GAME/resources/_fixes/" ;;
	esac
done
trap 'rm -rf "$GAME/resources/_fixes"' EXIT
export JAVA_TOOL_OPTIONS="-Djava.awt.headless=true $JAVA_TOOL_OPTIONS"
FAIL=0

"$GAME/build.sh" > "$W/build.txt" 2>&1 || { cat "$W/build.txt"; exit 1; }

if [ "$1" = "saves" ]; then TESTS=""; elif [ $# -gt 0 ]; then TESTS="$*"; else
	TESTS="$(cd "$FX" && ls *.js | sed 's/\.js$//' | grep -v '^prelude$')"
fi
SCRIPT='Fixer\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
for T in $TESTS; do
	cat "$FX/prelude.js" "$FX/$T.js" > "$GAME/resources/_fixes/$T.js"
	SCRIPT="${SCRIPT}jrun /resources/_fixes/$T.js\n"
done
SCRIPT="${SCRIPT}quit\nY\n"
printf "$SCRIPT" > "$W/jrun.script"
expect()  # expect OUTFILE TEXT
{
	if grep -aqF -- "$2" "$W/$1"; then
		echo "  ok    $1: $2"
	else
		echo "  FAIL  $1: missing '$2'"
		FAIL=1
	fi
}
count()  # count OUTFILE TEXT N: TEXT appears on exactly N lines
{
	N=$(grep -acF -- "$2" "$W/$1")
	if [ "$N" = "$3" ]; then
		echo "  ok    $1: '$2' $3 times"
	else
		echo "  FAIL  $1: '$2' $N times, not $3"
		FAIL=1
	fi
}
jsresults()  # jsresults OUTFILE: a played test's JavaScript checks
{
	grep -a "FIXTEST ok" "$W/$1" | sed 's/.*FIXTEST ok /  ok    /'
	if grep -aq "FIXTEST FAIL" "$W/$1"; then
		grep -a "FIXTEST FAIL" "$W/$1" | sed 's/.*FIXTEST FAIL /  FAIL  /'
		FAIL=1
	fi
	if ! grep -aq "FIXTEST done" "$W/$1"; then
		echo "  FAIL  $1: a script did not run to its end"; FAIL=1
	fi
	if grep -aq "JavaScript error\|JS error in JRUN" "$W/$1"; then
		grep -a "JavaScript error\|JS error in JRUN" "$W/$1" | sed 's/^/  FAIL  /'; FAIL=1
	fi
}
play()  # play OUTFILE DB WORLD SCRIPT [NAME=VALUE ...]
{
	OUT="$1"; DB="$2"; WD="$3"; printf "$4" > "$W/$OUT.script"; shift 4
	python3 "$ROOT/tools/sudplay.py" "$W/$OUT.script" "DBSERVICE=jdbc:fakedb:$W/$DB" "WORLDDIR=$W/$WD" "SAVEDIR=${SAVES:-$W/saves}" "$@" > "$W/$OUT" 2>&1
}

group_jrun()
{
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
}

group_st1()
{
	SAVES="$W/psaves"
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
}

group_pr5()
{
	SAVES="$W/tsaves"
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
}

group_saves()
{
	SAVES="$W/ssaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	echo "Played: step 2b, what a save keeps"
	mkdir -p "$W/sdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/sdb/"
	cp -r "$GAME/world" "$W/sworld"
	# S6: a help file and a command class of the world's own
	mkdir -p "$W/sworld/resources/help" "$W/sworld/classes/commands"
	printf 'FIXHELP=FIXHELP the world has its own help.\n' > "$W/sworld/resources/help/fixhelp.ini"
	cp "$FX/classes/FixWorld.js" "$W/sworld/classes/commands/"
	play s-build.txt sdb sworld "Builder\n${NEWCHAR}create quest LOAD=_fixes/s1.quest\ncreate quest LOAD=_fixes/s1wait.quest\nsave quests\njrun /resources/_fixes/s1-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	expect s-build.txt "Quest 's1quest' added."
	expect s-build.txt "Quest 's1wait' added."
	expect s-build.txt "FIXSETUP s1 area offers the quest"

	# S1: quest variables, a waiting quest's wait, the quest giver's offers,
	# QuestChat's said-once lines, and a savable script's ONCE_PROG
	play s1-play.txt sdb sworld "Sayer\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s1-play.js\nlook\nN\nlook\nsay hello\nwait 6\nsay hello\nwait 6\nsave s1\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s1-play.txt
	count s1-play.txt "FIXOFFER" 1
	count s1-play.txt "FIXCHAT" 1
	count s1-play.txt "FIXONCE" 1
	expect s1-play.txt "Game saved as 's1'."
	play s1-restore.txt sdb sworld 'look\nsay hello\nwait 6\njrun /resources/_fixes/s1-check.js\nquit\nn\n' RESTORE=s1 TIMEMODE=TURNS
	expect s1-restore.txt "Restored 's1'."
	jsresults s1-restore.txt
	count s1-restore.txt "FIXOFFER" 0
	count s1-restore.txt "FIXCHAT" 0
	count s1-restore.txt "FIXONCE" 0

	# S2: save rules.  Save points, a place that refuses saves, an autosave
	# the first time a character comes in, and no saving in a fight
	play s2-points.txt sdb sworld "Pointer\n${NEWCHAR}get book\nread book\nsave s2a\njrun /resources/_fixes/s2-points.js\nsave s2a\nnorth\nsave s2b\nsouth\nnorth\nsouth\njrun /resources/_fixes/s2-fight.js\nsave s2a\nquit\nn\n" SAVEMODE=SAVEPOINTS RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s2-points.txt
	expect s2-points.txt "You can only save the game at a save point."
	expect s2-points.txt "FIXPOINT you rest at the shrine."
	expect s2-points.txt "Game saved as 's2a'."
	expect s2-points.txt "FIXNOSAVE the walls whisper too loudly."
	count s2-points.txt "FIXAUTO the game saved itself." 1
	expect s2-points.txt "You cannot save the game during a fight."
	expect s2-points.txt "Quit without saving (n), or cancel (C)?"
	if grep -q "^savemode=SAVEPOINTS" "$SAVES/s2a/header.properties" && [ -f "$SAVES/auto/header.properties" ]; then
		echo "  ok    s2-points: the save keeps its save mode, and the autosave was written"
	else
		echo "  FAIL  s2-points: no save mode in the header, or no autosave"; FAIL=1
	fi
	# an ironman game: one save, kept at QUIT; no going back
	play s2-iron.txt sdb sworld "Iron\n${NEWCHAR}save other\nsave\nrestore iron\nhelp fixhelp\nfixworld\nquit\nY\n" SAVEMODE=IRONMAN RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s2-iron.txt "FIXHELP the world has its own help."
	expect s2-iron.txt "FIXWORLDCMD ran from the world's own classes."
	expect s2-iron.txt "This is an ironman game: it has one save, 'iron', which it keeps for you."
	expect s2-iron.txt "Game saved as 'iron'."
	expect s2-iron.txt "This is an ironman game: there is no going back to a save."
	expect s2-iron.txt "Quit -- the game is saved as 'iron' (Y), or cancel (c)?"
	play s2-iron2.txt sdb sworld 'save other\nquit\nY\n' RESTORE=iron TIMEMODE=TURNS
	expect s2-iron2.txt "Restored 'iron'."
	expect s2-iron2.txt "This is an ironman game: it has one save, 'iron', which it keeps for you."

}

group_saves2()
{
	SAVES="$W/vsaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	echo "Played: step 2b, what a save keeps (second group)"
	mkdir -p "$W/vdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/vdb/"
	cp -r "$GAME/world" "$W/vworld"
	# S3: the world's own settings in world.ini, between coffeemud.ini and
	# the command line, and WORLD SET in builder mode
	mkdir -p "$W/s3db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/s3db/"
	cp -r "$GAME/world" "$W/s3world"
	printf '# the test world\nGAMENAME=Ashen Reach\nSTART=New Area#1\nHOURSINDAY=12\nTIMEMODE=REALTIME\nPORT=9999\n' > "$W/s3world/world.ini"
	play s3-new.txt s3db s3world "Ash\n${NEWCHAR}look\ntime\nworld\nworld set HOURSINDAY=6\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s3-new.txt "world.ini: PORT is not a world setting, so ignored"
	expect s3-new.txt "Welcome to Ashen Reach"
	expect s3-new.txt "(Hour: "
	if grep -aq "(Hour: [0-9]*/11)" "$W/s3-new.txt"; then
		echo "  ok    s3-new.txt: the day has the world's 12 hours"
	else
		echo "  FAIL  s3-new.txt: the day does not have the world's 12 hours"; FAIL=1
	fi
	if sed -n '/Press Enter to begin/,$p' "$W/s3-new.txt" | grep -aq "^Rocky Room"; then
		echo "  ok    s3-new.txt: the character started where the world says"
	else
		echo "  FAIL  s3-new.txt: the character did not start in the world's START room"; FAIL=1
	fi
	expect s3-new.txt "HOURSINDAY           = 12  (world.ini)"
	expect s3-new.txt "TIMEMODE             = TURNS  (the command line)"
	expect s3-new.txt "WORLD SET changes the world's own files, so it is for builder mode"
	play s3-build.txt s3db s3world "Builder\n${NEWCHAR}world set HOURSINDAY=10\nworld set PORT=1\nlist worldini\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	expect s3-build.txt "HOURSINDAY=10 is set in world.ini."
	expect s3-build.txt "PORT is not a world setting"
	expect s3-build.txt "GAMENAME             = Ashen Reach  (world.ini)"
	if grep -q "^HOURSINDAY=10$" "$W/s3world/world.ini" && grep -q "^# the test world$" "$W/s3world/world.ini" && [ "$(grep -c HOURSINDAY "$W/s3world/world.ini")" = 1 ]; then
		echo "  ok    s3-build: WORLD SET changed its line and kept the rest"
	else
		echo "  FAIL  s3-build: world.ini is not as WORLD SET should leave it"; FAIL=1
	fi
	# the next game starts with the changed world.ini
	play s3-after.txt s3db s3world "Cinder\n${NEWCHAR}time\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	if grep -aq "(Hour: [0-9]*/9)" "$W/s3-after.txt"; then
		echo "  ok    s3-after.txt: the next game has the 10 hours WORLD SET gave"
	else
		echo "  FAIL  s3-after.txt: the next game does not have 10 hours a day"; FAIL=1
	fi

	# S4: who rides what, in the world files and in saves.  Builder mode keeps
	# its characters in the cache, so later builder sessions play on as Builder
	play s4-build.txt vdb vworld "Builder\n${NEWCHAR}jrun /resources/_fixes/s4-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s4-build.txt
	if grep -q "IRIDE" "$W/vworld/areas/"*.cmare; then
		echo "  ok    s4-build: the area file says what the wagon is hitched to"
	else
		echo "  FAIL  s4-build: no IRIDE in the area file"; FAIL=1
	fi
	play s4-play.txt vdb vworld "Rider\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s4-play.js\nsave s4\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s4-play.txt
	play s4-restore.txt vdb vworld 'jrun /resources/_fixes/s4-check.js\nquit\nn\n' RESTORE=s4 TIMEMODE=TURNS
	expect s4-restore.txt "Restored 's4'."
	jsresults s4-restore.txt

	# S7: what scripts have done: a ONCE_PROG that ran, a queued MPALARM, a
	# DELAY_PROG under way and a script's own variable, through a save
	play s7-build.txt vdb vworld "Builder\njrun /resources/_fixes/s7-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s7-build.txt
	play s7-play.txt vdb vworld "Ringer\n${NEWCHAR}get book\nread book\nwait\njrun /resources/_fixes/s7-play.js\nsaves check\nwait 10\nsave s7\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s7-play.txt
	count s7-play.txt "FIXS7ONCE" 1
	count s7-play.txt "FIXS7ROOM" 1
	count s7-play.txt "FIXS7ALARM" 0
	count s7-play.txt "FIXS7DELAY" 0
	expect s7-play.txt "a summoned wisp in New Room"
	# 20 ticks were left on the alarm and 30 on the delay; a new game would
	# not have the alarm, and would toll only after 40
	play s7-restore.txt vdb vworld 'wait 35\njrun /resources/_fixes/s7-check.js\nquit\nn\n' RESTORE=s7 TIMEMODE=TURNS
	expect s7-restore.txt "Restored 's7'."
	jsresults s7-restore.txt
	count s7-restore.txt "FIXS7ONCE" 0
	count s7-restore.txt "FIXS7ROOM" 0
	count s7-restore.txt "FIXS7ALARM" 1
	count s7-restore.txt "FIXS7DELAY" 1

	# S9: a behaviour's own state: a hired sellsword's contract
	play s9-play.txt vdb vworld "Hirer\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s9-play.js\nsave s9\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s9-play.txt
	play s9-restore.txt vdb vworld 'jrun /resources/_fixes/s9-check.js\nquit\nn\n' RESTORE=s9 TIMEMODE=TURNS
	expect s9-restore.txt "Restored 's9'."
	jsresults s9-restore.txt

	# S12: an area's law: a warrant and a fine, put back once the player is
	# back, through the area's STATE
	play s12-build.txt vdb vworld "Builder\njrun /resources/_fixes/s12-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s12-build.txt
	play s12-play.txt vdb vworld "Felon\n${NEWCHAR}get book\nread book\nwait\njrun /resources/_fixes/s12-play.js\nsave s12\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s12-play.txt
	play s12-restore.txt vdb vworld 'wait 3\njrun /resources/_fixes/s12-check.js\nquit\nn\n' RESTORE=s12 TIMEMODE=TURNS
	expect s12-restore.txt "Restored 's12'."
	jsresults s12-restore.txt

	# S8: a quest made in play (as RandomQuests makes one) is saved whole and
	# made again
	play s8-play.txt vdb vworld "Questmaker\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s8-play.js\nsave s8\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s8-play.txt
	play s8-restore.txt vdb vworld 'jrun /resources/_fixes/s8-check.js\nquit\nn\n' RESTORE=s8 TIMEMODE=TURNS
	expect s8-restore.txt "Restored 's8'."
	jsresults s8-restore.txt
}

# the groups share nothing (each has its own world, database and saves), so
# with no tests named they run side by side; each writes its own log, and a
# FAIL in any fails the run
if [ $# -eq 0 ]; then
	group_jrun > "$W/group_jrun.log" 2>&1 &
	J_PID=$!
	group_st1 > "$W/group_st1.log" 2>&1 &
	S_PID=$!
	group_pr5 > "$W/group_pr5.log" 2>&1 &
	P_PID=$!
	group_saves > "$W/group_saves.log" 2>&1 &
	V_PID=$!
	group_saves2 > "$W/group_saves2.log" 2>&1 &
	V2_PID=$!
	wait $J_PID; wait $S_PID; wait $P_PID; wait $V_PID; wait $V2_PID
	cat "$W/group_jrun.log" "$W/group_st1.log" "$W/group_pr5.log" "$W/group_saves.log" "$W/group_saves2.log"
	if grep -q "^  FAIL" "$W/group_jrun.log" "$W/group_st1.log" "$W/group_pr5.log" "$W/group_saves.log" "$W/group_saves2.log"; then FAIL=1; fi
elif [ "$1" = "saves" ]; then
	group_saves > "$W/group_saves.log" 2>&1 &
	V_PID=$!
	group_saves2 > "$W/group_saves2.log" 2>&1 &
	V2_PID=$!
	wait $V_PID; wait $V2_PID
	cat "$W/group_saves.log" "$W/group_saves2.log"
	if grep -q "^  FAIL" "$W/group_saves.log" "$W/group_saves2.log"; then FAIL=1; fi
else
	group_jrun > "$W/group_jrun.log" 2>&1
	cat "$W/group_jrun.log"
	if grep -q "^  FAIL" "$W/group_jrun.log"; then FAIL=1; fi
fi

if [ "$FAIL" = 0 ]; then
	echo "PASS"
	[ -n "$KEEP" ] && echo "kept: $W" || rm -rf "$W"
	exit 0
fi
echo "FAILED.  Output kept in $W"
exit 1
