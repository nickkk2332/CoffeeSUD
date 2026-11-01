#!/bin/sh
# Runs playtest files (DT15): tools/playtest.sh [FILE.play ...], world/tests/*.play by default.
# See tools/playtest.py for the file format.  Exits 1 if any fails.
exec python3 "$(dirname "$0")/playtest.py" "$@"
