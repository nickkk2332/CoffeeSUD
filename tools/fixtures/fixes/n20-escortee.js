// N20: Escortee: MODE=LEAD walks to DEST a step a tick with its party following, WAITFOR=LEADER waits and calls WAITMSG, TALK= remarks,
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
// it takes the lead: it follows no one, and the one it followed follows it, so comes along
check("leads", where(m) == "1" && m.amFollowing() == null, "at " + where(m));
check("party-follows", pip.amFollowing() == m && where(pip) == "1", "pip at " + where(pip) + ", following " + pip.amFollowing());
// the party is still led by the player's character, not the NPC they follow
check("party-leader", CM.core.Party.leaderOf(pip) == pip, "leader " + CM.core.Party.leaderOf(pip));
// pip stops following and stays behind: it waits, and calls
CMLib.commands().postFollow(pip, null, true);
rooms[0].bringMobHere(pip, false);
tick(m, E);
check("waits", where(m) == "1" && !has(heard(w1), "Fixn20 wait for me!"), "at " + where(m));
tick(m, E);
check("calls", where(m) == "1" && has(heard(w1), "Fixn20 wait for me!"), "no call");
rooms[1].bringMobHere(pip, false);
tick(m, E);
// once pip has stopped following, it is not made to again
check("goes-on", where(m) == "2" && where(pip) == "1" && pip.amFollowing() == null, "at " + where(m) + ", pip at " + where(pip));
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
check("led-kept", has(state, "LED=\"true\""), state);

// leading the party the whole way: pip follows it to the end, and then stops
rooms[0].bringMobHere(pip, false);
var c = newMob("GenMob", rooms[0]);
c.setName("Fixn20courier");
var C = CMClass.getBehavior("Escortee");
C.setParms("DEST=Fixn20Road#3 MODE=LEAD");
c.addBehavior(C);
follow(c, pip);
var dog = newMob("GenMob", rooms[0]);
dog.setName("Fixn20dog");
follow(dog, pip);
tick(c, C);
// pip's own follower comes too, and still counts as following pip: saved with pip, not with the world, and on
// the player's side
check("led-follower", where(dog) == "1" && dog.amFollowing() == pip, "dog at " + where(dog));
check("led-follower-saved", callTyped(null, java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState"), "isSavedHere", ["MOB"], [dog]) == false
	&& CM.core.GameDifficulty.playerSide(dog) && CM.core.Party.leaderOf(dog) == pip, "the dog counts as the world's");
for (var i = 0; i < 3; i++) tick(c, C);
check("led-there", where(c) == "3" && where(pip) == "3", "courier at " + where(c) + ", pip at " + where(pip));
check("led-released", pip.amFollowing() == null && has(s(C.getStat("STATE")), "DONE=\"true\""), "pip still follows " + pip.amFollowing());

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
// a PANIC word that is none of FLEE, HIDE and COWER is reported and ignored, not taken for COWER
var u = newMob("GenMob", rooms[1]);
u.setName("Fixn20stoic");
var UB = CMClass.getBehavior("Escortee");
UB.setParms("DEST=Fixn20Road#3 PANIC=RUN");
u.addBehavior(UB);
var heardBefore = heard(w1).length;
rooms[1].send(foe, CMClass.getMsg(foe, u, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> shove(s) <T-NAME>."));
var after = heard(w1).substring(heardBefore);
check("panic-unknown", has(s(UB.getStat("DEBUG")), "PANIC=RUN is not FLEE, HIDE or COWER") && !has(after, "cower") && !has(after, "panic"),
	s(UB.getStat("DEBUG")) + " / " + after);
foe.makePeace(true); u.makePeace(true);
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

[m, c, dog, g, t, u, foe, w1.mob, w2.mob].forEach(function (x) { if (!x.amDestroyed()) x.destroy(); });
pip.setPlayerStats(null); pip.destroy();
CMLib.map().obliterateMapArea(X);
done();
