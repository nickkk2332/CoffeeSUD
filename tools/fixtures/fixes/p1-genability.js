// P1: a GenAbility added as a plain effect: HERESTATS and MOCKABILITY at once; its SCRIPT reads its own text with EFFECTPARM(KEY) and $PARMS.
FIXFILE = "p1-genability";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var CS = CM.Common.interfaces.CharStats;
var R = scratchRoom(), R2 = scratchRoom();
var G = null, things = [], made = [];
try {
	G = CMClass.getAbility("GenAbility").copyOf();
	G.setStat("CLASS", "FixP1Haunted");
	G.setStat("NAME", "Fixp1 Haunting");
	G.setStat("HERESTATS", "dex+2");
	G.setStat("MOCKABILITY", "Prop_Invisibility");
	G.setStat("SCRIPT", "RAND_PROG 100\nif effectparm(FREQ == 5)\n mpsetvar FIXTEST P1A yes\nendif\nif effectparm(FREQ == 40)\n mpsetvar FIXTEST P1A loud\nendif\nmpsetvar FIXTEST P1B $PARMS\n~\n");
	// its script, on a room, with the text it was added with
	["P1A", "P1B"].forEach(clearVar);
	var P = CMClass.getAbility("FixP1Haunted"); P.setMiscText("FREQ=5 MSG=moan"); R.addNonUninvokableEffect(P);
	for (var i = 0; i < 3; i++) R.fetchEffect("FixP1Haunted").tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
	var a1 = gvar("P1A"), b1 = gvar("P1B");
	clearVar("P1A");
	var P2 = CMClass.getAbility("FixP1Haunted"); P2.setMiscText("FREQ=40"); R2.addNonUninvokableEffect(P2);
	for (var i = 0; i < 3; i++) R2.fetchEffect("FixP1Haunted").tick(R2, Tickable.TICKID_ROOM_BEHAVIOR);
	var a2 = gvar("P1A");
	check("parms", a1 == "yes" && b1 == "FREQ=5 MSG=moan" && a2 == "loud", a1 + " / " + b1 + " / " + a2);
	// HERESTATS on a mob it is added to
	var V = newMob("GenMob", R); made.push(V); V.recoverCharStats();
	var d0 = V.charStats().getStat(CS.STAT_DEXTERITY);
	var P3 = CMClass.getAbility("FixP1Haunted"); V.addNonUninvokableEffect(P3); V.recoverCharStats();
	var d1 = V.charStats().getStat(CS.STAT_DEXTERITY);
	check("herestats", d1 == d0 + 2, d0 + " -> " + d1);
	// MOCKABILITY on an item, and on a copy of it
	var I = CMClass.getItem("GenItem"); I.setName("a fixp1 lamp"); R.addItem(I); things.push(I);
	var vis0 = CMLib.flags().isInvisible(I);
	var P4 = CMClass.getAbility("FixP1Haunted"); I.addNonUninvokableEffect(P4); I.recoverPhyStats();
	var I2 = I.copyOf(); things.push(I2); I2.recoverPhyStats();
	var q1 = field(I.fetchEffect("FixP1Haunted"), "quietEffect"), q2 = field(I2.fetchEffect("FixP1Haunted"), "quietEffect");
	check("mockability", !vis0 && CMLib.flags().isInvisible(I) && CMLib.flags().isInvisible(I2) && q1 != null && q2 != null && q1 != q2, vis0 + " " + CMLib.flags().isInvisible(I) + " " + CMLib.flags().isInvisible(I2));
	check("help", has(CMLib.help().getHelpText("GENABILITY", M, true), "EFFECTPARM") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "EFFECTPARM"), "no help");
} finally {
	things.forEach(function (X) { X.destroy(); });
	made.forEach(function (X) { X.destroy(); });
	[R, R2].forEach(function (X) { var e = X.fetchEffect("FixP1Haunted"); if (e != null) X.delEffect(e); });
	if (G != null && CMClass.getAbility("FixP1Haunted") != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, CMClass.getAbility("FixP1Haunted"));
}
done();
