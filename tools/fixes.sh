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
#   tools/fixes.sh saves3     run one group of them (saves1 to saves5)
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

# a played group by name (st1, st6, pr5, saves1 to saves5) runs alone, without the JavaScript tests
case "$1" in st1|st6|pr5|s10|travel|presentation|language|review|remaining|modules|composition|combat|trial|itemcraft|saves|saves1|saves2|saves3|saves4|saves5) ONLYSAVES=1;; *) ONLYSAVES="";; esac
if [ -n "$ONLYSAVES" ]; then TESTS=""; elif [ $# -gt 0 ]; then TESTS="$*"; else
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

group_st6()
{
	SAVES="$W/gsaves"
	echo "Played: ST6, the world's scripts and world/js/init.js hear a new game, a new character, saves, turns and a restore"
	mkdir -p "$W/gdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/gdb/"
	cp -r "$GAME/world" "$W/gworld"
	mkdir -p "$W/gworld/scripts" "$W/gworld/js"
	cp "$FX/setup/st6-world.script" "$FX/setup/st6-refuse.script" "$W/gworld/scripts/"
	cp "$FX/setup/st6-init.js" "$W/gworld/js/init.js"
	play st6-new.txt gdb gworld 'Gamer\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\nlook\nwait\nwait\nsave refuse\nsave jsrefuse\nsave st6\nquit\nn\n' RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect st6-new.txt "FIXGAME new game for Gamer"
	expect st6-new.txt "FIXGAME new character Gamer"
	expect st6-new.txt "FIXJS new game, booted yes"
	expect st6-new.txt "FIXJS enter Gamer"
	expect st6-new.txt "FIXTURN 2"
	expect st6-new.txt "FIXGAME the world will not let you save here"
	count st6-new.txt "The game was not saved." 2
	expect st6-new.txt "FIXGAME saving st6"
	play st6-restore.txt gdb gworld 'look\nwait\nwait\nquit\nn\n' RESTORE=st6 TIMEMODE=TURNS
	expect st6-restore.txt "Restored 'st6'."
	expect st6-restore.txt "FIXGAME restored st6 at turn"
	expect st6-restore.txt "FIXJS restored st6"
	expect st6-restore.txt "FIXTURN 4"
	count st6-restore.txt "FIXGAME new game" 0
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
	# PR21: a window's side panel, on a virtual display, in builder mode (its
	# folders are its own: the groups run side by side in one folder)
	echo "Played: PR21, the panel beside a character window's text"
	if command -v xvfb-run > /dev/null 2>&1; then
		mkdir -p "$W/paneldb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/paneldb/"
		cp -r "$GAME/world" "$W/panelworld"
		mkdir -p "$W/panelworld/text"
		printf 'FIXPANEL %%r\nChapter %%{VAR FIXCHAPTER}\nHP %%h/%%H\n' > "$W/panelworld/text/panel.txt"
		printf 'Paneler\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\njrun /resources/_fixes/pr21-panel.js\nquit\nY\n' > "$W/pr21.script"
		JAVA_TOOL_OPTIONS="$(echo "$JAVA_TOOL_OPTIONS" | sed 's/-Djava.awt.headless=true//')" xvfb-run -a python3 "$ROOT/tools/sudplay.py" "$W/pr21.script" "DBSERVICE=jdbc:fakedb:$W/paneldb" "WORLDDIR=$W/panelworld" "SAVEDIR=$W/panelsaves" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS > "$W/pr21.txt" 2>&1
		jsresults pr21.txt
	else
		echo "  FAIL  pr21: no virtual display here (xvfb-run) to open a window on"; FAIL=1
	fi
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
	# S25: the export gives a room's own text as XML, one element to a line
	if grep -q '<RTEXT xml="1">' "$W/sworld/areas/"*.cmare && ! grep -q '<RTEXT>&lt;' "$W/sworld/areas/"*.cmare; then
		echo "  ok    s-build: the area files have readable room texts"
	else
		echo "  FAIL  s-build: the area files have escaped room texts"; FAIL=1
	fi

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


	# the cache gap: a builder's change made by JRUN, without the database,
	# is in the next game as well as in the world files
	play s10-gap.txt sdb sworld "Builder\njrun /resources/_fixes/s10-gap.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s10-gap.txt
	play s10-next.txt sdb sworld "Gapper\n${NEWCHAR}north\nlook\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s10-next.txt "FIXGAP the walls are new."

	# S25: a room's script that compares with < is kept in the area's file as
	# CDATA, as it is (read back below, in a game built from the files)
	play s25-script.txt sdb sworld "Builder\njrun /resources/_fixes/s25-script.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s25-script.txt
	if grep -q 'if var($n FIXCDATA < 5)' "$W/sworld/areas/"*.cmare && grep -q '<!\[CDATA\[GREET_PROG' "$W/sworld/areas/"*.cmare; then
		echo "  ok    s25-script: the area's file has the script as CDATA, as it is"
	else
		echo "  FAIL  s25-script: the area's file has not the script as CDATA"; FAIL=1
	fi

	# S11: a custom ability goes to a file of its own, its script of several
	# lines beside it as it is, and a game built from the files has it so
	play s11-build.txt sdb sworld "Builder\njrun /resources/_fixes/s11-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s11-build.txt
	if grep -q "FILE:spell-fixbrand.script" "$W/sworld/custom/abilities/spell-fixbrand.cmare" 2>/dev/null \
	&& grep -q "if var(\$n FIXHEAT < 5)" "$W/sworld/custom/abilities/spell-fixbrand.script" && [ ! -f "$W/sworld/custom.cmare" ]; then
		echo "  ok    s11-build: the ability has its own file, its script beside it as it is"
	else
		echo "  FAIL  s11-build: the ability's files are not as they should be"; FAIL=1
	fi
	mkdir -p "$W/s11db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/s11db/"
	play s11-new.txt s11db sworld "Brander\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s11-check.js\njrun /resources/_fixes/s25-cdata.js\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s11-new.txt "rebuilding the world from them"
	jsresults s11-new.txt

	# S25: a builder session that changes nothing writes the readable files
	# again as they were, so they read back to the very same world (the
	# file system's files are left out: the engine writes some of them again,
	# in another order, such as the tech manufacturers; and the backups and
	# undo log builder mode keeps beside them, B6)
	(cd "$W/sworld" && find . -type f -not -path './vfs/*' -not -path './.backup/*' -not -path './.history/*' | sort | xargs cksum) > "$W/sworld.sums"
	play s25-again.txt sdb sworld "Builder\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	if (cd "$W/sworld" && find . -type f -not -path './vfs/*' -not -path './.backup/*' -not -path './.history/*' | sort | xargs cksum | cmp -s - "$W/sworld.sums"); then
		echo "  ok    s25-again: the world files read back and write out the same"
	else
		echo "  FAIL  s25-again: the world files changed on a builder session that changed nothing"; FAIL=1
		(cd "$W/sworld" && find . -type f -not -path './vfs/*' | sort | xargs cksum) | diff "$W/sworld.sums" - | head -5
	fi
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


	# S24: things in space and in no room: in the world's files from builder
	# mode, put in space at each boot, and in a save where they were
	play s24-build.txt vdb vworld "Builder\njrun /resources/_fixes/s24-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s24-build.txt
	if grep -q "a derelict station" "$W/vworld/space.cmare" 2>/dev/null && grep -q "a patrol cutter" "$W/vworld/space.cmare"; then
		echo "  ok    s24-build: space.cmare has the station and the cutter"
	else
		echo "  FAIL  s24-build: space.cmare lacks the station or the cutter"; FAIL=1
	fi
	play s24-play.txt vdb vworld "Spacer\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s24-where.js\nwait 10\njrun /resources/_fixes/s24-where.js\nsave s24\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s24-play.txt
	play s24-restore.txt vdb vworld 'jrun /resources/_fixes/s24-where.js\nquit\nn\n' RESTORE=s24 TIMEMODE=TURNS
	expect s24-restore.txt "Restored 's24'."
	jsresults s24-restore.txt
	saved="$(grep -ao 'FIXINFO space.*' "$W/s24-play.txt" | tail -1)"
	back="$(grep -ao 'FIXINFO space.*' "$W/s24-restore.txt" | head -1)"
	if [ -n "$saved" ] && [ "$saved" = "$back" ]; then
		echo "  ok    s24: space is as it was saved"
	else
		echo "  FAIL  s24: space was '$saved', and is '$back'"; FAIL=1
	fi
}

# The world-content round trip can also run alone while developing it.
run_s10()
{
	# S10: templates, a clan with no players, a notice board's posts and the
	# database's files go to the world files, and a new game has them
	play s10-build.txt wdb wworld "Builder\n${NEWCHAR}jrun /resources/_fixes/s10-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s10-build.txt
	if [ -f "$W/wworld/templates.cmare" ] && grep -q "FIXCLAN" "$W/wworld/clans.cmare" \
	&& grep -q "FIXPOST" "$W/wworld/journals/"*.xml && grep -q "FIXVFS" "$W/wworld/vfs/fixdir/fixvfs.txt"; then
		echo "  ok    s10-build: the world files have the template, clan, post and file"
	else
		echo "  FAIL  s10-build: the world files lack the template, clan, post or file"; FAIL=1
	fi
	mkdir -p "$W/wfreshdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/wfreshdb/"
	play s10-new.txt wfreshdb wworld "Elric\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s10-check.js\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s10-new.txt

}

group_s10()
{
	SAVES="$W/wsaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	mkdir -p "$W/wdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/wdb/"
	cp -r "$GAME/world" "$W/wworld"
	run_s10
}

group_saves3()
{
	SAVES="$W/wsaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	echo "Played: step 2b, what a save keeps (third group)"
	mkdir -p "$W/wdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/wdb/"
	cp -r "$GAME/world" "$W/wworld"
	# S8: a quest made in play (as RandomQuests makes one) is saved whole and
	# made again
	play s8-play.txt wdb wworld "Questmaker\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s8-play.js\nsave s8\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s8-play.txt
	play s8-restore.txt wdb wworld 'jrun /resources/_fixes/s8-check.js\nquit\nn\n' RESTORE=s8 TIMEMODE=TURNS
	expect s8-restore.txt "Restored 's8'."
	jsresults s8-restore.txt

	run_s10

	# S13: a save remembers the world it was made with: restoring it after the
	# world changed says what changed, and FRESH=CHANGED takes the changed
	# areas' rooms as the world has them now
	play s13-play.txt wdb wworld "Keeper\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s13-play.js\nsave s13\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s13-play.txt
	cp -r "$W/wworld" "$W/wworld.before"
	sed -i 's/<RDISP>Rocky Room</<RDISP>Mossy Ledge</' "$W/wworld/areas/new-area.cmare"
	# S25: tools/worlddiff.py says what changed, by object
	python3 "$ROOT/tools/worlddiff.py" "$W/wworld.before" "$W/wworld" > "$W/s25-diff.txt" 2>&1
	expect s25-diff.txt "New Area#1: title 'Rocky Room' -> 'Mossy Ledge'"
	count s25-diff.txt "New Area" 1
	play s13-old.txt wdb wworld 'north\nlook\nsaves\nquit\nn\n' RESTORE=s13 TIMEMODE=TURNS
	expect s13-old.txt "This save was made with another version of the world"
	expect s13-old.txt "Changed area file: new-area.cmare"
	expect s13-old.txt "Mossy Ledge"
	expect s13-old.txt "A fix sentinel stands guard here."
	expect s13-old.txt "(older world)"
	play s13-fresh.txt wdb wworld 'north\nlook\nquit\nn\n' RESTORE=s13 FRESH=CHANGED TIMEMODE=TURNS
	expect s13-fresh.txt "Mossy Ledge"
	count s13-fresh.txt "A fix sentinel stands guard here." 0

	# S23: the save tools: INFO, COPY, DIFF, DELETE, CHECKPOINT and ROLLBACK,
	# AUTOSAVE, and tools/saveinspect.py outside the game
	play s23-play.txt wdb wworld "Saver\n${NEWCHAR}save s23a\nnorth\nsave s23b\nsaves info s23b\nsaves copy s23b s23c\nsaves diff s23a s23b\ncheckpoint\nsaves delete s23c\ny\nwait 5\nquit\nn\n" AUTOSAVE=1 RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s23-play.txt "Saved game 's23b':"
	expect s23-play.txt "Character : Saver, level"
	expect s23-play.txt "Saved game 's23b' copied to 's23c'."
	expect s23-play.txt "From 's23a' to 's23b':"
	expect s23-play.txt "player room: New Area#0 -> New Area#1"
	expect s23-play.txt "Checkpoint saved as 'checkpoint1'."
	expect s23-play.txt "Saved game 's23c' deleted."
	expect s23-play.txt "[Autosaved as 'autosave1']"
	play s23-roll.txt wdb wworld 'rollback\ny\nlook\nquit\nn\n' RESTORE=s23b TIMEMODE=TURNS
	expect s23-roll.txt "Restoring 'checkpoint1'..."
	expect s23-roll.txt "Restored 'checkpoint1'."
	python3 "$ROOT/tools/saveinspect.py" "$SAVES/s23a" > "$W/s23-inspect.txt" 2>&1
	python3 "$ROOT/tools/saveinspect.py" "$SAVES/s23a" --hp 7 >> "$W/s23-inspect.txt" 2>&1
	python3 "$ROOT/tools/saveinspect.py" "$SAVES/s23a" >> "$W/s23-inspect.txt" 2>&1
	expect s23-inspect.txt "Character: Saver in New Area#0"
	expect s23-inspect.txt "hit points 7,"
	# D-SP-1 and D-SP-2 (docs audit): a save edited by saveinspect.py is still
	# whole (its .complete list is brought up to date), and a room added to the
	# world after the save keeps what the world puts in it
	play audit-save.txt wdb wworld "Auditor\n${NEWCHAR}get book\nread book\nsave auditsp\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	python3 "$ROOT/tools/saveinspect.py" "$SAVES/auditsp" --hp 7 > "$W/audit-inspect.txt" 2>&1
	expect audit-inspect.txt "hit points 7,"
	cp "$W/wworld/areas/new-area.cmare" "$W/new-area.before-audit"
	python3 -c 'import sys; p, r = sys.argv[1], open(sys.argv[2]).read(); s = open(p).read(); open(p, "w").write(s.replace(" </AROOMS>", r + " </AROOMS>", 1))' "$W/wworld/areas/new-area.cmare" "$FX/setup/audit-newroom.xml"
	play audit-restore.txt wdb wworld 'jrun /resources/_fixes/audit-restore.js\nquit\nn\n' RESTORE=auditsp TIMEMODE=TURNS
	expect audit-restore.txt "Restored 'auditsp'."
	expect audit-restore.txt "1 room is new since this save and is as the world has it now: New Area#7."
	count audit-restore.txt "is incomplete or damaged" 0
	jsresults audit-restore.txt
	cp "$W/new-area.before-audit" "$W/wworld/areas/new-area.cmare"

	# S22: a character's entry limits (InstanceArea's LIMIT) and planar
	# visits are saved, with the time they have left
	play s22-play.txt wdb wworld "Roamer\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s22-play.js\nsave s22\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s22-play.txt
	play s22-restore.txt wdb wworld 'jrun /resources/_fixes/s22-check.js\nquit\nn\n' RESTORE=s22 TIMEMODE=TURNS
	expect s22-restore.txt "Restored 's22'."
	jsresults s22-restore.txt

	# S6, the rest: a world's parser rules before the stock ones, and
	# #STOCK OFF; a recipe whose result is a catalog item; the editors of the
	# game's data files refused in a normal game; a quest's folder kept
	mkdir -p "$W/ldb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/ldb/"
	cp -r "$GAME/world" "$W/lworld"
	mkdir -p "$W/lworld/resources/skills" "$W/lworld/quests/fixq"
	cp "$FX/setup/s6l-parser.properties" "$W/lworld/resources/parser_en_TX.properties"
	cp "$FX/setup/s6l-carpentry.add" "$W/lworld/resources/skills/carpentry.txt.add"
	echo "notes the quest reads" > "$W/lworld/quests/fixq/notes.txt"
	play s6l-build.txt ldb lworld "Builder\n${NEWCHAR}jrun /resources/_fixes/s6l-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s6l-build.txt
	if [ -f "$W/lworld/quests/fixq/notes.txt" ] && grep -q "a fix totem" "$W/lworld/catalog.cmare" 2>/dev/null; then
		echo "  ok    s6l-build: the catalog has the totem, and the quest's folder is kept"
	else
		echo "  FAIL  s6l-build: the totem is not in the catalog, or the quest's folder is gone"; FAIL=1
	fi
	play s6l-play.txt ldb lworld "Lexer\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s6l-parse.js\njrun /resources/_fixes/s6l-carve.js\ncarpentry fix totem\nwait 20\njrun /resources/_fixes/s6l-made.js\ncreate social FIXSOC\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s6l-play.txt
	expect s6l-play.txt "The social editor is refused in a normal game"
	printf '#STOCK OFF\n' >> "$W/lworld/resources/parser_en_TX.properties"
	play s6l-off.txt ldb lworld "Lexed\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s6l-off.js\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s6l-off.txt
}

group_saves4()
{
	SAVES="$W/xsaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	echo "Played: step 2b, what a save keeps (fourth group)"
	mkdir -p "$W/xdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/xdb/"
	cp -r "$GAME/world" "$W/xworld"
	# S15: vessels save once, with their crew and cargo, a vessel the
	# character owns is not saved twice, and one under way flies on
	play s15-build.txt xdb xworld "Builder\n${NEWCHAR}jrun /resources/_fixes/s15-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s15-build.txt
	play s15-play.txt xdb xworld "Mariner\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s15-play.js\nsave s15\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s15-play.txt
	play s15-restore.txt xdb xworld 'jrun /resources/_fixes/s15-check.js\nwait 20\njrun /resources/_fixes/s15-moved.js\nquit\nn\n' RESTORE=s15 TIMEMODE=TURNS
	expect s15-restore.txt "Restored 's15'."
	count s15-restore.txt "is gone" 0
	jsresults s15-restore.txt

	# S16: a crop growing and a craft under way are saved with what they
	# work on, and finish after the restore
	play s16-play.txt xdb xworld "Grower\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s16-play.js\nfarm wheat\nwait 50\njrun /resources/_fixes/s16-planted.js\ncarpentry bowl\nwait 3\njrun /resources/_fixes/s16-part.js\nsave s16\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s16-play.txt
	expect s16-play.txt "Kept in part: Carpentry (helpingAbility) under way."
	play s16-restore.txt xdb xworld 'jrun /resources/_fixes/s16-check.js\nwait 60\njrun /resources/_fixes/s16-done.js\nquit\nn\n' RESTORE=s16 TIMEMODE=TURNS
	expect s16-restore.txt "Restored 's16'."
	expect s16-restore.txt "have grown here"
	jsresults s16-restore.txt

	# S21: cron jobs run on the game clock: as game time passes (WAIT 60,
	# four game minutes in about a second of real time, runs a two-minute
	# job), and on after a restore
	play s21-play.txt xdb xworld "Croner\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s21-play.js\njrun /resources/_fixes/s21-mark.js\nwait 60\nsave s21\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s21-play.txt
	if awk '/FIXMARK/{m=NR} /FIXCRON ran/{if(!c)c=NR} END{exit !(m && c && c>m)}' "$W/s21-play.txt"; then
		echo "  ok    s21-play: the job ran as game time passed"
	else
		echo "  FAIL  s21-play: the job did not run as game time passed"; FAIL=1
	fi
	play s21-restore.txt xdb xworld 'wait 60\nquit\nn\n' RESTORE=s21 TIMEMODE=TURNS
	expect s21-restore.txt "Restored 's21'."
	expect s21-restore.txt "FIXCRON ran"

	# DT3: a world file that loads only in part (an effect of an unknown
	# class, skipped) is reported, and builder mode does not write it short
	# until SAVE FORCE
	mkdir -p "$W/pbdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/pbdb/"
	cp -r "$GAME/world" "$W/pbworld"
	sed -i 's/Prop_Smell/Prop_Smelll/' "$W/pbworld/areas/new-area.cmare"
	play dt3-build.txt pbdb pbworld "Builder\n${NEWCHAR}world problems\nsave\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	expect dt3-build.txt "World file problems: 1 in 1 file, which builder mode"
	expect dt3-build.txt "areas/new-area.cmare: CoffeeMaker: Unknown affect Prop_Smelll"
	expect dt3-build.txt "These world files loaded only in part"
	expect dt3-build.txt "Not written, as they loaded only in part: areas/new-area.cmare."
	if grep -q "Prop_Smelll" "$W/pbworld/areas/new-area.cmare"; then
		echo "  ok    dt3-build: the file that loaded in part was not written short"
	else
		echo "  FAIL  dt3-build: the file that loaded in part was written without what it lost"; FAIL=1
	fi
	# the next game loads the cache, and still knows; SAVE FORCE writes the file
	play dt3-force.txt pbdb pbworld "Builder\nsave force\nworld problems\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	expect dt3-force.txt "World file problems: 1 in 1 file, which builder mode"
	expect dt3-force.txt "The world files loaded whole."
	if grep -q "Prop_Smelll" "$W/pbworld/areas/new-area.cmare"; then
		echo "  FAIL  dt3-force: SAVE FORCE did not write the file"; FAIL=1
	else
		echo "  ok    dt3-force: SAVE FORCE wrote the file as the world has it"
	fi

	# DT5: DEV=1, a normal game in which each character has the developer
	# group; saves are marked [dev], and a game without DEV=1 takes it away
	# (the book here, read, would make an Archon, so it is left alone)
	play dt5-dev.txt xdb xworld "Devver\n${NEWCHAR}vars set FIXDEV FIXD 7\njrun /resources/_fixes/dt5-check.js\nsave dt5\nsaves\nsaves info dt5\nquit\nn\n" DEV=1 RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect dt5-dev.txt "A developer's game (DEV=1)"
	expect dt5-dev.txt "you have the DEV security group"
	expect dt5-dev.txt "GLOBAL FIXDEV FIXD is '7'."
	jsresults dt5-dev.txt
	expect dt5-dev.txt "[dev]"
	expect dt5-dev.txt "made in a developer's game (DEV=1)"
	if grep -q "^dev=true" "$W/xsaves/dt5/header.properties" 2>/dev/null; then
		echo "  ok    dt5-dev: the save's header is marked dev"
	else
		echo "  FAIL  dt5-dev: the save's header is not marked dev"; FAIL=1
	fi
	play dt5-plain.txt xdb xworld 'vars set FIXDEV FIXD 8\nquit\nn\n' RESTORE=dt5 TIMEMODE=TURNS
	expect dt5-plain.txt "Restored 'dt5'."
	count dt5-plain.txt "FIXDEV FIXD is" 0

	# DT13: CHECK=1 loads the shipped world as a new game would, checks it
	# with no player, and ends (exit 1 on errors; the shipped world has none)
	mkdir -p "$W/ckdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/ckdb/"
	cp -r "$GAME/world" "$W/ckworld"
	SAVES="$W/cksaves" play dt13-check.txt ckdb ckworld '' CHECK=1
	expect dt13-check.txt "World check (CHECK=1)"
	expect dt13-check.txt "WORLD CHECK: 0 errors"

	# DT25: RECORDINPUT=1 writes what is typed to a playtest file, after a
	# header of the game it started from, and the runner plays it again
	mkdir -p "$W/rcdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/rcdb/"
	cp -r "$GAME/world" "$W/rcworld"
	RCCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	SAVES="$W/rcsaves" play dt25-rec.txt rcdb rcworld "Recorder\n${RCCHAR}look\nsay hello there\nrecord mark seen it\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS DEV=1 RECORDINPUT=1
	expect dt25-rec.txt "Recording what you type to"
	REC=$(ls "$W/rcsaves/recordings/"*.play 2>/dev/null | head -1)
	if [ -n "$REC" ] && grep -q "^look$" "$REC" && grep -q "^say hello there$" "$REC" && grep -q "^#seed 42$" "$REC" \
		&& grep -q "^#new$" "$REC" && grep -q "^#char Recorder,Human,Apprentice,M,good$" "$REC" && grep -q "^# MARK .*seen it" "$REC"; then
		echo "  ok    dt25-recorded: what was typed is in the playtest file, after its header"
		python3 "$ROOT/tools/playtest.py" "$REC" > "$W/dt25-replay.txt" 2>&1
		echo "exit $?" >> "$W/dt25-replay.txt"
		expect dt25-replay.txt "PASS  "
		expect dt25-replay.txt "exit 0"
	else
		echo "  FAIL  dt25-recorded: no playtest file, or not what was typed: $REC"; FAIL=1
	fi

	# DT27: world/dev areas: a DEV=1 game has them and starts at DEVSTART;
	# a normal game, on the same cache, does not
	mkdir -p "$W/dvdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/dvdb/"
	cp -r "$GAME/world" "$W/dvworld"
	mkdir -p "$W/dvworld/dev"
	sed 's/New Area/Fixes Dev Hub/g' "$W/dvworld/areas/new-area.cmare" > "$W/dvworld/dev/fixes-dev-hub.cmare"
	DVCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	SAVES="$W/dvsaves" play dt27-dev.txt dvdb dvworld "Devver\n${DVCHAR}assert here Fixes Dev Hub#0\nareas\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS DEV=1 "DEVSTART=Fixes Dev Hub#0"
	expect dt27-dev.txt "ASSERT ok: HERE Fixes Dev Hub#0"
	expect dt27-dev.txt "Fixes Dev Hub"
	SAVES="$W/dvsaves" play dt27-normal.txt dvdb dvworld "Plainer\n${DVCHAR}areas\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS "DEVSTART=Fixes Dev Hub#0"
	expect dt27-normal.txt "New Area"
	count dt27-normal.txt "Fixes Dev Hub" 0

	# DT15: playtest files: the world's own passes, and one made to fail
	# fails at each check, with the runner exiting 1
	python3 "$ROOT/tools/playtest.py" "$GAME/world/tests/start.play" > "$W/dt15-pass.txt" 2>&1
	echo "exit $?" >> "$W/dt15-pass.txt"
	expect dt15-pass.txt "PASS  "
	expect dt15-pass.txt "exit 0"
	python3 "$ROOT/tools/playtest.py" "$FX/setup/dt15-fail.play" > "$W/dt15-fail.txt" 2>&1
	echo "exit $?" >> "$W/dt15-fail.txt"
	expect dt15-fail.txt "line 3: expected 'This text is nowhere in the game'"
	expect dt15-fail.txt "line 4: 'Obvious exits' should not show"
	expect dt15-fail.txt "line 5: ASSERT FAIL: HERE Nowhere#99: the character is in "
	expect dt15-fail.txt "line 6: ASSERT FAIL: HAS a fixes unicorn: the character has no such thing"
	expect dt15-fail.txt "exit 1"
}

group_saves5()
{
	SAVES="$W/ysaves"
	NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
	echo "Played: step 2b, what a save keeps (fifth group)"
	mkdir -p "$W/ydb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/ydb/"
	cp -r "$GAME/world" "$W/yworld"
	# S17: rooms and areas changed in play: a room's texts, behaviour and
	# lasting effect, a room made in play and its contents, a room destroyed,
	# an area's description and climate, and a start room made in play
	play s17-build.txt ydb yworld "Builder\n${NEWCHAR}jrun /resources/_fixes/s17-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s17-build.txt
	play s17-play.txt ydb yworld "Shaper\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s17-play.js\nwait 3\nsave s17\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s17-play.txt
	play s17-restore.txt ydb yworld 'jrun /resources/_fixes/s17-check.js\nquit\nn\n' RESTORE=s17 TIMEMODE=TURNS
	expect s17-restore.txt "Restored 's17'."
	jsresults s17-restore.txt

	# S19 and S18: areas made in play survive a save: an instance (the
	# crypt's), with its rooms, its dead, its timer and its group, and the
	# player in it; and a generated dungeon (the mines'), the same after
	play s19-build.txt ydb yworld "Builder\njrun /resources/_fixes/s19-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s19-build.txt
	# S25: the crypt's file gives the lieutenant's own text as XML, and a
	# game on an empty database, built from the files, has the lieutenant
	if grep -q '<MTEXT xml="1">' "$W/yworld/areas/crypt.cmare" && ! grep -q '<MTEXT>&lt;' "$W/yworld/areas/crypt.cmare" \
	&& grep -q '<PROP ABILITY="' "$W/yworld/areas/crypt.cmare"; then
		echo "  ok    s19-build: the crypt's file has a readable mob text, its base stats named"
	else
		echo "  FAIL  s19-build: the crypt's file has an escaped mob text"; FAIL=1
	fi
	mkdir -p "$W/y2db" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/y2db/"
	play s25-fresh.txt y2db yworld "Fresher\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/s25-lich.js\ndown\nnorth\nlook\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	expect s25-fresh.txt "rebuilding the world from them"
	expect s25-fresh.txt "A lich lieutenant stands here."
	jsresults s25-fresh.txt
	play s19-play.txt ydb yworld "Raider\n${NEWCHAR}get book\nread book\ndown\nnorth\njrun /resources/_fixes/s19-play.js\nsave s19\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s19-play.txt
	# JRUN costs no action; wait for each queued move before inspecting location.
	play s19-restore.txt ydb yworld 'jrun /resources/_fixes/s19-check.js\nsouth\n#expect A crypt stair\nup\n#expect New Room\ndown\n#expect A crypt stair\njrun /resources/_fixes/s19-again.js\nquit\nn\n' RESTORE=s19 TIMEMODE=TURNS
	expect s19-restore.txt "Restored 's19'."
	jsresults s19-restore.txt
	play s18-play.txt ydb yworld "Miner\n${NEWCHAR}get book\nread book\nwest\njrun /resources/_fixes/s18-kill.js\njrun /resources/_fixes/s18-sig.js\nsave s18\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s18-play.txt
	play s18-restore.txt ydb yworld 'jrun /resources/_fixes/s18-sig.js\neast\nwest\njrun /resources/_fixes/s18-again.js\nquit\nn\n' RESTORE=s18 TIMEMODE=TURNS
	expect s18-restore.txt "Restored 's18'."
	jsresults s18-restore.txt
	# PERSIST=true: the vault, left empty for long past its PASSIVEMINS,
	# is kept for its group, and the area's file keeps PASSIVEMINS
	if grep -q "PASSIVEMINS" "$W/yworld/areas/vault.cmare" 2>/dev/null; then
		echo "  ok    s19-build: the vault's file keeps PASSIVEMINS"
	else
		echo "  FAIL  s19-build: the vault's file lost PASSIVEMINS"; FAIL=1
	fi
	play s18-persist.txt ydb yworld "Vaulter\n${NEWCHAR}get book\nread book\nsouth\nnorth\nwait 100\nwait 100\nwait 100\nsouth\njrun /resources/_fixes/s18-kept.js\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
	jsresults s18-persist.txt
	before="$(grep -ao 'FIXINFO mines.*' "$W/s18-play.txt" | head -1)"
	after="$(grep -ao 'FIXINFO mines.*' "$W/s18-restore.txt" | head -1)"
	if [ -n "$before" ] && [ "$before" = "$after" ]; then
		echo "  ok    s18: the mines are the same after the restore"
	else
		echo "  FAIL  s18: the mines were '$before', and are '$after'"; FAIL=1
	fi
}

group_travel()
{
    SAVES="$W/travelsaves"
    NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
    echo "Played: travel, a paused journey, same-name mounts, sister stables, waypoints and a mid-shaft lift"
    mkdir -p "$W/traveldb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/traveldb/"
    cp -r "$GAME/world" "$W/travelworld"
    play travel-build.txt traveldb travelworld "Builder\n${NEWCHAR}jrun /resources/_fixes/travel-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
    jsresults travel-build.txt
    play travel-play.txt traveldb travelworld "Traveller\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/travel-play.js\n#sleep 3\njrun /resources/_fixes/travel-paused.js\nsave travel\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
    jsresults travel-play.txt
    expect travel-play.txt "Game saved as 'travel'."
    play travel-checkpoint.txt traveldb travelworld 'jrun /resources/_fixes/travel-check.js\nsave travelagain\nquit\nn\n' RESTORE=travel TIMEMODE=TURNS
    jsresults travel-checkpoint.txt
    expect travel-checkpoint.txt "Game saved as 'travelagain'."
    play travel-restore.txt traveldb travelworld 'jrun /resources/_fixes/travel-check.js\njourney resume\n#sleep 3\njrun /resources/_fixes/travel-final-scene.js\njourney resume\njrun /resources/_fixes/travel-arrived.js\nquit\nn\n' RESTORE=travelagain TIMEMODE=TURNS
    expect travel-restore.txt "Restored 'travelagain'."
    jsresults travel-restore.txt
}

group_presentation()
{
    SAVES="$W/presentationsaves"
    NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
    echo "Played: presentation, first visits, asynchronous narration and saved lore, journal, maps and preferences"
    mkdir -p "$W/presentationdb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/presentationdb/"
    cp -r "$GAME/world" "$W/presentationworld"
    play pr-build.txt presentationdb presentationworld "Builder\n${NEWCHAR}jrun /resources/_fixes/pr-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
    jsresults pr-build.txt
    play pr-play.txt presentationdb presentationworld "Reader\n${NEWCHAR}get book\nread book\njrun /resources/_fixes/pr-play.js\neast\nwest\neast\nwest\nlore SAVEDENTRY\njrun /resources/_fixes/pr-narrate.js\n\n\njrun /resources/_fixes/pr-after-pages.js\nsave presentation\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS COLORALIASES=LORE=LIGHTRED
    jsresults pr-play.txt
    expect pr-play.txt "FIX_FIRST_VISIT"
    count pr-play.txt "FIX_AREA_FIRST" 1
    expect pr-play.txt "FIX_RETURN_VISIT"
    count pr-play.txt "FIX_ONCE_ENTRY" 1
    expect pr-play.txt "FIX_NARRATE_ONE"
    expect pr-play.txt "FIX_NARRATE_TWO"
    expect pr-play.txt "FIX_NARRATE_THREE"
    expect pr-play.txt "Game saved as 'presentation'."
    play pr-restore.txt presentationdb presentationworld 'jrun /resources/_fixes/pr-check.js\neast\nwest\nquit\nn\n' RESTORE=presentation TIMEMODE=TURNS COLORALIASES=LORE=LIGHTRED
    jsresults pr-restore.txt
    play pr-real.txt presentationdb presentationworld 'jrun /resources/_fixes/pr-narrate-real.js\n#sleep 2\n\n\njrun /resources/_fixes/pr-after-real.js\nquit\nn\n' RESTORE=presentation TIMEMODE=TURNS COLORALIASES=LORE=LIGHTRED
    jsresults pr-real.txt
    count pr-restore.txt "FIX_ONCE_ENTRY" 0
    expect pr-restore.txt "FIX_RETURN_VISIT"
}

group_language()
{
    SAVES="$W/languagesaves"
    NEWCHAR='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
    echo "Played: language dictionaries, custom fields, learned proficiency and pregrant immersion survive saves"
    mkdir -p "$W/languagedb" && cp "$GAME/resources/fakedb/fakedb.schema" "$W/languagedb/"
    cp -r "$GAME/world" "$W/languageworld"
    play l-build.txt languagedb languageworld "Builder\n${NEWCHAR}jrun /resources/_fixes/l-build.js\nquit\nY\n" BUILDER=1 RANDOMSEED=42 TIMEMODE=TURNS
    jsresults l-build.txt
    play l-play.txt languagedb languageworld "Linguist\n${NEWCHAR}jrun /resources/_fixes/l-play.js\nsave language\nquit\nn\n" RESTORE=NEW RANDOMSEED=42 TIMEMODE=TURNS
    jsresults l-play.txt
    expect l-play.txt "Game saved as 'language'."
    play l-restore.txt languagedb languageworld 'jrun /resources/_fixes/l-check.js\nquit\nn\n' RESTORE=language TIMEMODE=TURNS
    expect l-restore.txt "Restored 'language'."
    jsresults l-restore.txt
}

group_remaining()
{
 echo "Played: remaining authoring workflows"
 python3 "$ROOT/tools/remaining-integration.py" "$W/remaining" > "$W/remaining.txt" 2>&1 || FAIL=1
 jsresults remaining.txt
}

group_review()
{
    echo "Played: project review, isolated campaigns, combat/identity and pending interactions"
    python3 "$ROOT/tools/review-integration.py" "$W/review" > "$W/review.txt" 2>&1 || FAIL=1
    jsresults review.txt
}

group_modules()
{
    echo "Played: optional modules, scoped state, migrations and safe retirement"
    python3 "$ROOT/tools/modules-integration.py" "$W/modules" > "$W/modules.txt" 2>&1 || FAIL=1
    jsresults modules.txt
}

group_composition()
{
    echo "Played: composed skills, supports, cooldowns and timed-effect restore"
    python3 "$ROOT/tools/composition-integration.py" "$W/composition" > "$W/composition.txt" 2>&1 || FAIL=1
    jsresults composition.txt
}

group_combat()
{
    echo "Played: combat profiles, resolved results, equipment and pinned-burn restore"
    python3 "$ROOT/tools/combat-integration.py" "$W/combat" > "$W/combat.txt" 2>&1 || FAIL=1
    jsresults combat.txt
}

group_trial()
{
    echo "Played: Ember Trial sample publication, mortal combat, completion and fresh-process persistence"
    python3 "$ROOT/docs/creator/examples/ember-trial/check.py" > "$W/trial.txt" 2>&1 || FAIL=1
    jsresults trial.txt
}

group_itemcraft()
{
    echo "Played: generated modifier records, workbench transactions and saved settlement"
    python3 "$ROOT/tools/item-modifier-integration.py" "$W/itemcraft" > "$W/itemcraft.txt" 2>&1 || FAIL=1
    jsresults itemcraft.txt
}

# the groups share nothing (each has its own world, database and saves), so
# with no tests named they run side by side; each writes its own log, and a
# FAIL in any fails the run
if [ $# -eq 0 ]; then
	# Limit simultaneous game instances on small CI machines. The default
	# retains the existing parallel run; FIXES_JOBS=2 runs pairs of groups.
	LIMIT=${FIXES_JOBS:-9}
	case "$LIMIT" in ''|*[!0-9]*|0) LIMIT=9;; esac
	PIDS=""; RUNNING=0
	for GROUP in jrun st1 st6 pr5 saves saves2 saves3 saves4 saves5 travel presentation language review remaining modules composition combat trial itemcraft; do
		"group_$GROUP" > "$W/group_$GROUP.log" 2>&1 &
		PIDS="$PIDS $!"; RUNNING=$((RUNNING+1))
		if [ "$RUNNING" -ge "$LIMIT" ]; then
			for PID in $PIDS; do wait "$PID"; done
			PIDS=""; RUNNING=0
		fi
	done
	for PID in $PIDS; do wait "$PID"; done
	cat "$W/group_jrun.log" "$W/group_st1.log" "$W/group_st6.log" "$W/group_pr5.log" "$W/group_saves.log" "$W/group_saves2.log" "$W/group_saves3.log" "$W/group_saves4.log" "$W/group_saves5.log" "$W/group_travel.log" "$W/group_presentation.log" "$W/group_language.log" "$W/group_review.log" "$W/group_remaining.log" "$W/group_modules.log" "$W/group_composition.log" "$W/group_combat.log" "$W/group_trial.log" "$W/group_itemcraft.log"
	if grep -q "^  FAIL" "$W/group_jrun.log" "$W/group_st1.log" "$W/group_st6.log" "$W/group_pr5.log" "$W/group_saves.log" "$W/group_saves2.log" "$W/group_saves3.log" "$W/group_saves4.log" "$W/group_saves5.log" "$W/group_travel.log" "$W/group_presentation.log" "$W/group_language.log" "$W/group_review.log" "$W/group_remaining.log" "$W/group_modules.log" "$W/group_composition.log" "$W/group_combat.log" "$W/group_trial.log" "$W/group_itemcraft.log"; then FAIL=1; fi
elif [ "$1" = "saves" ]; then
	group_saves > "$W/group_saves.log" 2>&1 &
	V_PID=$!
	group_saves2 > "$W/group_saves2.log" 2>&1 &
	V2_PID=$!
	group_saves3 > "$W/group_saves3.log" 2>&1 &
	V3_PID=$!
	group_saves4 > "$W/group_saves4.log" 2>&1 &
	V4_PID=$!
	group_saves5 > "$W/group_saves5.log" 2>&1 &
	V5_PID=$!
	wait $V_PID; wait $V2_PID; wait $V3_PID; wait $V4_PID; wait $V5_PID
	cat "$W/group_saves.log" "$W/group_saves2.log" "$W/group_saves3.log" "$W/group_saves4.log" "$W/group_saves5.log"
	if grep -q "^  FAIL" "$W/group_saves.log" "$W/group_saves2.log" "$W/group_saves3.log" "$W/group_saves4.log" "$W/group_saves5.log"; then FAIL=1; fi
elif [ -n "$ONLYSAVES" ]; then
	G="group_$1"; [ "$1" = "saves1" ] && G=group_saves
	$G > "$W/$G.log" 2>&1
	cat "$W/$G.log"
	if grep -q "^  FAIL" "$W/$G.log"; then FAIL=1; fi
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
