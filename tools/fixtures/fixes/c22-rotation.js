// C22: CombatAbilities rotations: OPENER=, +SKILL(EVERY=n FIRST=n), (ONCE BELOW=n% SELF), (COOLDOWN=n), (WEIGHT=n),
// (TARGET=CASTER); the counters start again with a new fight.
FIXFILE = "c22-rotation";
function s(x) { return "" + x; }
var R = scratchRoom();
function npc(name, hp, ids) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.basePhyStats().setLevel(30);
	m.baseState().setHitPoints(hp); m.baseState().setMana(2000); m.baseState().setMovement(2000);
	m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState();
	(ids || []).forEach(function (id) { var A = CMClass.getAbility(id); A.setProficiency(100); m.addAbility(A); });
	return m;
}
var wyvern = npc("Fixc22wyvern", 300, ["Skill_Trip", "Spell_MagicMissile", "Prayer_CureLight", "Fighter_Kick"]);
var foe = npc("Fixc22foe", 5000);
CMLib.factions().setAlignment(wyvern, CM.Common.interfaces.Faction.Align.GOOD); // for the prayer
var B = CMClass.getBehavior("CombatAbilities");
B.setParms("proficient OPENER=Skill_Trip +Spell_MagicMissile(EVERY=2) +Prayer_CureLight(ONCE BELOW=30% SELF) +Fighter_Kick(WEIGHT=3)");
wyvern.addBehavior(B);
check("parsed", field(B, "rotation").size() == 3 && s(field(B, "opener")) == "Skill_Trip"
	&& s(B.getStat("SKILLSALWAYS")).indexOf("Spell_MagicMissile") >= 0 && s(B.getStat("SKILLSALWAYS")).indexOf("Fighter_Kick") >= 0,
	"rules " + field(B, "rotation").size() + " opener " + field(B, "opener") + " always " + B.getStat("SKILLSALWAYS"));
function fight() { wyvern.setVictim(foe); foe.setVictim(wyvern); }
function rounds(n) {
	for (var i = 0; i < n; i++) {
		fight();
		wyvern.setActions(20); wyvern.curState().setMana(2000); wyvern.curState().setMovement(2000);
		foe.curState().setHitPoints(5000);
		B.tick(wyvern, Tickable.TICKID_MOB);
	}
	return s(field(B, "rotationLog"));
}
var log = rounds(6);
function at(r) { var l = log.replace(/[\[\] ]/g, "").split(","); for (var i = 0; i < l.length; i++) if (l[i].indexOf("@" + r) > 0 && l[i].split("@")[1] == ("" + r)) return l[i].split("@")[0]; return ""; }
check("opener", at(1) == "Skill_Trip", log);
check("every", at(2) == "Spell_MagicMissile" && at(4) == "Spell_MagicMissile" && at(6) == "Spell_MagicMissile", log);
check("between", at(3) != "Spell_MagicMissile" && at(5) != "Spell_MagicMissile" && at(3) != "Prayer_CureLight" && at(5) != "Prayer_CureLight", log);
// below 30%: the heal, on itself, once
wyvern.curState().setHitPoints(60);
log = rounds(1); // round 7: odd, so not the missile's turn
var healedAt7 = at(7);
check("below", healedAt7 == "Prayer_CureLight", log);
wyvern.curState().setHitPoints(60);
log = rounds(2);
check("once", s(log).split("Prayer_CureLight").length == 2, log);
// a new fight starts again with the opener
wyvern.makePeace(true); foe.makePeace(true);
B.tick(wyvern, Tickable.TICKID_MOB);
check("reset", field(B, "round") == 0 && field(B, "rotationLog").size() == 0, "round " + field(B, "round"));
log = rounds(1);
check("opener-again", at(1) == "Skill_Trip", log);
wyvern.makePeace(true); foe.makePeace(true);
// COOLDOWN and TARGET=CASTER
var mage = npc("Fixc22mage", 500, ["Spell_Sleep", "Spell_MagicMissile"]);
var B2 = CMClass.getBehavior("CombatAbilities");
B2.setParms("proficient +Spell_MagicMissile(EVERY=1 COOLDOWN=2 TARGET=CASTER)");
var drake = npc("Fixc22drake", 300, ["Spell_MagicMissile"]);
drake.addBehavior(B2);
var log2 = "";
for (var i = 0; i < 6; i++) {
	drake.setVictim(foe); foe.setVictim(drake); mage.setVictim(drake);
	drake.setActions(20); drake.curState().setMana(2000); foe.curState().setHitPoints(5000); mage.curState().setHitPoints(500);
	B2.tick(drake, Tickable.TICKID_MOB);
}
log2 = s(field(B2, "rotationLog"));
check("cooldown", log2.split("Spell_MagicMissile").length - 1 == 2, log2);
check("help", s(CMLib.help().getHelpText("BEHAVIOR_COMBATABILITIES", M, true)).indexOf("OPENER=") >= 0, "no help");

[wyvern, foe, mage, drake].forEach(function (m) { m.makePeace(true); m.destroy(); });
R.destroy();
done();
