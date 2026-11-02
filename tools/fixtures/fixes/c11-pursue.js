// C11: Pursue CHANCE= MAXROOMS= DELAY= MASK= LEASH= STAY GIVEUPMSG= REMEMBER=: it follows one who flees it, room by
// room, attacks them there, gives up (and walks home) past its limits, and attacks one who comes back soon on sight.
FIXFILE = "c11-pursue";
var Dir = CM.core.Directions;
function s(x) { return "" + x; }
function room(name) { var r = scratchRoom(); r.setDisplayText(name); return r; }
function link(a, b) {
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
}
var A = room("Fixc11 den"), B = room("Fixc11 trail"), C = room("Fixc11 ridge"), D = room("Fixc11 peak");
link(A, B); link(B, C); link(C, D);
var V = newMob("GenMob", A);
V.setName("Fixc11pip");
V.baseState().setHitPoints(500); V.recoverMaxState(); V.resetToMaxState();
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var cat = newMob("GenMob", A);
cat.setName("Fixc11cat");
cat.setStartRoom(A);
cat.baseState().setHitPoints(500); cat.recoverMaxState(); cat.resetToMaxState();
var P = CMClass.getBehavior("Pursue");
P.setParms("GIVEUPMSG=\"<S-NAME> hiss(es): no chance, no delay, and give(s) up.\" CHANCE=100 MAXROOMS=2 DELAY=0 REMEMBER=10");
check("exact-keys", field(P, "chance") == 100 && field(P, "delay") == 0 && field(P, "maxRooms") == 2, "chance " + field(P, "chance") + " delay " + field(P, "delay"));
var XP = CM.core.CMParms.parseExactParms("A=1 FLAG B=\"two \\\"words\\\"\" C=x=y \"stray\" D=");
check("exact-parse", s(XP.get("A")) == "1" && s(XP.get("FLAG")) == "" && s(XP.get("B")) == 'two "words"' && s(XP.get("C")) == "x=y" && s(XP.get("D")) == "" && XP.size() == 5, "" + XP);
cat.addBehavior(P);
function flee(to) {
	cat.setVictim(V); V.setVictim(cat);
	V.location().send(V, CMClass.getMsg(V, null, null, CMMsg.MSG_FLEE, "<S-NAME> flee(s)."));
	V.makePeace(true);
	to.bringMobHere(V, false);
}
function tick(n) { for (var i = 0; i < (n || 1); i++) P.tick(cat, Tickable.TICKID_MOB); }
flee(B);
check("quarry", field(P, "quarry") == V, "not chasing");
tick();
check("follows", cat.location() == B && cat.getVictim() == V, "cat in " + cat.location().displayText() + ", fights " + cat.getVictim());
flee(C);
tick();
check("again", cat.location() == C, "cat in " + cat.location().displayText());
// caught, the rooms it took still count: MAXROOMS is for the whole chase
tick();
check("counted", field(P, "followed") == 2 && cat.getVictim() == V, "followed " + field(P, "followed"));
// MAXROOMS=2: it gives up, and walks home
out();
flee(D);
tick();
var seen = out();
check("gives-up", cat.location() == C && field(P, "quarry") == null && cat.fetchEffect("WanderHomeLater") != null, "cat in " + cat.location().displayText() + " " + seen);
cat.delEffect(cat.fetchEffect("WanderHomeLater"));
cat.makePeace(true);
// REMEMBER: one who fled it and comes back soon is attacked on sight
V.makePeace(true);
CMLib.tracking().walk(V, Dir.WEST, false, false); // back from the peak into the ridge
tick();
check("remember", cat.getVictim() == V, "not attacked on sight");
cat.makePeace(true); V.makePeace(true);
// MASK, CHANCE and LEASH
P.setParms("CHANCE=100 MAXROOMS=5 DELAY=0 MASK=\"-NAMES +Fixc11nobody\"");
A.bringMobHere(cat, false); A.bringMobHere(V, false);
flee(B);
tick();
check("mask", cat.location() == A && field(P, "quarry") == null, "chased one the mask does not fit");
P.setParms("CHANCE=0 DELAY=0");
A.bringMobHere(V, false);
flee(B);
tick();
check("chance", cat.location() == A, "chased at CHANCE=0");
P.setParms("CHANCE=100 MAXROOMS=5 DELAY=0 LEASH=1 STAY");
A.bringMobHere(V, false);
flee(B); tick();
flee(C); tick();
check("leash", cat.location() == B && cat.fetchEffect("WanderHomeLater") == null, "cat in " + cat.location().displayText());
// DELAY: it waits
P.setParms("CHANCE=100 DELAY=2");
cat.makePeace(true); A.bringMobHere(cat, false); A.bringMobHere(V, false);
flee(B);
tick(2);
var waited = cat.location() == A;
tick();
check("delay", waited && cat.location() == B, "delay");
check("help", s(CMLib.help().getHelpText("BEHAVIOR_PURSUE", M, true)).indexOf("MAXROOMS") >= 0, "no help");

[cat, V].forEach(function (m) { m.makePeace(true); });
cat.destroy(); V.destroy();
[A, B, C, D].forEach(function (r) { r.destroy(); });
done();
