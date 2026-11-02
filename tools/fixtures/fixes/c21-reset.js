// C21: EncounterReset RADIUS= IDLE= HEAL= HOME= DESPAWNADDS= ROOM=: when those it fought stay out of reach for IDLE
// ticks, it goes home, heals, sheds harmful effects, loses its summoned adds, and RESET_PROG runs.
FIXFILE = "c21-reset";
var Dir = CM.core.Directions;
function s(x) { return "" + x; }
function room(name) { var r = scratchRoom(); r.setDisplayText(name); return r; }
function link(a, b) {
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
}
var A = room("Fixc21 bridge"), B = room("Fixc21 bank"), C = room("Fixc21 road");
link(A, B); link(B, C);
function npc(name, r, hp) { var m = newMob("GenMob", r); m.setName(name); m.baseState().setHitPoints(hp); m.recoverMaxState(); m.resetToMaxState(); return m; }
var V = npc("Fixc21pip", A, 500);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); // the player's side: not one of the NPCs it resets
var troll = npc("Fixc21troll", A, 400);
troll.setStartRoom(A);
var guard = npc("Fixc21guard", A, 200);
guard.setStartRoom(A);
var ER = CMClass.getBehavior("EncounterReset");
ER.setParms("RADIUS=1 IDLE=3 HEAL=100 ROOM=true");
troll.addBehavior(ER);
clearVar("FIXC21R");
scriptOn(troll, "RESET_PROG\nmpsetvar FIXTEST FIXC21R reset\n~\n");
function tick(n) { for (var i = 0; i < (n || 1); i++) ER.tick(troll, Tickable.TICKID_MOB); }
// the fight
troll.setVictim(V); V.setVictim(troll);
tick();
check("engaged", field(ER, "engaged") == true, "not engaged");
troll.curState().setHitPoints(150); guard.curState().setHitPoints(50);
var P = CMClass.getAbility("Poison"); P.startTickDown(V, troll, 100);
var add = newMob("GenMob", A); add.setName("Fixc21skeleton"); add.setStartRoom(null);
add["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](troll);
var pet = npc("Fixc21dog", A, 50); pet.setStartRoom(A);
pet["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](troll);
// the party steps back one room: in reach, no reset
troll.makePeace(true); V.makePeace(true);
B.bringMobHere(V, false);
B.bringMobHere(troll, false); // it chased a little way
tick(5);
check("in-reach", troll.curState().getHitPoints() == 150 && troll.location() == B, "reset with a foe a room away");
// gone two rooms away: after IDLE ticks, the reset
C.bringMobHere(V, false);
A.bringMobHere(troll, false);
tick(2);
var early = troll.curState().getHitPoints() == 150;
tick();
check("idle", early, "reset before IDLE ticks");
check("healed", troll.curState().getHitPoints() == troll.maxState().getHitPoints() && guard.curState().getHitPoints() == guard.maxState().getHitPoints(),
	"troll " + troll.curState().getHitPoints() + " guard " + guard.curState().getHitPoints());
check("cured", troll.fetchEffect("Poison") == null, "still poisoned");
check("adds", add.amDestroyed() && (!pet.amDestroyed()), "skeleton gone " + add.amDestroyed() + ", dog " + pet.amDestroyed());
check("prog", gvar("FIXC21R") == "reset", "RESET_PROG did not run");
check("disengaged", field(ER, "engaged") == false, "still engaged");
// HOME: it walks back from where it was (ROOM=false: the guard on the bridge is not watching for its foes)
ER.setParms("RADIUS=1 IDLE=3");
C.bringMobHere(troll, false); C.bringMobHere(V, false);
troll.setVictim(V); V.setVictim(troll);
tick();
troll.makePeace(true); V.makePeace(true);
A.bringMobHere(V, false); // the troll in the road, the foe on the bridge two rooms off
tick(3);
check("home", troll.location() == A, "the troll stayed in " + troll.location().displayText());
check("help", s(CMLib.help().getHelpText("BEHAVIOR_ENCOUNTERRESET", M, true)).indexOf("IDLE") >= 0, "no help");

V.setPlayerStats(null);
[troll, guard, pet, V].forEach(function (m) { m.makePeace(true); m.destroy(); });
[A, B, C].forEach(function (r) { r.destroy(); });
done();
