// ST3: DISCOVER_PROG fires on a character's first visit to a room (ROOM)
// and to an area (AREA); with PARTY only for the first of the group there.
// $g is the room's ID.
FIXFILE = "st3-discover";
var R = M.location();
var PS = CM.Common.interfaces.PhyStats;
var A = CMClass.getAreaType("StdArea");
A.setName("FixST3 Area");
A.basePhyStats().setDisposition(A.basePhyStats().disposition() | PS.IS_UNSAVABLE);
A.recoverPhyStats();
CMLib.map().addArea(A);
var rooms = [];
for (var i = 0; i < 3; i++) {
	var X = CMClass.getLocale("StdRoom");
	X.setRoomID("FixST3 Area#" + i);
	X.setArea(A);
	X.setDisplayText("FixST3 room " + i);
	X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
	X.recoverPhyStats();
	rooms.push(X);
}
["FIXST3A", "FIXST3R", "FIXST3P", "FIXST3X"].forEach(clearVar);
var SA = scriptOn(A, "DISCOVER_PROG AREA\nmpsetvar FIXTEST FIXST3A $n:$G\n~\n");
var S0 = scriptOn(rooms[0], "DISCOVER_PROG ROOM -NAME +fixst3nobody\nmpsetvar FIXTEST FIXST3X $n\n~\nDISCOVER_PROG ROOM\nmpsetvar FIXTEST FIXST3R $n:$G\n~\n");
var S1 = scriptOn(rooms[1], "DISCOVER_PROG ROOM PARTY\nmpsetvar FIXTEST FIXST3P $n\n~\n");
function fakePlayer(name) {
	var P = newMob("StdMOB", R);
	P.setName(name);
	P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return P;
}
function enter(P, X) { X.send(P, CMClass.getMsg(P, X, null, CMMsg.MSG_ENTER, null)); }
function runAll() { runQueued(SA); runQueued(S0); runQueued(S1); }
var P = fakePlayer("Fixstthreelead");
var Q = fakePlayer("Fixstthreefollow");
Q.setFollowing(P);
check("group", P.getGroupMembers(new java.util.HashSet()).contains(Q), "the two are not a group");
check("area-new", !P.playerStats().hasVisited(A), "VISITABLE_ROOMS " + A.getIStat(CM.Areas.interfaces.Area.Stats.VISITABLE_ROOMS));

// the leader's first room in the area: ROOM and AREA
enter(P, rooms[0]);
runAll();
check("room", gvar("FIXST3R") == "Fixstthreelead:FixST3 Area#0", "FIXST3R='" + gvar("FIXST3R") + "'");
check("area", gvar("FIXST3A") == "Fixstthreelead:FixST3 Area#0", "FIXST3A='" + gvar("FIXST3A") + "'");
check("mask", gvar("FIXST3X") == "", "FIXST3X='" + gvar("FIXST3X") + "'");
// again: nothing
["FIXST3A", "FIXST3R"].forEach(clearVar);
enter(P, rooms[0]);
runAll();
check("second-visit", gvar("FIXST3R") == "" && gvar("FIXST3A") == "", "FIXST3R='" + gvar("FIXST3R") + "', FIXST3A='" + gvar("FIXST3A") + "'");
// each member counts on their own
enter(Q, rooms[0]);
runAll();
check("member-room", gvar("FIXST3R") == "Fixstthreefollow:FixST3 Area#0" && gvar("FIXST3A") == "Fixstthreefollow:FixST3 Area#0", "FIXST3R='" + gvar("FIXST3R") + "', FIXST3A='" + gvar("FIXST3A") + "'");
// a second room of the area: no AREA; PARTY fires for the first of the group only
clearVar("FIXST3A");
enter(P, rooms[1]);
runAll();
check("party-first", gvar("FIXST3P") == "Fixstthreelead" && gvar("FIXST3A") == "", "FIXST3P='" + gvar("FIXST3P") + "', FIXST3A='" + gvar("FIXST3A") + "'");
clearVar("FIXST3P");
enter(Q, rooms[1]);
runAll();
check("party-once", gvar("FIXST3P") == "" && Q.playerStats().hasVisited(rooms[1]), "FIXST3P='" + gvar("FIXST3P") + "'");

Q["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](null);
P.setPlayerStats(null); Q.setPlayerStats(null);
P.destroy(); Q.destroy();
CMLib.map().obliterateMapArea(A);
done();
