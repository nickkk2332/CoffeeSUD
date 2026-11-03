// P13: world/perks.ini: perks and their picks by level (PERKS, PERK), HASPERK() and MPPERK, and the traits and flaws of the TRAITS creation step.
FIXFILE = "p13-perks";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var PK = CM.core.Perks;
var CS = CM.Common.interfaces.CharStats;
var PF = PK.file();
var had = PF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(PF.toPath()), "UTF-8")) : null;
var R0 = M.location();
var made = [];
function hero(name) {
	var h = newMob("GenMob", R0); h.setName(name);
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	var F = CMClass.getCharClass("Fighter"); h.baseCharStats().setCurrentClass(F); h.baseCharStats().setClassLevel(F, 1);
	h.basePhyStats().setLevel(1); h.recoverCharStats(); h.recoverPhyStats();
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	made.push(h);
	return h;
}
function level(h, n) { h.basePhyStats().setLevel(n); h.baseCharStats().setClassLevel(CMClass.getCharClass("Fighter"), n); h.recoverPhyStats(); h.recoverCharStats(); }
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY");
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\^./g, "").replace(/\s+/g, " "); }
var CC = CMLib.login(), LSI = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function lget(name) { var f = LSI.getDeclaredField(name); f.setAccessible(true); return f.get(lo); }
function step(name, input) {
	if (input != null) lset("lastInput", input);
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name && ms[i].getParameterCount() == 2) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
var PERK = CMClass.getCommand("PerkCmd");
try {
	write(PF, ["# fixp13",
		"PICKS=GENERAL every 3 levels; CLASS:Fighter at 1,5,10",
		"TRAITS=2",
		'PERK_FIXHARDY NAME="Fixp13 Hardy" POOL=GENERAL HELP="Hard to kill."',
		'PERK_FIXTOUGH NAME="Fixp13 Tough" POOL=GENERAL MASK="-LEVEL +>=3" REQUIRES=PERK_FIXHARDY COST=1 REWARDS="1 CHARISMA"',
		'PERK_FIXBLADE NAME="Fixp13 Blade" POOL=CLASS MASK="-CLASS +Fighter"',
		'TRAIT_FIXLUCKY NAME="Fixp13 Lucky" POOL=TRAIT COST=2 REWARDS="1 CHARISMA"',
		'TRAIT_FIXBADBACK NAME="Fixp13 Bad back" POOL=TRAIT COST=-1',
		'TRAIT_FIXKEEN NAME="Fixp13 Keen" POOL=TRAIT COST=1',
		""].join("\n"));
	var P = PK.plan();
	check("read", P.perks.size() == 6 && P.picks.size() == 2 && P.traits == 2, P.perks.size() + " " + P.picks.size() + " " + P.traits);
	var H = hero("Fixp13hero"); H.setSession(VS); VS.setMob(H);
	var hardy = PK.perk("Fixp13 Hardy"), tough = PK.perk("PERK_FIXTOUGH"), blade = PK.perk("fixp13 bl");
	check("level 1", PK.earned(H, "GENERAL") == 0 && PK.earned(H, "CLASS") == 1 && PK.why(H, blade, false) == null
		&& has(PK.why(H, hardy, false), "no general pick left") && has(PK.why(H, tough, false), "needs"), s(PK.why(H, hardy, false)));
	said(); PERK.execute(H, list("PERKS"), 0); var o1 = said();
	check("list", has(o1, "You can take") && has(o1, "Fixp13 Blade") && has(o1, "Fixp13 Hardy (no general pick left)") && !has(o1, "Fixp13 Lucky"), o1.substring(0, 300));
	level(H, 3);
	check("level 3", PK.left(H, "GENERAL") == 1 && PK.why(H, hardy, false) == null && has(PK.why(H, tough, false), "needs Fixp13 Hardy") && has(PK.levelNote(H), "PERKS"),
		s(PK.why(H, tough, false)));
	said(); PERK.execute(H, list("PERK", "fixp13", "hardy"), 0); var o2 = said();
	check("taken", H.findTattoo("PERK_FIXHARDY") != null && has(o2, "You take Fixp13 Hardy") && has(PK.why(H, tough, false), "no general pick left"), o2 + " " + PK.why(H, tough, false));
	PERK.execute(H, list("PERK", "Fixp13 Tough"), 0); var o3 = said();
	check("refused", has(o3, "You cannot take Fixp13 Tough: no general pick left") && H.findTattoo("PERK_FIXTOUGH") == null, o3);
	// MPPERK and HASPERK()
	var cha = H.baseCharStats().getStat(CS.STAT_CHARISMA);
	clearVar("FIXP13H"); clearVar("FIXP13N");
	var sm = scriptedMob("Fixp13scribe", "ONCE_PROG 100\nmpperk Fixp13hero PERK_FIXTOUGH\nmpperk Fixp13hero -PERK_FIXBLADE\nif hasperk(Fixp13hero PERK_FIXTOUGH)\nmpsetvar FIXTEST FIXP13H yes\nendif\nif hasperk(Fixp13hero Fixp13 Blade)\nmpsetvar FIXTEST FIXP13N yes\nendif\n~\n");
	made.push(sm);
	H.addTattoo("PERK_FIXBLADE");
	tickMob(sm); tickMob(sm);
	check("mpperk", H.findTattoo("PERK_FIXTOUGH") != null && H.findTattoo("PERK_FIXBLADE") == null && H.baseCharStats().getStat(CS.STAT_CHARISMA) == cha + 1, H.baseCharStats().getStat(CS.STAT_CHARISMA) + "/" + cha);
	check("hasperk", gvar("FIXP13H") == "yes" && gvar("FIXP13N") != "yes", gvar("FIXP13H") + gvar("FIXP13N"));
	check("trait after creation", has(PK.why(H, PK.perk("Fixp13 Keen"), false), "a trait"), s(PK.why(H, PK.perk("Fixp13 Keen"), false)));
	// the TRAITS creation step
	var N = hero("Fixp13new"); N.setSession(VS); VS.setMob(N); lset("mob", N); lget("queue").clear(); lset("resume", null);
	said();
	var r1 = step("charcrTraitsStart"), o4 = said();
	check("trait menu", s(r1) == "INPUT_REQUIRED" && has(o4, "points left: 2") && has(o4, "Fixp13 Lucky (costs 2)") && has(o4, "Fixp13 Bad back (flaw, gives 1)"), o4.substring(0, 300));
	step("charcrTraitsPicked", "lucky");
	step("charcrTraitsPicked", "keen"); var o5 = said();
	check("too dear", N.findTattoo("TRAIT_FIXLUCKY") != null && N.findTattoo("TRAIT_FIXKEEN") == null && has(o5, "not enough trait points"), o5.substring(0, 200));
	step("charcrTraitsPicked", "bad back"); step("charcrTraitsPicked", "keen");
	check("flaw pays", N.findTattoo("TRAIT_FIXBADBACK") != null && N.findTattoo("TRAIT_FIXKEEN") != null && PK.left(N, "TRAIT") == 0, PK.left(N, "TRAIT"));
	step("charcrTraitsPicked", "fixp13 bad"); said();
	step("charcrTraitsPicked", "done"); var o6 = said();
	check("overspent", N.findTattoo("TRAIT_FIXBADBACK") == null && has(o6, "spent more points than you have") && s(lget("state")) == "CHARCR_TRAITSSTART", o6);
	step("charcrTraitsPicked", "keen"); step("charcrTraitsPicked", "done");
	check("done", s(lget("state")) == "CHARCR_FACTIONNEXT" && PK.left(N, "TRAIT") == 0, s(lget("state")));
	var c0 = N.baseCharStats().getStat(CS.STAT_CHARISMA);
	PK.traitRewards(N);
	check("trait rewards", N.baseCharStats().getStat(CS.STAT_CHARISMA) == c0 + 1, N.baseCharStats().getStat(CS.STAT_CHARISMA) + "/" + c0);
	check("help", has(CMLib.help().getHelpText("PERKS", M, false), "PERK") && has(CMLib.help().getHelpText("PERKSINI", M, true), "perks.ini"), "no help");
} finally {
	if (had != null) write(PF, had); else if (PF.isFile()) PF["delete"]();
	made.forEach(function (m) { m.setSession(null); if (m.playerStats() != null) m.setPlayerStats(null); m.destroy(); });
	clearVar("FIXP13H"); clearVar("FIXP13N");
}
done();
