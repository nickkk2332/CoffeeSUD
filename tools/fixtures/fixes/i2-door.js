// I2: MPDOOR OPEN|CLOSE|LOCK|UNLOCK|TOGGLE room dir|container [QUIET|"message"]: both sides, a message in
// both rooms, the relock timer; DOORSTATE(room dir); JavaScript's door() through it.
FIXFILE = "i2-door";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixi2Crypt");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi2Crypt#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var HALL = room(0, "Fixi2 hall"), CRYPT = room(1, "Fixi2 crypt"), CELLAR = room(2, "Fixi2 cellar");
function door(name) { var e = CMClass.getExit("GenExit"); e.setName(name); e.setDoorsNLocks(true, false, true, true, true, true); return e; }
var D1 = door("a stone door"), D2 = door("a stone door");
HALL.rawDoors()[Dir.NORTH] = CRYPT; HALL.setRawExit(Dir.NORTH, D1);
CRYPT.rawDoors()[Dir.SOUTH] = HALL; CRYPT.setRawExit(Dir.SOUTH, D2);
var chest = CMClass.getItem("GenContainer"); chest.setName("an iron chest"); chest.setDoorsNLocks(true, false, true, true, true, true); HALL.addItem(chest);
function watcher(name, R) {
	var w = CMClass.getMOB("GenMob"); w.setName(name); w.bringToLife(R, true); w.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var se = CMClass.getCommon("FakeSession"); se.initialize(null, "MEMORY"); w.setSession(se); se.setMob(w);
	return { m: w, se: se, mark: 0 };
}
function out(w) { var all = s(field(w.se, "bout").toString()); var o = all.substring(w.mark); w.mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var A = watcher("Fixi2ann", HALL), B = watcher("Fixi2bo", CRYPT);
// a lever in the cellar, far from both
var lever = CMClass.getMOB("GenMob"); lever.setName("Fixi2lever"); lever.bringToLife(CELLAR, true);
var ST = "mpsetvar FIXTEST FIXI2S $%DOORSTATE(Fixi2Crypt#0 north)%\n~\n";
var S = scriptOn(lever, "FUNCTION_PROG OPEN\nmpdoor open Fixi2Crypt#0 north \"With a grinding of stone the crypt door swings open.\"\n" + ST
	+ "FUNCTION_PROG CLOSE\nmpdoor close Fixi2Crypt#0 north QUIET\n" + ST
	+ "FUNCTION_PROG LOCK\nmpdoor lock Fixi2Crypt#0 north\n" + ST
	+ "FUNCTION_PROG TOGGLE\nmpdoor toggle Fixi2Crypt#0 north\n" + ST
	+ "FUNCTION_PROG CHEST\nmpdoor open Fixi2Crypt#0 \"iron chest\"\nmpsetvar FIXTEST FIXI2C $%DOORSTATE(Fixi2Crypt#0 iron chest)%\n~\n");
clearVar("FIXI2S"); clearVar("FIXI2C");
function go(f) { out(A); out(B); S.callFunction(lever, f, "", lever); return s(gvar("FIXI2S")); }
var st = go("OPEN");
var sa = out(A), sb = out(B);
check("open", D1.isOpen() && D2.isOpen() && (!D1.isLocked()) && (!D2.isLocked()) && st == "open", "near " + D1.isOpen() + " far " + D2.isOpen() + " state " + st);
check("both-rooms", has(sa, "With a grinding of stone the crypt door swings open.") && has(sb, "With a grinding of stone the crypt door swings open."), "hall '" + sa + "' crypt '" + sb + "'");
check("relock", CMLib.threads().isTicking(D1, Tickable.TICKID_EXIT_REOPEN), "no relock timer");
st = go("CLOSE");
sa = out(A); sb = out(B);
check("quiet", (!D1.isOpen()) && (!D2.isOpen()) && st == "closed" && s(sa).trim() == "" && s(sb).trim() == "", "open " + D1.isOpen() + " '" + sa + "' '" + sb + "'");
st = go("LOCK");
sa = out(A); sb = out(B);
check("lock", D1.isLocked() && D2.isLocked() && st == "locked" && has(sa, "stone door locks") && has(sb, "stone door locks"), "state " + st + " '" + sa + "'");
st = go("TOGGLE");
check("toggle", D1.isOpen() && D2.isOpen() && st == "open", "state " + st);
// a container, and the function on it
HALL.bringMobHere(lever, false);
S.callFunction(lever, "CHEST", "", lever);
check("container", chest.isOpen() && (!chest.isLocked()) && gvar("FIXI2C") == "open", "open " + chest.isOpen() + " state " + gvar("FIXI2C"));
// JavaScript's door(), both sides, quietly
var J = CM.core.Doors;
var why = J.change(CMLib.map().getRoom("Fixi2Crypt#1"), "south", "LOCK", "");
check("api", why == null && D1.isLocked() && D2.isLocked(), "why " + why);
check("help", has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPDOOR OPEN/CLOSE/LOCK/UNLOCK/TOGGLE")
	&& has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "DOORSTATE("), "no help");

[A.m, B.m].forEach(function (m) { m.setPlayerStats(null); m.destroy(); });
lever.destroy();
CMLib.map().obliterateMapArea(X);
done();
