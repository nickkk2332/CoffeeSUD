#!/bin/sh
# Checks that Java files changed since a revision (HEAD by default) changed their layout only: each set, as at the
# revision and as it is now, is compiled without debug information, and the class files must be byte for byte the
# same.  Usage: tools/javafmt-verify.sh [revision] [file.java ...]   (files default to those changed since it)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REV="${1:-HEAD}"; [ $# -gt 0 ] && shift
cd "$ROOT" || exit 1
FILES="$*"
[ -n "$FILES" ] || FILES="$(git diff --name-only "$REV" -- 'CoffeeMud/*.java')"
[ -n "$FILES" ] || { echo "No Java files changed since $REV."; exit 0; }
W="$(mktemp -d "${TMPDIR:-/tmp}/javafmt.XXXXXX")"
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/before" "$W/now" "$W/src" "$W/none"
LIST=""
for F in $FILES; do
	R="${F#CoffeeMud/}"
	mkdir -p "$W/src/$(dirname "$R")"
	git show "$REV:$F" > "$W/src/$R" || exit 1
	LIST="$LIST $R"
done
CP="$ROOT/CoffeeMud:$ROOT/CoffeeMud/lib/js.jar"
OPTS="-nowarn -Xlint:none -g:none -encoding UTF-8 --release 8 -implicit:none -sourcepath $W/none -classpath $CP"
(cd "$W/src" && javac $OPTS -d "$W/before" $LIST) || exit 1
(cd "$ROOT/CoffeeMud" && javac $OPTS -d "$W/now" $LIST) || exit 1
(cd "$W/before" && find . -name '*.class' | sort) > "$W/before.txt"
(cd "$W/now" && find . -name '*.class' | sort) > "$W/now.txt"
if ! cmp -s "$W/before.txt" "$W/now.txt"; then echo "The class files differ in number or name."; exit 1; fi
BAD=0
for C in $(cat "$W/now.txt"); do cmp -s "$W/before/$C" "$W/now/$C" || { echo "differs: $C"; BAD=1; }; done
[ $BAD = 0 ] && echo "Layout only: $(echo $LIST | wc -w) files, $(wc -l < "$W/now.txt") class files the same as at $REV."
exit $BAD
