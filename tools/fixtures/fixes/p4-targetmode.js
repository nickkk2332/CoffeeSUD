// P4: GenAbility TARGETMODE (ALLENEMIES, ALLALLIES), MAXTARGETS and AOEMSG: each target its own cast and script event, the cost paid once.
FIXFILE = "p4-targetmode";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function count(t, p) { var n = 0, x = s(t), i = x.indexOf(p); while (i >= 0) { n++; i = x.indexOf(p, i + p.length); } return n; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var AB = CM.Abilities.interfaces.Ability;
var R = scratchRoom(), R2 = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixp4caster");
V.basePhyStats().setLevel(20); V.recoverPhyStats();
V.baseState().setMana(5000); V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
function reached(t) { return count(t, "FIXP4HIT") + count(t, "and fails"); } // a cast that fizzles still reached its target
function targeted(t) { return count(t, "uses an ability against") + count(t, "and fails"); } // each target's own cast, even one it resists (a save always keeps 5%) or that fizzles
var made = [], mobs = [];
function npc(name) { var m = newMob("GenMob", R); m.setName(name); m.basePhyStats().setLevel(1); m.recoverPhyStats(); mobs.push(m); return m; }
var F = npc("Fixp4ally"); F.setFollowing(V);
var E1 = npc("Fixp4enemya"), E2 = npc("Fixp4enemyb");
function gen(cls, quality, mode, extra) {
	var G = CMClass.getAbility("GenAbility").copyOf();
	G.setStat("CLASS", cls); G.setStat("NAME", cls); G.setStat("TRIGSTR", cls.toUpperCase());
	G.setStat("CLASSIFICATION", "" + AB.ACODE_SKILL); G.setStat("QUALITY", "" + quality);
	G.setStat("CANAFFECTMASK", "0"); G.setStat("CANTARGETMASK", "" + AB.CAN_MOBS); G.setStat("TARGETMODE", mode);
	G.setStat("SCRIPT", "FUNCTION_PROG ONCAST\nmpecho FIXP4HIT $t\n~\n");
	for (var k in extra) G.setStat(k, extra[k]);
	made.push(cls);
	var A = CMClass.getAbility(cls); A.setProficiency(100); V.addAbility(A);
	return V.fetchAbility(cls);
}
function spent(f) { var m0 = V.curState().getMana() + V.curState().getMovement(); f(); return m0 - (V.curState().getMana() + V.curState().getMovement()); }
function peace() { [V].concat(mobs).forEach(function (m) { m.makePeace(true); }); }
try {
	var A = gen("FixP4Thunderclap", AB.QUALITY_MALICIOUS, "ALLENEMIES", { "AOEMSG": "<S-NAME> clap(s) like thunder!" });
	check("field", s(A.getStat("TARGETMODE")) == "ALLENEMIES" && has(A.getStat("ALLXML"), "<TARGETMODE>ALLENEMIES</TARGETMODE>"), s(A.getStat("TARGETMODE")));
	var out = "", cost = spent(function () { said(); A.invoke(V, list(), null, false, 0); out = said(); });
	peace();
	check("all enemies", targeted(out) == 2 && reached(out) >= 1 && (has(out, "FIXP4HIT Fixp4enemya") || has(out, "FIXP4HIT Fixp4enemyb")) && !has(out, "FIXP4HIT Fixp4ally") && !has(out, "FIXP4HIT Fixp4caster")
		&& count(out, "clap(s) like thunder!") == 1, out.substring(0, 300)); // the session keeps the message raw
	// the cost of one cast
	A.setStat("TARGETMODE", "SINGLE");
	var cost1 = spent(function () { said(); A.invoke(V, list("Fixp4enemya"), null, false, 0); said(); });
	peace();
	check("cost once", cost == cost1, cost + " vs " + cost1);
	// MAXTARGETS
	A.setStat("TARGETMODE", "ALLENEMIES"); A.setStat("MAXTARGETS", "@x2-1");
	said(); A.invoke(V, list(), null, false, 0); var o2 = said();
	peace();
	check("maxtargets", targeted(o2) == 1, o2.substring(0, 300));
	// ALLALLIES: the caster's group there
	var B = gen("FixP4Cadence", AB.QUALITY_BENEFICIAL_OTHERS, "ALLALLIES", {});
	said(); B.invoke(V, list(), null, false, 0); var o3 = said();
	check("all allies", reached(o3) == 2 && !has(o3, "FIXP4HIT Fixp4enemy"), o3.substring(0, 300));
	// nobody there: refused before any cost
	R2.bringMobHere(V, false);
	var o4 = "", c4 = spent(function () { said(); A.invoke(V, list(), null, false, 0); o4 = said(); });
	check("nobody", has(o4, "There is no one here for") && c4 == 0, o4 + " / " + c4);
	check("help", has(CMLib.help().getHelpText("GENABILITY", M, true), "TARGETMODE"), "no help");
} finally {
	peace();
	mobs.forEach(function (m) { m.destroy(); });
	V.setPlayerStats(null); V.destroy();
	made.forEach(function (c) { var X = CMClass.getAbility(c); if (X != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, X); });
}
done();
