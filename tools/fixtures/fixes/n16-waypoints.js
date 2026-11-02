// N16: Patroller waypoints: WAIT=n, DO=command, EMOTE=text, IF=mask (else to the next RESTART), ROUTE=DAY:/NIGHT:
// by the time of day, and RESUME=true back to the route after a fight.
FIXFILE = "n16-waypoints";
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixn16Fort");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
var rooms = [];
for (var i = 0; i < 4; i++) {
	var Q = CMClass.getLocale("StdRoom");
	Q.setRoomID("Fixn16Fort#" + i);
	Q.setArea(X);
	Q.setDisplayText("Fixn16 room " + i);
	rooms.push(Q);
}
var EAST = CMLib.directions().getGoodDirectionCode("east"), WEST = CMLib.directions().getGoodDirectionCode("west");
function link(a, b) {
	a.rawDoors()[EAST] = b; a.setRawExit(EAST, CMClass.getExit("StdOpenDoorway"));
	b.rawDoors()[WEST] = a; b.setRawExit(WEST, CMClass.getExit("StdOpenDoorway"));
}
link(rooms[0], rooms[1]); link(rooms[1], rooms[2]); link(rooms[2], rooms[3]);
function where(m) { var R = m.location(); return R == null ? "nowhere" : s(R.roomID()).replace("Fixn16Fort#", ""); }
function watcher(at) {
	var w = newMob("GenMob", rooms[at]);
	w.setName("Fixn16watcher" + at);
	var S = CMClass.getCommon("FakeSession"); S.initialize(null, "MEMORY"); w.setSession(S); S.setMob(w);
	return { mob: w, s: S };
}
function heard(w) { return s(field(w.s, "bout").toString()).replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var w1 = watcher(1), w2 = watcher(2);
var C = X.getTimeObj();
var wasHour = C.getHourOfDay();
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); var t = s(C.getTODCode()); if (t == "NIGHT" && night < 0) night = h; if (t == "DAY" && day < 0) day = h; }
C.setHourOfDay(day);

// a round: Gate (#1), wait, a word; Wall (#2), a look; IF fails: back to the start, never to #3
var g = newMob("GenMob", rooms[0]);
g.setName("Fixn16guard");
var PA = CMClass.getBehavior("Patroller");
PA.setParms("min=1 max=1 chance=100;Fixn16Fort#1;WAIT=2;DO=say Fixn16 gate secure.;Fixn16Fort#2;EMOTE=peers over the battlements.;IF=-NAMES +nobodyfixn16;Fixn16Fort#3;RESTART");
g.addBehavior(PA);
var trail = [];
for (var i = 0; i < 16; i++) { PA.tick(g, Tickable.TICKID_MOB); trail.push(where(g)); }
var tr = trail.join("");
check("waits", tr.indexOf("111") >= 0, tr);
check("do", has(heard(w1), "gate secure."), "no word at the gate: " + tr); // (SAY takes "Fixn16" as the watcher spoken to)
check("emote", has(heard(w2), "peers over the battlements."), "no look from the wall: " + tr);
check("if", tr.indexOf("3") < 0 && tr.indexOf("2") >= 0 && tr.lastIndexOf("1") > tr.indexOf("2"), "the IF did not turn it back: " + tr);
// the steps it walks back: none of the tokens
var steps = s(call(PA, "getSteps", []));
check("steps", has(steps, "WAIT=2") && has(steps, "RESTART"), steps);

// routes by the time of day
var n = newMob("GenMob", rooms[1]);
n.setName("Fixn16watch");
var PN = CMClass.getBehavior("Patroller");
PN.setParms("min=1 max=1 chance=100;ROUTE=DAY:Fixn16Fort#2;Fixn16Fort#3;RESTART;ROUTE=NIGHT:Fixn16Fort#0;RESTART");
n.addBehavior(PN);
PN.tick(n, Tickable.TICKID_MOB);
var daySteps = s(call(PN, "getSteps", []));
check("route-day", has(daySteps, "Fixn16Fort#2") && !has(daySteps, "Fixn16Fort#0") && where(n) == "2", daySteps + " at " + where(n));
C.setHourOfDay(night);
PN.tick(n, Tickable.TICKID_MOB); PN.tick(n, Tickable.TICKID_MOB); PN.tick(n, Tickable.TICKID_MOB);
var nightSteps = s(call(PN, "getSteps", []));
check("route-night", has(nightSteps, "Fixn16Fort#0") && !has(nightSteps, "Fixn16Fort#3"), nightSteps);
check("route-walks", where(n) == "1" || where(n) == "0", "at " + where(n));
C.setHourOfDay(day);

// RESUME: chased off its route, it goes back
var r = newMob("GenMob", rooms[2]);
r.setName("Fixn16sentry");
var PR = CMClass.getBehavior("Patroller");
PR.setParms("min=50 max=50 chance=100 RESUME=true;east;west");
r.addBehavior(PR);
PR.tick(r, Tickable.TICKID_MOB); // on its route at #2
var foe = newMob("GenMob", rooms[2]);
foe.setName("Fixn16foe");
r.setVictim(foe);
PR.tick(r, Tickable.TICKID_MOB);
rooms[0].bringMobHere(r, false); // driven off
r.makePeace(true); foe.makePeace(true);
PR.tick(r, Tickable.TICKID_MOB);
PR.tick(r, Tickable.TICKID_MOB);
PR.tick(r, Tickable.TICKID_MOB);
check("resume", where(r) == "2", "the sentry is at " + where(r));

C.setHourOfDay(wasHour);
[g, n, r, foe, w1.mob, w2.mob].forEach(function (m) { m.destroy(); });
CMLib.map().obliterateMapArea(X);
done();
