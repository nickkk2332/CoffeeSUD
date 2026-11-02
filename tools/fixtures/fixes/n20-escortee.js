// N20: Escortee: MODE=LEAD walks to DEST a step a tick, WAITFOR=LEADER waits and calls WAITMSG, TALK= remarks,
// ONARRIVE= with the leader as $n; MODE=FOLLOW with AVOID=; PANIC=COWER; ONDEATH=; TIMEOUT= and ONTIMEOUT=; its state.
FIXFILE = "n20-escortee";
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixn20Road");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
var rooms = [];
for (var i = 0; i < 4; i++) {
	var Q = CMClass.getLocale("StdRoom");
	Q.setRoomID("Fixn20Road#" + i);
	Q.setArea(X);
	Q.setDisplayText("Fixn20 road " + i);
	rooms.push(Q);
}
var EAST = CMLib.directions().getGoodDirectionCode("east"), WEST = CMLib.directions().getGoodDirectionCode("west");
function link(a, b) {
	a.rawDoors()[EAST] = b; a.setRawExit(EAST, CMClass.getExit("StdOpenDoorway"));
	b.rawDoors()[WEST] = a; b.setRawExit(WEST, CMClass.getExit("StdOpenDoorway"));
}
link(rooms[0], rooms[1]); link(rooms[1], rooms[2]); link(rooms[2], rooms[3]);
function where(m) { var R = m.location(); return R == null ? "nowhere" : s(R.roomID()).replace("Fixn20Road#", ""); }
function watcher(at) {
	var w = newMob("GenMob", rooms[at]);
	w.setName("Fixn20watcher" + at);
	var S = CMClass.getCommon("FakeSession"); S.initialize(null, "MEMORY"); w.setSession(S); S.setMob(w);
	return { mob: w, s: S };
}
function heard(w) { return s(field(w.s, "bout").toString()).replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var w1 = watcher(1), w2 = watcher(2);
var pip = newMob("GenMob", rooms[0]);
pip.setName("Fixn20pip");
pip.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function follow(m, who) { m["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](who); }
function tick(m, B) { B.tick(m, Tickable.TICKID_MOB); }
VARS.setVar("Fixn20pip", "FIXN20ESC", "");
["FIXN20DEAD", "FIXN20LATE"].forEach(clearVar);

// leading the way
var m = newMob("GenMob", rooms[0]);
m.setName("Fixn20merchant");
var E = CMClass.getBehavior("Escortee");
E.setParms("DEST=Fixn20Road#3 MODE=LEAD WAITFOR=LEADER WAITCALL=2 WAITMSG=\"Fixn20 wait for me!\" "
	+ "TALK=\"Fixn20Road#2:Fixn20 they say trolls live here.\" ONARRIVE=\"say Fixn20 thank you, friends!;mpsetvar $n FIXN20ESC 1\"");
m.addBehavior(E);
tick(m, E);
check("not-yet", where(m) == "0", "it went with no leader");
follow(m, pip);
tick(m, E);
check("leads", where(m) == "1" && m.amFollowing() == null, "at " + where(m));
tick(m, E);
check("waits", where(m) == "1" && !has(heard(w1), "Fixn20 wait for me!"), "at " + where(m));
tick(m, E);
check("calls", where(m) == "1" && has(heard(w1), "Fixn20 wait for me!"), "no call");
rooms[1].bringMobHere(pip, false);
tick(m, E);
check("goes-on", where(m) == "2", "at " + where(m));
tick(m, E);
check("remark", has(heard(w2), "Fixn20 they say trolls live here."), "no remark at the bridge");
rooms[2].bringMobHere(pip, false);
tick(m, E);
tick(m, E);
check("arrives", where(m) == "3" && s(VARS.getVar("Fixn20pip", "FIXN20ESC")) == "1", "at " + where(m) + " var '" + VARS.getVar("Fixn20pip", "FIXN20ESC") + "'");
var state = s(E.getStat("STATE"));
check("state", has(state, "LEADER=\"Fixn20pip\"") && has(state, "DONE=\"true\""), state);
var E2 = CMClass.getBehavior("Escortee");
E2.setParms(s(E.getParms()));
E2.setStat("STATE", state);
check("restored", s(E2.getStat("STATE")) == state, s(E2.getStat("STATE")));

// following, but not into a room it avoids; cowering when attacked; its death
rooms[1].bringMobHere(pip, false);
var g = newMob("GenMob", rooms[1]);
g.setName("Fixn20pilgrim");
var G = CMClass.getBehavior("Escortee");
G.setParms("DEST=Fixn20Road#3 AVOID=Fixn20Road#2 PANIC=COWER ONDEATH=\"mpsetvar FIXTEST FIXN20DEAD $n\"");
g.addBehavior(G);
follow(g, pip);
tick(g, G);
var refused = !g.okMessage(g, CMClass.getMsg(g, rooms[2], null, CMMsg.MSG_ENTER, null));
check("avoid", refused && g.okMessage(g, CMClass.getMsg(g, rooms[0], null, CMMsg.MSG_ENTER, null)), "it would go to the avoided room");
var foe = newMob("GenMob", rooms[1]);
foe.setName("Fixn20foe");
rooms[1].send(foe, CMClass.getMsg(foe, g, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> shove(s) <T-NAME>."));
check("cower", !g.isInCombat() && has(heard(w1), "cower"), "it fought on");
foe.makePeace(true);
rooms[1].send(g, CMClass.getMsg(g, null, foe, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_DEATH, "<S-NAME> is DEAD!!!"));
check("ondeath", gvar("FIXN20DEAD") == "Fixn20pip", "'" + gvar("FIXN20DEAD") + "'");

// a time limit
var t = newMob("GenMob", rooms[1]);
t.setName("Fixn20slow");
var TB = CMClass.getBehavior("Escortee");
TB.setParms("DEST=Fixn20Road#3 TIMEOUT=2 ONTIMEOUT=\"mpsetvar FIXTEST FIXN20LATE yes\"");
t.addBehavior(TB);
follow(t, pip);
tick(t, TB);
check("before-timeout", gvar("FIXN20LATE") == "", "too soon");
tick(t, TB);
check("timeout", gvar("FIXN20LATE") == "yes", "'" + gvar("FIXN20LATE") + "'");

[m, g, t, foe, w1.mob, w2.mob].forEach(function (x) { if (!x.amDestroyed()) x.destroy(); });
pip.setPlayerStats(null); pip.destroy();
CMLib.map().obliterateMapArea(X);
done();
