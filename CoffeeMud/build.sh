#!/bin/sh
# Compiles every source file in place. CMClass discovers game classes by
# scanning the package folders for .class files, so the output must sit
# next to the sources.
#   ./build.sh          rebuild if any source is newer than the last good build
#   ./build.sh clean    delete all .class files first
# When a source file is added or deleted the build starts clean, because a
# class file left behind by a deleted source would still be loaded.
cd "$(dirname "$0")" || exit 1
STAMP=.build-stamp
# a list per run, so that games launched side by side do not read each other's
LIST=".sources.$$.list"
find com -name '*.java' | sort > "$LIST"
LISTSUM=$(cksum < "$LIST")
if [ "$1" = "clean" ] || [ ! -f "$STAMP" ] || [ "$(cat "$STAMP")" != "$LISTSUM" ]; then
	find com -name '*.class' -delete
	rm -f "$STAMP"
elif [ -z "$(find com -name '*.java' -newer "$STAMP" -print -quit)" ]; then
	rm -f "$LIST"
	echo "Up to date."
	exit 0
fi
javac -nowarn -Xlint:none -g -encoding UTF-8 --release 8 -Xmaxerrs 1000 \
	-J-Xmx2g -classpath ".:./lib/js.jar:./lib/jzlib.jar" -d . @"$LIST" > .build.log 2>&1
STATUS=$?
rm -f "$LIST"
grep -v '^Picked up JAVA_TOOL_OPTIONS\|^warning: \[options\]\|^Note: \|^[0-9]* warnings\?$' .build.log
if [ $STATUS -eq 0 ]; then
	echo "$LISTSUM" > "$STAMP"
	echo "Build OK."
else
	echo "Build FAILED."
fi
exit $STATUS
