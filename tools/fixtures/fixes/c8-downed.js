// C8: PLAYERDEATH's DOWNED x y: Prop_Downed, REVIVE, a heal, the fight's end, the time running out;
// the party's defeat: PARTYDEFEAT_PROG, PARTYDEFEAT=CHECKPOINT (PARTYDEFEATGOLD) and DEATH.
FIXFILE = "c8-downed";
var PS = CM.Common.interfaces.PhyStats;
var DN = CM.core.Downed;
var CP = CM.core.Checkpoints;
var TM = Tickable.TICKID_MOB;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixc8Keep");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixc8Keep#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var HALL = room(0, "Fixc8 great hall"), YARD = room(1, "Fixc8 yard"), CELL = room(2, "Fixc8 cell"), SHRINE = room(3, "Fixc8 shrine");
var DRx = CMClass.getAbility("Prop_DeathRules");
DRx.setMiscText("PLAYERDEATH=\"DOWNED 2 5\" TO=Fixc8Keep#2 HEAL=50");
X.addNonUninvokableEffect(DRx);
var V = CMClass.getMOB("GenMob");
V.setName("Fixc8pip");
V.baseState().setHitPoints(400); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(HALL, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var pal = CMClass.getMOB("GenMob"); pal.setName("Fixc8pal");
pal.baseState().setHitPoints(200); pal.recoverMaxState(); pal.resetToMaxState();
pal.bringToLife(HALL, true);
pal["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
var foes = [];
function ogre(R) { var o = CMClass.getMOB("GenMob"); o.setName("Fixc8ogre"); o.bringToLife(R, true); foes.push(o); return o; }
function fell(who, o) { o.setVictim(who); who.setVictim(o); who.curState().setHitPoints(0); CMLib.combat().postDeath(o, who, null); }
// down, not dead
var o1 = ogre(HALL);
out();
fell(V, o1);
var said = out();
check("down", DN.isDown(V) && (!V.amDead()) && V.location() == HALL && V.curState().getHitPoints() == 1 && o1.getVictim() == null && has(said, "You are down!"),
	"down " + DN.isDown(V) + " dead " + V.amDead() + " hp " + V.curState().getHitPoints() + " " + said);
check("no-defeat", !has(said, "Your party has fallen"), "the pal still stands");
// not attacked, and acts not
var hit = CMLib.combat().postAttack(o1, V, null);
var stand = HALL.okMessage(V, CMClass.getMsg(V, null, null, CMMsg.MSG_STAND, "<S-NAME> stand(s)."));
check("safe", (!hit) && (!stand) && V.curState().getHitPoints() == 1 && DN.isDown(V), "attacked " + hit + " stood " + stand);
// an ally's REVIVE
CMClass.getCommand("Revive").execute(pal, new java.util.ArrayList(java.util.Arrays.asList(["REVIVE", "Fixc8pip"])), 0);
check("revive", (!DN.isDown(V)) && V.curState().getHitPoints() == 40, "down " + DN.isDown(V) + " hp " + V.curState().getHitPoints());
// a heal of 5 or more
fell(V, o1);
CMLib.combat().postHealing(pal, V, null, 3, CMMsg.MSG_OK_ACTION, null);
var still = DN.isDown(V);
CMLib.combat().postHealing(pal, V, null, 6, CMMsg.MSG_OK_ACTION, null);
check("heal", still && (!DN.isDown(V)) && V.curState().getHitPoints() >= 6, "small heal " + still + " big heal " + DN.isDown(V) + " hp " + V.curState().getHitPoints());
// the fight over: up at 1
fell(V, o1);
var ticked = V.fetchEffect("Prop_Downed");
ticked.tick(V, TM);
var held = DN.isDown(V);
o1.destroy();
ticked.tick(V, TM);
check("over", held && (!DN.isDown(V)) && V.curState().getHitPoints() == 1, "held " + held + " down " + DN.isDown(V) + " hp " + V.curState().getHitPoints());
// the time runs out: the rest of the list (Prop_DeathRules TO=, a defeat)
V.resetToMaxState();
var o2 = ogre(HALL);
fell(V, o2);
var A = V.fetchEffect("Prop_Downed");
A.tick(V, TM);
var one = DN.isDown(V);
A.tick(V, TM);
check("time", one && (!DN.isDown(V)) && (!V.amDead()) && V.location() == CELL, "after one " + one + " down " + DN.isDown(V) + " in " + V.location());
// the party falls: the room's PARTYDEFEAT_PROG takes it
HALL.bringMobHere(V, false); HALL.bringMobHere(pal, false);
V.resetToMaxState(); pal.resetToMaxState();
var S = scriptOn(HALL, "PARTYDEFEAT_PROG\nmpsetvar FIXTEST FIXC8P $n\n~\n");
clearVar("FIXC8P");
out();
fell(pal, o2);
var palDown = DN.isDown(pal);
fell(V, o2);
said = out();
check("partydefeat-prog", palDown && DN.isDown(V) && DN.isDown(pal) && has(said, "Your party has fallen") && gvar("FIXC8P") == "Fixc8pip",
	"pal " + palDown + " pip " + DN.isDown(V) + " prog '" + gvar("FIXC8P") + "' " + said);
DN.revive(V, 100); DN.revive(pal, 100);
// PARTYDEFEAT=CHECKPOINT, a tenth of the gold
var page = CMProps.instance();
var oldPD = page.getProperty("PARTYDEFEAT"), oldGold = page.getProperty("PARTYDEFEATGOLD");
page.setProperty("PARTYDEFEAT", "RESTORE,CHECKPOINT");
page.setProperty("PARTYDEFEATGOLD", "10");
YARD.bringMobHere(V, false); YARD.bringMobHere(pal, false);
CP.set(V, SHRINE);
CMLib.beanCounter().giveSomeoneMoney(V, 100.0);
var gold0 = CMLib.beanCounter().getTotalAbsoluteNativeValue(V);
var o3 = ogre(YARD);
fell(pal, o3); fell(V, o3);
var gold1 = CMLib.beanCounter().getTotalAbsoluteNativeValue(V);
var slot = CM.core.save.SaveGame.currentSlot();
if (slot != null)
	check("checkpoint", DN.isDown(V) && has(out(), "brings back your last save"), "a save was there: " + slot);
else
	check("checkpoint", V.location() == SHRINE && pal.location() == SHRINE && (!DN.isDown(V)) && (!DN.isDown(pal)) && V.curState().getHitPoints() == 1
		&& Math.abs(gold1 - gold0 * 0.9) < 0.01 && !o3.isInCombat(), "pip in " + V.location() + " pal in " + pal.location() + " gold " + gold0 + " -> " + gold1);
// no checkpoint: DEATH, the last word (here TO=, the cell)
if (slot == null) {
	CP.clear(V);
	YARD.bringMobHere(V, false); YARD.bringMobHere(pal, false);
	V.resetToMaxState(); pal.resetToMaxState();
	fell(pal, o3); fell(V, o3);
	check("death", (!DN.isDown(V)) && (!DN.isDown(pal)) && V.location() == CELL, "pip in " + V.location() + " down " + DN.isDown(V) + " pal down " + DN.isDown(pal));
}
page.setProperty("PARTYDEFEAT", oldPD == null ? "DEATH" : oldPD);
page.setProperty("PARTYDEFEATGOLD", oldGold == null ? "10" : oldGold);
// no DOWNED: death as before, and no party defeat
var stray = CMClass.getMOB("GenMob"); stray.setName("Fixc8stray"); stray.bringToLife(otherRoom(), true);
check("rule", DN.rule(V) != null && DN.rule(V)[0] == 2 && DN.rule(V)[1] == 5 && DN.rule(stray) == null, "rule " + DN.rule(V) + " stray " + DN.rule(stray));
check("help", has(CMLib.help().getHelpText("PROP_DOWNED", M, true), "HEAL") && has(CMLib.help().getHelpText("REVIVE", M, false), "a tenth of their hit points")
	&& has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "PARTYDEFEAT_PROG"), "no help");

[V, pal, stray].concat(foes).forEach(function (m) { m.makePeace(true); if (m == V) m.setPlayerStats(null); m.destroy(); });
CMLib.map().obliterateMapArea(X);
done();
