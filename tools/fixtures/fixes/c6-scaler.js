// C6: Prop_PartyScaler BASE= HP= DAMAGE= ATTACK= LEVEL= MIN= MAX= ADDS=: scaled when the fight starts, until it ends;
// on a room its NPCs get it when they fight; CONSIDER says so.
FIXFILE = "c6-scaler";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var SK = CMClass.getMOB("GenMob"); SK.setName("a fixc6 skeleton"); SK.recoverPhyStats(); CMLib.catalog().addCatalog(SK); SK.destroy();
var R = scratchRoom();
function npc(name, hp, lvl) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.basePhyStats().setLevel(lvl); m.basePhyStats().setDamage(20); m.basePhyStats().setAttackAdjustment(50);
	m.baseState().setHitPoints(hp);
	m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState();
	return m;
}
var V = npc("Fixc6pip", 5000, 10);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var king = npc("Fixc6king", 300, 10);
var P = CMClass.getAbility("Prop_PartyScaler");
P.setMiscText("BASE=3 HP=+25%/MEMBER DAMAGE=+10%/MEMBER ATTACK=+5/MEMBER ADDS=\"a fixc6 skeleton/MEMBER-ABOVE-3\"");
king.addNonUninvokableEffect(P);
function fight(m) { m.setVictim(V); V.setVictim(m); }
function tick(m, n) { var A = m.fetchEffect("Prop_PartyScaler"); for (var i = 0; i < (n || 1); i++) A.tick(m, Tickable.TICKID_MOB); }
function skeletons() { var n = 0; for (var i = 0; i < R.numInhabitants(); i++) if (s(R.fetchInhabitant(i).Name()) == "a fixc6 skeleton") n++; return n; }
// alone against a king tuned for three
fight(king);
tick(king);
check("alone", king.maxState().getHitPoints() == 150 && king.curState().getHitPoints() == 150 && king.phyStats().damage() == 16 && king.phyStats().attackAdjustment() == 40 && skeletons() == 0,
	"hp " + king.maxState().getHitPoints() + "/" + king.curState().getHitPoints() + " damage " + king.phyStats().damage() + " attack " + king.phyStats().attackAdjustment());
// the fight ends: as it was
king.makePeace(true); V.makePeace(true);
tick(king, 2);
check("after", king.maxState().getHitPoints() == 300 && king.phyStats().damage() == 20 && P.members() == 0, "hp " + king.maxState().getHitPoints());
// a party of five: half again the hit points, and two skeletons
var pals = [];
for (var i = 0; i < 4; i++) { var p = npc("Fixc6pal" + i, 500, 10); p["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V); pals.push(p); }
king.resetToMaxState();
fight(king);
tick(king);
var sk = null; for (var j = 0; j < R.numInhabitants(); j++) if (s(R.fetchInhabitant(j).Name()) == "a fixc6 skeleton") sk = R.fetchInhabitant(j);
check("five", P.members() == 5 && king.maxState().getHitPoints() == 450 && skeletons() == 2 && sk != null && sk.amFollowing() == king && sk.hasTag("SUMMONED"),
	"members " + P.members() + " hp " + king.maxState().getHitPoints() + " skeletons " + skeletons());
king.makePeace(true); V.makePeace(true);
for (var k = R.numInhabitants() - 1; k >= 0; k--) R.fetchInhabitant(k).makePeace(true);
tick(king, 2);
check("adds-go", skeletons() == 0, "skeletons stayed " + skeletons());
// LEVEL=AVG+2 with MAX=
var duke = npc("Fixc6duke", 200, 3);
var PD = CMClass.getAbility("Prop_PartyScaler"); PD.setMiscText("LEVEL=AVG+2 MIN=5 MAX=11"); duke.addNonUninvokableEffect(PD);
fight(duke);
tick(duke);
check("level", duke.phyStats().level() == 11, "level " + duke.phyStats().level());
duke.makePeace(true); V.makePeace(true); tick(duke, 2);
check("level-back", duke.phyStats().level() == 3, "level " + duke.phyStats().level());
// on a room: an NPC that fights there gets it, and loses it after
var R2 = scratchRoom();
var PR = CMClass.getAbility("Prop_PartyScaler"); PR.setMiscText("BASE=1 HP=+100%/MEMBER"); R2.addNonUninvokableEffect(PR);
var ogre = newMob("GenMob", R2); ogre.setName("Fixc6ogre"); ogre.baseState().setHitPoints(100); ogre.recoverMaxState(); ogre.resetToMaxState();
R2.bringMobHere(V, false); pals.forEach(function (p) { R2.bringMobHere(p, false); });
CMLib.combat().postAttack(V, ogre, null);
var handed = ogre.fetchEffect("Prop_PartyScaler");
ogre.setVictim(V);
if (handed != null) handed.tick(ogre, Tickable.TICKID_MOB);
check("room", handed != null && ogre.maxState().getHitPoints() == 500, "handed " + (handed != null) + " hp " + ogre.maxState().getHitPoints());
ogre.makePeace(true); V.makePeace(true);
if (handed != null) { handed.tick(ogre, Tickable.TICKID_MOB); handed.tick(ogre, Tickable.TICKID_MOB); }
check("room-after", ogre.fetchEffect("Prop_PartyScaler") == null && ogre.maxState().getHitPoints() == 100, "still scaled");
// CONSIDER says it scales
R.bringMobHere(V, false);
out();
CMClass.getCommand("Consider").execute(V, CM.core.CMParms.parse("consider Fixc6king"), 0);
check("consider", has(out(), "scales to your party"), "CONSIDER said nothing of it");
check("help", has(CMLib.help().getHelpText("PROP_PARTYSCALER", M, true), "MEMBER-ABOVE"), "no help");

[king, duke, ogre, V].concat(pals).forEach(function (m) { m.makePeace(true); if (!m.amDestroyed()) m.destroy(); });
for (var z = R.numInhabitants() - 1; z >= 0; z--) R.fetchInhabitant(z).destroy();
R.destroy(); R2.destroy();
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob("a fixc6 skeleton"));
done();
