#!/bin/sh
# Boot the stock CoffeeMud server against a throwaway copy of the database,
# with outbound and web services off.  Usage: mudserver.sh WORKDIR PORT
# Prints the server PID once "Initialization complete" appears.
W="$1"; PORT="${2:-5599}"
ROOT="$(cd "$(dirname "$0")/../CoffeeMud" && pwd)"
rm -rf "$W/db" && mkdir -p "$W" && cp -r "$ROOT/resources/fakedb" "$W/db"
cd "$ROOT" || exit 1
java -classpath ".:./lib/js.jar:./lib/jzlib.jar" -Djava.awt.headless=true -Xmx1g \
	com.planet_ink.coffee_mud.application.MUD SmokeMud \
	"DBSERVICE=jdbc:fakedb:$W/db" PORT=$PORT RUNWEBSERVERS=false RUNSMTPSERVER=FALSE \
	RUNI3SERVER=FALSE RUNCM1SERVER=FALSE RUNIMC2CLIENT=FALSE \
	"DISABLE=DBERRORQUESTART,COMMAND_POSE,QUITREASON,RETIREREASON,CONNSPAMBLOCK,MAXCONNSPERIP,MAXNEWPERIP" \
	> "$W/server.out" 2>&1 &
PID=$!
for i in $(seq 1 120); do
	grep -q "Initialization complete" "$W/server.out" 2>/dev/null && { echo $PID; exit 0; }
	kill -0 $PID 2>/dev/null || break
	sleep 1
done
echo "server failed to start; see $W/server.out" >&2
exit 1
