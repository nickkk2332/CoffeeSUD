// P3: a GenAbility's CANCAST, ONCAST, ONTICK, ONEXPIRE and ONLEARN hooks; CASTFAILMSG and $t in CASTMASK; a GenTrap's ONTRIGGER and ONDISARM.
FIXFILE = "p3-hooks";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var AB = CM.Abilities.interfaces.Ability;
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixp3caster");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
V.baseState().setMana(1000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
V.playerStats().getSetSecurityFlags("SUPERSKILL"); // Hook assertions need successful proficiency checks.
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
var made = [];
function gen(cls, script, extra) {
	var G = CMClass.getAbility("GenAbility").copyOf();
	G.setStat("CLASS", cls); G.setStat("NAME", cls); G.setStat("TRIGSTR", cls.toUpperCase());
	G.setStat("CLASSIFICATION", "" + AB.ACODE_SKILL); G.setStat("QUALITY", "" + AB.QUALITY_BENEFICIAL_SELF);
	G.setStat("CANAFFECTMASK", "" + AB.CAN_MOBS); G.setStat("CANTARGETMASK", "" + AB.CAN_MOBS); G.setStat("TICKSOVERRIDE", "10");
	G.setStat("SCRIPT", script);
	for (var k in extra) G.setStat(k, extra[k]);
	made.push(cls);
	var A = CMClass.getAbility(cls); A.setProficiency(100); V.addAbility(A);
	return V.fetchAbility(cls);
}
function cast(A) { said(); var ok = A.invoke(V, list(), null, false, 0); return [s(ok), said()]; }
try {
	["P3LEARN", "P3CAST", "P3TICK", "P3EXP"].forEach(clearVar);
	var A = gen("FixP3Moonblade", "FUNCTION_PROG CANCAST\nif hastattoo($n FIXP3DARK)\n mpecho The moon is hidden.\n return false\nendif\n~\n"
		+ "FUNCTION_PROG ONCAST\nmpsetvar FIXTEST P3CAST $t\n~\nFUNCTION_PROG ONTICK\nmpsetvar FIXTEST P3TICK yes\n~\n"
		+ "FUNCTION_PROG ONEXPIRE\nmpsetvar FIXTEST P3EXP yes\n~\nFUNCTION_PROG ONLEARN\nmpsetvar FIXTEST P3LEARN $n\n~\n", {});
	check("onlearn", gvar("P3LEARN") == "Fixp3caster", gvar("P3LEARN"));
	// CANCAST refuses
	V.addTattoo("FIXP3DARK");
	var c1 = cast(A);
	check("cancast", c1[0] == "false" && has(c1[1], "The moon is hidden.") && V.fetchEffect("FixP3Moonblade") == null && gvar("P3CAST") == "", c1[1]);
	V.delTattoo(V.findTattoo("FIXP3DARK"));
	// ONCAST, ONTICK, ONEXPIRE
	var c2 = cast(A);
	var E = V.fetchEffect("FixP3Moonblade");
	if (E != null) { E.tick(V, Tickable.TICKID_MOB); E.unInvoke(); }
	check("lifecycle", c2[0] == "true" && E != null && gvar("P3CAST") == "Fixp3caster" && gvar("P3TICK") == "yes" && gvar("P3EXP") == "yes",
		c2[0] + " " + (E != null) + " " + gvar("P3CAST") + " " + gvar("P3TICK") + " " + gvar("P3EXP") + " / " + c2[1]);
	// CASTMASK sees the target as $t; CASTFAILMSG
	var B = gen("FixP3Shadowstep", "", { "CASTMASK": "-IF \"isname($t Fixp3nobody)\"", "CASTFAILMSG": "The shadows will not hide <T-NAME>." });
	var c3 = cast(B);
	B.setStat("CASTMASK", "-IF \"isname($t Fixp3caster)\"");
	var c4 = cast(B);
	check("castmask", c3[0] == "false" && has(c3[1], "The shadows will not hide") && !has(c3[1], "requirements") && c4[0] == "true" && V.fetchEffect("FixP3Shadowstep") != null, c3[1] + " / " + c4[0]);
	// a GenTrap's ONTRIGGER and ONDISARM
	["P3TRIG", "P3DIS"].forEach(clearVar);
	var T = CMClass.getAbility("GenTrap").copyOf();
	T.setStat("CLASS", "FixP3Snare"); made.push("FixP3Snare");
	T.setStat("SCRIPT", "FUNCTION_PROG ONTRIGGER\nmpsetvar FIXTEST P3TRIG $n\n~\nFUNCTION_PROG ONDISARM\nmpsetvar FIXTEST P3DIS yes\n~\n");
	var T1 = CMClass.getAbility("FixP3Snare");
	var box = CMClass.getItem("GenItem"); box.setName("a fixp3 box"); R.addItem(box);
	var trapper = newMob("GenMob", R); trapper.setName("a fixp3 trapper"); trapper.basePhyStats().setLevel(1); trapper.recoverPhyStats();
	T1.setInvoker(trapper); box.addNonUninvokableEffect(T1);
	// a monster at home is exempt from its own area's traps, so the player springs it
	for (var i = 0; i < 30 && gvar("P3TRIG") == ""; i++) { T1.spring(V); }
	var t1 = gvar("P3TRIG"), d0 = gvar("P3DIS");
	T1.disable();
	check("trap", t1 == "Fixp3caster" && d0 == "" && gvar("P3DIS") == "yes", t1 + " / " + d0 + " / " + gvar("P3DIS"));
	box.destroy(); trapper.destroy();
	check("help", has(CMLib.help().getHelpText("GENABILITY", M, true), "CANCAST") && has(CMLib.help().getHelpText("GENTRAP", M, true), "ONDISARM"), "no help");
} finally {
	V.setPlayerStats(null); V.destroy();
	made.forEach(function (c) { var X = CMClass.getAbility(c); if (X != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, X); });
}
done();
