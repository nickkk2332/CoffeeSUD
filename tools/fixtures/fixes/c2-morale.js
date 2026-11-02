// C2: Morale FLEEAT=, OUTNUMBERED=, BREAK=LEADERDEAD|n%DEAD|HP<n%, ACTION=FLEE|SURRENDER|BERSERK, CHANCE=, MSG=,
// SURRENDERAT= (SURRENDER_PROG), BRAVE=, RALLY= REGROUP=.
FIXFILE = "c2-morale";
var Dir = CM.core.Directions;
function s(x) { return "" + x; }
function room(name) { var r = scratchRoom(); r.setDisplayText(name); return r; }
function link(a, b) {
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
}
var A = room("Fixc2 camp"), B = room("Fixc2 warren");
link(A, B);
function npc(name, r, hp) { var m = newMob("GenMob", r); m.setName(name); m.baseState().setHitPoints(hp); m.baseState().setMovement(500); m.recoverMaxState(); m.resetToMaxState(); return m; }
var V = npc("Fixc2pip", A, 1000);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function morale(m, parms) { var B = CMClass.getBehavior("Morale"); B.setParms(parms); m.addBehavior(B); return B; }
function fight(m) { m.setVictim(V); V.setVictim(m); }
function tick(m, B, n) { for (var i = 0; i < (n || 1); i++) B.tick(m, Tickable.TICKID_MOB); }

// FLEEAT: under 40%, it runs
var gob = npc("Fixc2goblin", A, 100);
var GB = morale(gob, "FLEEAT=40% ACTION=FLEE");
fight(gob); tick(gob, GB);
gob.curState().setHitPoints(30);
tick(gob, GB);
check("flee", gob.location() == B, "the goblin stayed at 30%");
V.makePeace(true); gob.makePeace(true); A.bringMobHere(gob, false);

// SURRENDERAT, with SURRENDER_PROG
var bandit = npc("Fixc2bandit", A, 100);
var sword = CMClass.getWeapon("Sword"); bandit.addItem(sword); sword.wearAt(CM.Items.interfaces.Wearable.WORN_WIELD); bandit.recoverPhyStats();
var BB = morale(bandit, "SURRENDERAT=20% SURRENDERMSG=\"Mercy! I'll show you the loot!\"");
clearVar("FIXC2S");
scriptOn(bandit, "SURRENDER_PROG\nmpsetvar FIXTEST FIXC2S $n\n~\n");
fight(bandit); tick(bandit, BB);
bandit.curState().setHitPoints(10);
tick(bandit, BB);
check("surrender", (!bandit.isInCombat()) && (V.getVictim() != bandit) && bandit.fetchWieldedItem() == null && gvar("FIXC2S") == "Fixc2pip",
	"fighting " + bandit.isInCombat() + " weapon " + bandit.fetchWieldedItem() + " prog '" + gvar("FIXC2S") + "'");

// BERSERK: an adjuster while the fight lasts
var orc = npc("Fixc2orc", A, 100);
orc.basePhyStats().setDamage(20); orc.basePhyStats().setAttackAdjustment(50); orc.recoverPhyStats();
var OB = morale(orc, "FLEEAT=50% ACTION=BERSERK MSG=\":roars in fury!\"");
fight(orc); tick(orc, OB);
orc.curState().setHitPoints(40);
tick(orc, OB);
check("berserk", orc.fetchEffect("Prop_Adjuster") != null && orc.phyStats().damage() == 26 && orc.phyStats().attackAdjustment() == 45 && orc.location() == A,
	"damage " + orc.phyStats().damage() + " attack " + orc.phyStats().attackAdjustment());
orc.makePeace(true); V.makePeace(true);
tick(orc, OB);
check("calm", orc.fetchEffect("Prop_Adjuster") == null && orc.phyStats().damage() == 20, "still berserk after the fight");

// BREAK=LEADERDEAD: the chief falls, the gang scatters; BRAVE never breaks
var chief = npc("Fixc2chief", A, 100);
var g2 = npc("Fixc2runt", A, 100);
var g3 = npc("Fixc2veteran", A, 100);
[g2, g3].forEach(function (m) { m["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](chief); });
var R2 = morale(g2, "BREAK=LEADERDEAD ACTION=FLEE");
var R3 = morale(g3, "BREAK=LEADERDEAD ACTION=FLEE BRAVE=\"-NAMES +Fixc2veteran\"");
fight(g2); fight(g3); fight(chief);
tick(g2, R2); tick(g3, R3);
CMLib.combat().postDeath(V, chief, null);
fight(g2); fight(g3);
tick(g2, R2); tick(g3, R3);
check("leaderdead", g2.location() == B && g3.location() == A, "runt in " + g2.location().displayText() + ", veteran in " + g3.location().displayText());
[g2, g3, V].forEach(function (m) { m.makePeace(true); });
B.bringMobHere(g3, false);

// OUTNUMBERED: alone against two
var wolf = npc("Fixc2wolf", A, 100);
var pal = npc("Fixc2pal", A, 100); pal["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
var WB = morale(wolf, "OUTNUMBERED=2.0 ACTION=FLEE");
fight(wolf); pal.setVictim(wolf);
tick(wolf, WB);
check("outnumbered", wolf.location() == B, "the wolf stood against two");
[wolf, pal, V].forEach(function (m) { m.makePeace(true); });

// RALLY=HOME REGROUP=2: it runs home, waits, gathers the idle ones there, and comes back at them
var scout = npc("Fixc2scout", A, 100); scout.setStartRoom(B);
var friend = npc("Fixc2friend", B, 100);
var SB = morale(scout, "FLEEAT=50% RALLY=HOME REGROUP=2");
fight(scout); tick(scout, SB);
scout.curState().setHitPoints(20);
tick(scout, SB);
var fled = scout.location() == B;
V.makePeace(true); scout.makePeace(true);
tick(scout, SB, 2);
var waited = s(field(SB, "state")) == "regrouping";
tick(scout, SB);
check("rally", fled && waited && friend.amFollowing() == scout && s(field(SB, "state")) == "returning",
	"fled " + fled + " waited " + waited + " friend follows " + friend.amFollowing() + " state " + field(SB, "state"));
CM.core.CMLib.tracking().stopTracking(scout);
A.bringMobHere(scout, false); // its walk back
tick(scout, SB);
check("charge", scout.getVictim() == V && s(field(SB, "state")) == "", "fights " + scout.getVictim() + " state " + field(SB, "state"));
[scout, V].forEach(function (m) { m.makePeace(true); });

// CHANCE=0: holds
var rat = npc("Fixc2rat", A, 100);
var RB = morale(rat, "FLEEAT=90% CHANCE=0");
fight(rat); rat.curState().setHitPoints(10); tick(rat, RB, 3);
check("chance", rat.location() == A, "fled at CHANCE=0");
[rat, V].forEach(function (m) { m.makePeace(true); });
check("help", s(CMLib.help().getHelpText("BEHAVIOR_MORALE", M, true)).indexOf("SURRENDERAT") >= 0, "no help");

V.setPlayerStats(null);
[gob, bandit, orc, chief, g2, g3, wolf, pal, scout, friend, rat, V].forEach(function (m) { if (!m.amDestroyed()) { m.makePeace(true); m.destroy(); } });
[A, B].forEach(function (r) { r.destroy(); });
done();
