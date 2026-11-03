// P19 (casts are auto, so the roll cannot fizzle): SUMMON, SUMMONCOUNT, SUMMONLEVEL and SUMMONFLAGS on a GenAbility: summons that follow, assist, go when the effect ends or the caster does, and stay out of saves.
FIXFILE = "p19-summon";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var AB = CM.Abilities.interfaces.Ability;
var MOBI = CM.MOBS.interfaces.MOB;
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixp19caster");
V.basePhyStats().setLevel(20); V.recoverPhyStats();
V.baseState().setMana(5000); V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var made = [], extra = [];
// a wolf in the catalog
var wolf = CMClass.getMOB("GenMob"); wolf.setName("a fixp19 grey wolf"); wolf.basePhyStats().setLevel(3); wolf.recoverPhyStats();
CMLib.catalog().addCatalog(wolf);
var catName = s(wolf.Name());
function gen(cls, extraStats) {
	var G = CMClass.getAbility("GenAbility").copyOf();
	G.setStat("CLASS", cls); G.setStat("NAME", cls); G.setStat("TRIGSTR", cls.toUpperCase());
	G.setStat("CLASSIFICATION", "" + AB.ACODE_SPELL); G.setStat("QUALITY", "" + AB.QUALITY_BENEFICIAL_SELF);
	G.setStat("CANAFFECTMASK", "" + AB.CAN_MOBS); G.setStat("CANTARGETMASK", "0");
	for (var k in extraStats) G.setStat(k, extraStats[k]);
	made.push(cls);
	var A = CMClass.getAbility(cls); A.setProficiency(100); V.addAbility(A);
	return [G, V.fetchAbility(cls)];
}
function summonsIn(room) { var o = []; for (var i = 0; i < room.numInhabitants(); i++) { var m = room.fetchInhabitant(i); if (m.fetchEffect("Prop_Summoned") != null) o.push(m); } return o; }
function followersOf(P) {
	var m = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState").getDeclaredMethod("followersOf", java.lang.Class.forName("com.planet_ink.coffee_mud.MOBS.interfaces.MOB")); m.setAccessible(true);
	return m.invoke(null, P);
}
try {
	var GA = gen("FixP19Pack", { "SUMMON": catName, "SUMMONCOUNT": "1+(@x1/10)", "SUMMONLEVEL": "@x1/2",
		"SUMMONFLAGS": "follow, assist dismissonexpire bind nosave junk", "TICKSOVERRIDE": "12" });
	var G = GA[0], A = GA[1];
	check("flags", s(G.getStat("SUMMONFLAGS")) == "FOLLOW ASSIST NOSAVE DISMISSONEXPIRE BIND" && has(G.getStat("ALLXML"), "<SUMMONCOUNT>1+(@x1/10)</SUMMONCOUNT>"), G.getStat("SUMMONFLAGS"));
	said(); A.invoke(V, list(), null, true, 0); var o1 = said();
	var W = summonsIn(R);
	check("three wolves", W.length == 3 && has(o1, "appear(s)!"), W.length + " " + o1.substring(0, 200));
	check("level and follow", W.length == 3 && W[0].phyStats().level() == 10 && W[0].amFollowing() == V && s(W[0].Name()) == catName
		&& !W[0].isAttributeSet(MOBI.Attrib.AUTOASSIST), W.length ? W[0].phyStats().level() + " " + W[0].amFollowing() : "none");
	var P = W[0].fetchEffect("Prop_Summoned");
	check("prop", P != null && has(P.text(), "ID=FixP19Pack") && has(P.text(), "TICKS=") && P.invoker() == V, P == null ? "none" : P.text());
	check("nosave", W.length == 3 && !CMLib.flags().isSavable(W[0]) && followersOf(V).size() == 0, s(followersOf(V)));
	// the effect ends: they go
	var E = V.fetchEffect("FixP19Pack");
	check("effect", E != null, "no effect on the caster");
	said(); if (E != null) E.unInvoke(); var o2 = said();
	check("dismissed with the effect", summonsIn(R).length == 0 && W[0].amDestroyed() && has(o2, "depart(s)"), summonsIn(R).length + " " + o2.substring(0, 200));
	// TICKS run out (after a restore the list on the effect is gone, the timer is not)
	A.invoke(V, list(), null, true, 0);
	W = summonsIn(R);
	var P2 = W[0].fetchEffect("Prop_Summoned"); P2.setMiscText("ID=FixP19Pack TICKS=2 DISMISSONEXPIRE");
	tickMob(W[0]);
	var stillThere = !W[0].amDestroyed();
	tickMob(W[0]);
	check("ticks", stillThere && W[0].amDestroyed() && s(P2.text()) == "ID=FixP19Pack TICKS=0 DISMISSONEXPIRE", P2.text());
	E = V.fetchEffect("FixP19Pack"); if (E != null) E.unInvoke();
	// FOLLOW without ASSIST, kept in saves, BIND: gone when the caster is
	var C2 = newMob("GenMob", R); C2.setName("Fixp19other"); C2.basePhyStats().setLevel(20); C2.recoverPhyStats();
	C2.baseState().setMana(5000); C2.recoverMaxState(); C2.resetToMaxState(); extra.push(C2);
	var GB = gen("FixP19Hound", { "SUMMON": "GenMob", "SUMMONFLAGS": "FOLLOW BIND", "CANAFFECTMASK": "0" });
	var B = CMClass.getAbility("FixP19Hound"); B.setProficiency(100); C2.addAbility(B); B = C2.fetchAbility("FixP19Hound");
	B.invoke(C2, list(), null, true, 0);
	W = summonsIn(R);
	check("follow, no assist", W.length == 1 && W[0].amFollowing() == C2 && W[0].isAttributeSet(MOBI.Attrib.AUTOASSIST)
		&& CMLib.flags().isSavable(W[0]) && followersOf(C2).size() == 1 && !has(W[0].fetchEffect("Prop_Summoned").text(), "TICKS"), W.length);
	tickMob(W[0]);
	var kept = !W[0].amDestroyed();
	C2.destroy();
	tickMob(W[0]);
	check("bind", kept && W[0].amDestroyed(), kept);
	// ASSIST without FOLLOW: joins the caster's fight
	var foe = newMob("GenMob", R); foe.setName("Fixp19foe"); extra.push(foe);
	var GC = gen("FixP19Ally", { "SUMMON": "GenMob", "SUMMONFLAGS": "ASSIST", "CANAFFECTMASK": "0" });
	V.setVictim(foe);
	GC[1].invoke(V, list(), null, true, 0);
	W = summonsIn(R);
	check("assist", W.length == 1 && W[0].getVictim() == foe && W[0].amFollowing() == null, W.length);
	V.makePeace(true); W.forEach(function (m) { m.makePeace(true); m.destroy(); }); foe.makePeace(true);
	check("help", has(CMLib.help().getHelpText("GENABILITY", M, true), "SUMMONFLAGS") && CMClass.getAbility("Prop_Summoned") != null, "no help");
} finally {
	summonsIn(R).forEach(function (m) { m.destroy(); });
	extra.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	V.setPlayerStats(null); V.destroy();
	made.forEach(function (c) { var X = CMClass.getAbility(c); if (X != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, X); });
	CMLib.catalog().delCatalog(wolf);
}
done();
