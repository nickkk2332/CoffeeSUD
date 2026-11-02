// C10: TargetPriority PREFER=HEALER,CASTER,LOWESTHP,LOWESTARMOR,LEADER,LASTHITME,STRONGEST,MASK:zapper, SWITCH=,
// STICKY=, TAUNTABLE (a clear threat lead first).
FIXFILE = "c10-target";
var TH = CM.core.Threat;
function s(x) { return "" + x; }
var R = scratchRoom();
function npc(name, hp, ids) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.baseState().setHitPoints(hp); m.recoverMaxState(); m.resetToMaxState();
	(ids || []).forEach(function (id) { var A = CMClass.getAbility(id); m.addAbility(A); });
	return m;
}
var lead = npc("Fixc10lead", 100);
var mage = npc("Fixc10mage", 100, ["Spell_MagicMissile", "Spell_Sleep"]);
var priest = npc("Fixc10priest", 100, ["Prayer_CureLight"]);
var tank = npc("Fixc10tank", 100);
[mage, priest, tank].forEach(function (m) { m["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](lead); });
var ogre = npc("Fixc10ogre", 1000);
function start(parms) {
	var old = ogre.fetchBehavior("TargetPriority");
	if (old != null) ogre.delBehavior(old);
	var B = CMClass.getBehavior("TargetPriority"); B.setParms(parms); ogre.addBehavior(B);
	ogre.setVictim(tank);
	[lead, mage, priest, tank].forEach(function (m) { m.setVictim(ogre); });
	return B;
}
function tick(B, n) { for (var i = 0; i < (n || 1); i++) B.tick(ogre, Tickable.TICKID_MOB); return ogre.getVictim() == null ? "nobody" : s(ogre.getVictim().Name()); }
var B = start("PREFER=CASTER,LOWESTHP SWITCH=100 STICKY=0");
check("caster", tick(B) == "Fixc10mage", "went for " + s(ogre.getVictim().Name()));
B = start("PREFER=HEALER SWITCH=100 STICKY=0");
check("healer", tick(B) == "Fixc10priest", "went for " + s(ogre.getVictim().Name()));
tank.curState().setHitPoints(20);
B = start("PREFER=LOWESTHP SWITCH=100 STICKY=0");
check("lowesthp", tick(B) == "Fixc10tank", "went for " + s(ogre.getVictim().Name()));
tank.curState().setHitPoints(100);
B = start("PREFER=LEADER SWITCH=100 STICKY=0");
check("leader", tick(B) == "Fixc10lead", "went for " + s(ogre.getVictim().Name()));
B = start("PREFER=LASTHITME SWITCH=100 STICKY=0");
CMLib.combat().postDamage(priest, ogre, null, 5, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("lasthit", tick(B) == "Fixc10priest", "went for " + s(ogre.getVictim().Name()));
B = start("PREFER=\"MASK:-NAMES +Fixc10mage\" SWITCH=100 STICKY=0");
check("mask", tick(B) == "Fixc10mage", "went for " + s(ogre.getVictim().Name()));
// STICKY: once switched, it stays a while
B = start("PREFER=LOWESTHP SWITCH=100 STICKY=3");
mage.curState().setHitPoints(10);
tick(B);
mage.curState().setHitPoints(100); priest.curState().setHitPoints(5);
var during = tick(B, 3);
var after = tick(B);
check("sticky", during == "Fixc10mage" && after == "Fixc10priest", "during " + during + ", after " + after);
priest.curState().setHitPoints(100);
// SWITCH=0: never
B = start("PREFER=CASTER SWITCH=0");
check("switch0", tick(B, 5) == "Fixc10tank", "switched with SWITCH=0");
// TAUNTABLE: a clear threat lead beats the list
B = start("PREFER=CASTER SWITCH=100 STICKY=0 TAUNTABLE");
TH.set(ogre, lead, 1000);
check("taunt", tick(B) == "Fixc10lead", "went for " + s(ogre.getVictim().Name()));
check("help", s(CMLib.help().getHelpText("BEHAVIOR_TARGETPRIORITY", M, true)).indexOf("LASTHITME") >= 0, "no help");

[lead, mage, priest, tank, ogre].forEach(function (m) { m.makePeace(true); });
[lead, mage, priest, tank, ogre].forEach(function (m) { m.destroy(); });
R.destroy();
done();
