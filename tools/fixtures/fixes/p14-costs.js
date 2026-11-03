// P14: ACTIONCOST, COMBATACTIONCOST and COMPONENTS in a generic ability's own record (GenAbility, craft and gather skills, tweaks).
FIXFILE = "p14-costs";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var AB = CM.Abilities.interfaces.Ability;
var RES = CM.core.Resources;
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixp14caster");
V.basePhyStats().setLevel(20); V.recoverPhyStats();
V.baseState().setMana(5000); V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var made = [];
function comps(id) { return CMLib.ableComponents().getAbilityComponentMap().get(id.toUpperCase()); }
function near(a, b) { return Math.abs(a - b) < 0.0001; }
try {
	// a spell: its own costs, in and out of a fight
	var G = CMClass.getAbility("GenAbility").copyOf();
	G.setStat("CLASS", "FixP14Ward"); made.push("FixP14Ward");
	G.setStat("NAME", "Fixp14 Ward"); G.setStat("TRIGSTR", "FIXP14WARD");
	G.setStat("CLASSIFICATION", "" + AB.ACODE_SPELL); G.setStat("QUALITY", "" + AB.QUALITY_BENEFICIAL_SELF);
	G.setStat("CANAFFECTMASK", "0"); G.setStat("CANTARGETMASK", "0");
	var A = CMClass.getAbility("FixP14Ward");
	var def = A.castingTime(V, list()), cdef = A.combatCastingTime(V, list());
	check("default", near(def, CM.core.CMProps.getSkillActionCost("FixP14Ward")) && s(A.getStat("ACTIONCOST")) == "-1", def + " " + A.getStat("ACTIONCOST"));
	G.setStat("ACTIONCOST", "0.5"); G.setStat("COMBATACTIONCOST", "2.0");
	check("costs", near(A.castingTime(V, list()), 0.5) && near(A.combatCastingTime(V, list()), 2.0) && near(A.checkedCastingCost(V, list()), 0.5),
		A.castingTime(V, list()) + " " + A.combatCastingTime(V, list()));
	G.setStat("QUALITY", "" + AB.QUALITY_MALICIOUS);
	check("malicious uses the combat cost", near(A.checkedCastingCost(V, list()), 2.0), A.checkedCastingCost(V, list()));
	G.setStat("QUALITY", "" + AB.QUALITY_BENEFICIAL_SELF);
	G.setStat("COMBATACTIONCOST", "-1");
	check("back to the default", near(A.combatCastingTime(V, list()), cdef) && s(A.getStat("COMBATACTIONCOST")) == "-1", A.combatCastingTime(V, list()));
	// components in the record
	G.setStat("COMPONENTS", "(inventory:consumed:1:fixsalt:)");
	var C = comps("FixP14Ward");
	check("component map", C != null && C.size() == 1, s(C));
	A = CMClass.getAbility("FixP14Ward"); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("FixP14Ward");
	said(); var r1 = A.invoke(V, list(), null, false, 0); var o1 = said();
	check("refused without", s(r1) == "false" && has(o1, "requirements to use this") && has(o1.toLowerCase(), "fixsalt"), r1 + " " + o1.replace(/:/g, ";"));
	var salt = CMClass.getBasicItem("GenItem"); salt.setName("a pinch of fixsalt"); V.addItem(salt);
	said(); A.invoke(V, list(), null, false, 0); var o2 = said();
	check("consumed with", !has(o2, "requirements to use this") && V.findItem("fixsalt") == null, o2);
	// the record round trip
	var x = s(G.getStat("ALLXML"));
	check("allxml", has(x, "<ACTIONCOST>0.5</ACTIONCOST>") && has(x, "<COMBATACTIONCOST>-1</COMBATACTIONCOST>") && has(x, "fixsalt"), x.substring(0, 200));
	var G2 = CMClass.getAbility("GenAbility").copyOf(); made.push("FixP14Ward2");
	G2.setStat("ALLXML", x.split("FixP14Ward").join("FixP14Ward2"));
	CMClass.addClass(CM.core.CMClass.CMObjectType.ABILITY, G2); // as the world load does
	RES.removeResource("COMPONENT_MAP");
	var A2 = CMClass.getAbility("FixP14Ward2");
	check("loaded", A2 != null && near(A2.castingTime(V, list()), 0.5) && comps("FixP14Ward2") != null, s(A2));
	G.setStat("COMPONENTS", "");
	check("components cleared", comps("FixP14Ward") == null && comps("FixP14Ward2") != null, "still there");
	// data craft and gather skills
	var K = CMClass.getAbility("GenCraftSkill").copyOf(); K.setStat("CLASS", "FixP14Craft"); made.push("FixP14Craft");
	K.setStat("ACTIONCOST", "3"); K.setStat("COMPONENTS", "(held:kept:1:fixchisel:)");
	var KA = CMClass.getAbility("FixP14Craft");
	check("craft skill", near(KA.castingTime(V, list()), 3) && comps("FixP14Craft") != null && has(K.getStat("ALLXML"), "<ACTIONCOST>3.0</ACTIONCOST>"), KA.castingTime(V, list()));
	var H = CMClass.getAbility("GenGatheringSkill").copyOf(); H.setStat("CLASS", "FixP14Gather"); made.push("FixP14Gather");
	H.setStat("COMBATACTIONCOST", "1.5"); H.setStat("COMPONENTS", "(held:kept:1:fixsieve:)");
	var HA = CMClass.getAbility("FixP14Gather");
	check("gather skill", near(HA.combatCastingTime(V, list()), 1.5) && comps("FixP14Gather") != null, HA.combatCastingTime(V, list()));
	// a tweak: its record, and the proxy over the original
	var W = CMClass.getAbility("GenTweakAbility").copyOf(); W.setStat("CLASS", "FixP14Tweak"); made.push("FixP14Tweak");
	W.setStat("ORIGINAL", "Spell_Light"); W.setStat("ACTIONCOST", "0.25"); W.setStat("COMPONENTS", "(inventory:consumed:1:fixwick:)");
	var WA = CMClass.getAbility("FixP14Tweak"), L = CMClass.getAbility("Spell_Light");
	check("tweak record", near(WA.castingTime(V, list()), 0.25) && near(WA.combatCastingTime(V, list()), L.combatCastingTime(V, list()))
		&& near(WA.checkedCastingCost(V, list()), 0.25) && comps("FixP14Tweak") != null && has(W.getStat("ALLXML"), "<ACTIONCOST>0.25</ACTIONCOST>"),
		WA.castingTime(V, list()) + " " + W.getStat("ALLXML"));
	var mk = W.getClass().getMethod("createProxy", java.lang.String, java.lang.String);
	var P = mk.invoke(null, "Spell_Light", "FixP14Tweak");
	check("tweak proxy", near(P.checkedCastingCost(V, list()), 0.25) && near(P.castingTime(V, list()), 0.25), P.checkedCastingCost(V, list()));
	W.setStat("ACTIONCOST", "-1");
	check("tweak default", near(WA.castingTime(V, list()), L.castingTime(V, list())) && s(W.getStat("ACTIONCOST")) == "-1", WA.castingTime(V, list()));
	check("help", has(CMLib.help().getHelpText("GENABILITY", M, true), "COMBATACTIONCOST") && has(CMLib.help().getHelpText("GENCRAFTSKILL", M, true), "ACTIONCOST"), "no help");
} finally {
	V.setPlayerStats(null); V.destroy();
	made.forEach(function (c) { var X = CMClass.getAbility(c); if (X != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, X); });
	RES.removeResource("COMPONENT_MAP");
}
done();
