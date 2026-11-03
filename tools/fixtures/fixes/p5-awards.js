// P5: achievement rewards SCRIPT(), EFFECT(), FACTION(), VAR() and RECIPE(): given, described, written back, and refused when wrong.
FIXFILE = "p5-awards";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var A = CMLib.achievements();
var KR = CM.core.KnownRecipes;
var R = scratchRoom();
var F = CMLib.factions().factions().nextElement(), fid = s(F.factionID());
var H = newMob("GenMob", R); H.setName("Fixp5hero");
H.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
H.baseCharStats().setMyRace(CMClass.getRace("Human")); H.recoverCharStats();
try {
	clearVar("FIXP5CHAPTER");
	var f0 = H.fetchFaction(fid); if (f0 == java.lang.Integer.MAX_VALUE) f0 = 0;
	var rewards = "1 FACTION(ID=" + fid + " AMOUNT=300) 1 EFFECT(ID=Prop_Smell PARMS=lavender TICKS=0) 1 VAR(NAME=FIXP5CHAPTER VALUE=2 WHO=FIXTEST)"
		+ " 1 RECIPE(SKILL=Carpentry NAME=bokken) 1 SCRIPT(ONCE_PROG 100;mptattoo $i FIXP5ONCE;~) 1 SCRIPT(FUNCTION_PROG ONACHIEVE;mptattoo $i FIXP5FN;~)";
	var said = s(A.giveAwards(H, rewards));
	check("faction", H.fetchFaction(fid) == f0 + 300 && has(said, "standing"), H.fetchFaction(fid) + " " + said.substring(0, 200));
	var sm = H.fetchEffect("Prop_Smell");
	check("effect", sm != null && s(sm.text()) == "lavender" && sm.isSavable(), s(sm));
	check("var", gvar("FIXP5CHAPTER") == "2", gvar("FIXP5CHAPTER"));
	check("recipe", KR.knows(H, "Carpentry", "bokken"), "not known");
	check("script", H.findTattoo("FIXP5ONCE") != null && H.findTattoo("FIXP5FN") != null, "no tattoos");
	// read as the achievements read them: descriptions, and written back
	var L = A.parseAwardsList("500 FACTION(ID=" + fid + ") 1 EFFECT(ID=Prop_Smell TICKS=20) 1 RECIPE(SKILL=Carpentry NAME=\"bokken\")");
	check("described", L.size() == 3 && has(L.get(0).getDescription(), "+500") && has(L.get(1).getDescription(), "for 20 ticks") && has(L.get(2).getDescription(), "the recipe for bokken"),
		L.size() + " " + (L.size() > 0 ? L.get(0).getDescription() : ""));
	var back = s(A.getAwardString(L.toArray(java.lang.reflect.Array.newInstance(java.lang.Class.forName("com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary$Award"), 0))));
	check("written back", has(back, "500 FACTION(ID=" + fid + ")") && has(back, "EFFECT(ID=Prop_Smell TICKS=20)"), back);
	// a timed effect, and a wrong one
	A.giveAwards(H, "1 EFFECT(ID=Spell_Light TICKS=5)");
	check("timed", H.fetchEffect("Spell_Light") != null, "no light");
	var bad = s(A.giveAwards(H, "1 FACTION(ID=fixp5nosuchfaction)"));
	check("refused", has(bad, "Error") && has(bad, "no faction"), bad);
	check("help", has(CMLib.help().getHelpText("ACHIEVEMENTREWARDS", M, true), "RECIPE("), "no help");
} finally {
	clearVar("FIXP5CHAPTER");
	H.setPlayerStats(null); H.destroy();
}
done();
