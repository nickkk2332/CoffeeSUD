// N17: WARN="line|line" WARNTICKS=n BACKOFF=true NEAR=1 on Aggressive (and MobileAggressive): a line a tick, then
// the attack if they are still there; backing off (no weapon, sitting) holds it; next door hears it.
FIXFILE = "n17-warning";
var AW = CM.core.AggroWarning;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom(), R2 = scratchRoom();
var EAST = CMLib.directions().getGoodDirectionCode("east"), WEST = CMLib.directions().getGoodDirectionCode("west");
R.rawDoors()[EAST] = R2; R.setRawExit(EAST, CMClass.getExit("StdOpenDoorway"));
R2.rawDoors()[WEST] = R; R2.setRawExit(WEST, CMClass.getExit("StdOpenDoorway"));
function person(name, room) {
	var m = newMob("GenMob", room);
	CMLib.threads().deleteTick(m, -1); // only the test ticks the test's mobs: the game's own ticks act between its steps
	m.setName(name);
	var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	S.initialize(null, "MEMORY"); m.setSession(S); S.setMob(m);
	m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return { mob: m, s: S, mark: 0 };
}
function out(p) { var all = s(field(p.s, "bout").toString()); var o = all.substring(p.mark); p.mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var pip = person("Fixn17pip", R), next = person("Fixn17next", R2);
var sword = CMClass.getWeapon("GenWeapon"); sword.setName("a fixn17 sword"); pip.mob.addItem(sword); sword.wearIfPossible(pip.mob);
var parms = "WARN=\"Fixn17 the wolf bares its teeth.|Fixn17 the wolf snarls and crouches!\" WARNTICKS=2 BACKOFF=true NEAR=1";
var spec = AW.parse(parms);
check("parse", spec != null && spec.lines.length == 2 && spec.ticks == 2 && spec.backoff && spec.near, "spec");
var wolf = newMob("GenMob", R);
CMLib.threads().deleteTick(wolf, -1);
wolf.setName("Fixn17wolf");
var A = CMClass.getBehavior("Aggressive");
A.setParms(parms);
wolf.addBehavior(A);
function tick(m, B) { B.tick(m, Tickable.TICKID_MOB); }
out(pip); out(next);
tick(wolf, A);
check("warn-1", has(out(pip), "Fixn17 the wolf bares its teeth.") && !wolf.isInCombat(), "no first warning");
check("near", has(out(next), "Fixn17 the wolf bares its teeth."), "next door heard nothing");
tick(wolf, A);
check("warn-2", has(out(pip), "Fixn17 the wolf snarls and crouches!") && !wolf.isInCombat(), "no second warning");
tick(wolf, A);
check("attack", wolf.isInCombat() && wolf.getVictim() == pip.mob, "no attack after the warning");
wolf.makePeace(true); pip.mob.makePeace(true);

// backing off: weapon away, held; taken up again, warned afresh
var wolf2 = newMob("GenMob", R);
CMLib.threads().deleteTick(wolf2, -1);
wolf2.setName("Fixn17bitch");
var A2 = CMClass.getBehavior("Aggressive");
A2.setParms(parms);
wolf2.addBehavior(A2);
tick(wolf2, A2);
check("w2-warned", AW.warnedFor(wolf2, "Fixn17pip") == 1, "count " + AW.warnedFor(wolf2, "Fixn17pip") + " wielded " + pip.mob.fetchWieldedItem() + " worn " + sword.rawWornCode() + " owner " + sword.owner() + " sit " + CMLib.flags().isSitting(pip.mob));
sword.unWear(); pip.mob.recoverPhyStats();
tick(wolf2, A2); tick(wolf2, A2); tick(wolf2, A2);
check("held", !wolf2.isInCombat() && AW.warnedFor(wolf2, "Fixn17pip") == -1, "count " + AW.warnedFor(wolf2, "Fixn17pip") + " wielded " + pip.mob.fetchWieldedItem() + " worn " + sword.rawWornCode() + " owner " + sword.owner() + " sit " + CMLib.flags().isSitting(pip.mob));
sword.wearAt(CM.Items.interfaces.Wearable.WORN_WIELD); pip.mob.recoverPhyStats();
out(pip);
tick(wolf2, A2);
check("afresh", has(out(pip), "Fixn17 the wolf bares its teeth.") && AW.warnedFor(wolf2, "Fixn17pip") == 1 && !wolf2.isInCombat(), "count " + AW.warnedFor(wolf2, "Fixn17pip") + " wielded " + pip.mob.fetchWieldedItem() + " worn " + sword.rawWornCode() + " owner " + sword.owner() + " sit " + CMLib.flags().isSitting(pip.mob));
// gone: forgotten
R2.bringMobHere(pip.mob, false);
tick(wolf2, A2);
check("forgotten", AW.warnedFor(wolf2, "Fixn17pip") == 0, "count " + AW.warnedFor(wolf2, "Fixn17pip") + " wielded " + pip.mob.fetchWieldedItem() + " worn " + sword.rawWornCode() + " owner " + sword.owner() + " sit " + CMLib.flags().isSitting(pip.mob));
R.bringMobHere(pip.mob, false);

// MobileAggressive warns too (the fight above over first)
[pip.mob, wolf, wolf2].forEach(function (m) { m.makePeace(true); });
var wolf3 = newMob("GenMob", R);
wolf3.setName("Fixn17cub");
CMLib.threads().deleteTick(wolf3, -1);
var A3 = CMClass.getBehavior("MobileAggressive");
A3.setParms("min=100 max=100 " + parms);
wolf3.addBehavior(A3);
// (it may wander off on its first tick, so: its inner VeryAggressive has the warning, and its tick carries it on)
var inner = field(A3, "veryA");
check("mobile", field(inner, "warn") != null && field(field(inner, "warn"), "ticks") == 2, "the warning did not reach it");
AW.mayAttack(wolf3, pip.mob, spec);
var room3 = wolf3.location();
tick(wolf3, A3);
check("mobile-tick", (wolf3.location() != room3) || (AW.warnedFor(wolf3, "Fixn17pip") == 2), "count " + AW.warnedFor(wolf3, "Fixn17pip"));

[wolf, wolf2, wolf3].forEach(function (m) { m.destroy(); });
[pip, next].forEach(function (p) { p.mob.setPlayerStats(null); p.mob.destroy(); });
R.destroy(); R2.destroy();
done();
