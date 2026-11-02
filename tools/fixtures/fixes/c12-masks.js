// C12: Prop_AddDamage TARGETMASK= and LEECH=; SOURCEMASK= on Prop_AbsorbDamage, Prop_HaveResister, Prop_WearResister.
FIXFILE = "c12-masks";
var Wp = CM.Items.interfaces.Weapon;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
function npc(name, race, hp) {
	var m = newMob("GenMob", R);
	m.setName(name);
	if (race != null) m.baseCharStats().setMyRace(CMClass.getRace(race));
	m.baseState().setHitPoints(hp);
	m.recoverCharStats(); m.recoverPhyStats(); m.recoverMaxState(); m.resetToMaxState();
	return m;
}
function arm(m, name) { var w = CMClass.getWeapon("Sword"); w.setName(name); m.addItem(w); w.wearAt(CM.Items.interfaces.Wearable.WORN_WIELD); m.recoverPhyStats(); return w; }
function hit(a, t, n, w) { CMLib.combat().postDamage(a, t, w, n, CMMsg.MASK_ALWAYS | CMMsg.TYP_WEAPONATTACK, Wp.TYPE_SLASHING, "<S-NAME> <DAMAGE> <T-NAME>."); }
var V = npc("Fixc12pip", null, 500);
V.curState().setHitPoints(100);
var sun = arm(V, "Fixc12 sunblade");
var AD = CMClass.getAbility("Prop_AddDamage"); AD.setMiscText("+10 TARGETMASK=\"-NAMES +Fixc12bones\" LEECH=50%"); sun.addNonUninvokableEffect(AD);
var bones = npc("Fixc12bones", null, 300);
var skel = npc("Fixc12skeleton", "Skeleton", 10);
var man = npc("Fixc12man", null, 300);
check("undead", CMLib.masking().maskCheck("-RACECAT +Undead", skel, true) && !CMLib.masking().maskCheck("-RACECAT +Undead", man, true), "the mask and a skeleton");
hit(V, bones, 20, sun);
check("bane", bones.curState().getHitPoints() == 270 && V.curState().getHitPoints() == 115, "bones " + bones.curState().getHitPoints() + " pip " + V.curState().getHitPoints());
hit(V, man, 20, sun);
check("not-bane", man.curState().getHitPoints() == 280 && V.curState().getHitPoints() == 115, "man " + man.curState().getHitPoints() + " pip " + V.curState().getHitPoints());
check("kept", has(AD.text(), "TARGETMASK=") && s(AD.text()).indexOf("LEECH=50%") >= 0, "the text lost them: " + AD.text());
// absorption against dragons only
var wyrm = npc("Fixc12wyrm", "Dragon", 300), wsword = arm(wyrm, "Fixc12 claw"), msword = arm(man, "Fixc12 blade");
var knight = npc("Fixc12knight", null, 300);
var AB = CMClass.getAbility("Prop_AbsorbDamage"); AB.setMiscText("50% +ALL SOURCEMASK=\"-RACECAT +Dragon\""); knight.addNonUninvokableEffect(AB);
hit(wyrm, knight, 40, wsword);
var afterWyrm = knight.curState().getHitPoints();
hit(man, knight, 40, msword);
check("absorb", afterWyrm == 280 && knight.curState().getHitPoints() == 240, "after the wyrm " + afterWyrm + ", after the man " + knight.curState().getHitPoints());
// a resister, carried and worn
var guard = npc("Fixc12guard", null, 300);
var ring = CMClass.getItem("GenItem"); ring.setName("Fixc12 ring"); guard.addItem(ring);
var HR = CMClass.getAbility("Prop_HaveResister"); HR.setMiscText("weapons 100% SOURCEMASK=\"-RACECAT +Dragon\""); ring.addNonUninvokableEffect(HR);
// the resister's own check, on a blow by each: it takes the slash out of the dragon's only
function blow(a, t, w) {
	var m = CMClass.getMsg(a, t, w, CMMsg.MSG_OK_VISUAL, null, CMMsg.MASK_ALWAYS | CMMsg.TYP_DAMAGE, null, CMMsg.MSG_OK_VISUAL, null);
	m.setValue(40);
	return m;
}
var mw = blow(wyrm, guard, wsword); HR.okMessage(guard, mw);
var mm = blow(man, guard, msword); HR.okMessage(guard, mm);
check("resist", mw.value() < 40 && mm.value() == 40 && field(HR, "sourceMask") != null, "the wyrm's blow " + mw.value() + ", the man's " + mm.value());
var shield = CMClass.getArmor("GenShield"); shield.setName("Fixc12 shield");
var WR = CMClass.getAbility("Prop_WearResister"); WR.setMiscText("weapons 100% SOURCEMASK=\"-RACECAT +Dragon\"");
check("wear", field(WR, "sourceMask") != null, "Prop_WearResister did not read SOURCEMASK");
check("help", has(CMLib.help().getHelpText("PROP_ADDDAMAGE", M, true), "LEECH") && has(CMLib.help().getHelpText("PROP_ABSORBDAMAGE", M, true), "SOURCEMASK")
	&& has(CMLib.help().getHelpText("PROP_HAVERESISTER", M, true), "SOURCEMASK"), "no help");

[V, bones, skel, man, wyrm, knight, guard].forEach(function (m) { m.makePeace(true); m.destroy(); });
R.destroy();
done();
