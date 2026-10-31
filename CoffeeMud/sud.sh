#!/bin/sh
# Play the single-player game in this terminal.  Extra NAME=VALUE arguments
# override coffeemud.ini.
cd "$(dirname "$0")" || exit 1
./build.sh > /dev/null || { echo "Build failed; run ./build.sh to see why." >&2; exit 1; }
exec java -classpath ".:./lib/js.jar:./lib/jzlib.jar" -Xmx1g \
	com.planet_ink.coffee_mud.application.SUD "$@"
