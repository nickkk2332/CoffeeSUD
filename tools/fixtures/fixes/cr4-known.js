// CR4: recipes a character knows (Prop_KnownRecipes): HIDDEN=1 rows only for those who know them; MPLEARNRECIPE (a skill's own recipe by name, or a whole line); KNOWSRECIPE(); LEARN FROM a recipe book; kept in its text; PROFILERECIPES shares them.
FIXFILE = "cr4-known";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), KR = CM.core.KnownRecipes;
var acct = CMClass.getCommon("DefaultPlayerAccount");
acct.setAccountName("Fixcr4profile");
var names = new java.util.ArrayList(); names.add("Fixcr4ann"); names.add("Fixcr4bo"); acct.setPlayerNames(names);
var made = [], sess = {}, seen = {}, things = [];
function character(name) {
	var V = newMob("GenMob", R);
	V.setName(name);
	V.basePhyStats().setLevel(10); V.recoverPhyStats();
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
	sess[name] = VS;
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(acct);
	CMLib.players().addPlayer(V);
	var A = CMClass.getAbility("Carpentry"); A.setProficiency(100); V.addAbility(A);
	made.push(V);
	return V;
}
function said(name) { var all = s(field(sess[name], "bout")), n = seen[name] || 0; seen[name] = all.length; return all.substring(n); }
function list(V) { var A = V.fetchAbility("Carpentry"), C = new java.util.ArrayList(); C.add("list"); said(V.Name()); A.invoke(V, C, null, false, 0); return said(V.Name()); }
var ann = character("Fixcr4ann"), bo = character("Fixcr4bo");
var A = ann.fetchAbility("Carpentry"), base = A.fetchRecipes(), width = base.get(base.size() - 1).size();
function line(name, keys) { var c = [name, "1", "10", "1", "1", "GenItem"]; while (c.length < width) c.push(""); if (keys) c.push(keys); return c.join("\t"); }
function book(lines) {
	var arr = java.lang.reflect.Array.newInstance(java.lang.String, lines.length);
	for (var i = 0; i < lines.length; i++) arr[i] = lines[i];
	var B = CMClass.getItem("GenRecipe"); B.setCommonSkillID("Carpentry"); B.setRecipeCodeLines(arr); B.setName("a fixcr4 pattern book"); things.push(B);
	return B;
}
function run(text) { var m = newMob("GenMob", R); m.setName("a fixcr4 master"); scriptOn(m, "RAND_PROG 100\n" + text + "\nmpdisable\n~\n"); tickMob(m); m.destroy(); }
try {
	// HIDDEN=1: not for those who do not know it
	var B1 = book([line("fixcr4 stool"), line("fixcr4 lute", "HIDDEN=1")]);
	ann.addItem(B1);
	var l1 = list(ann);
	KR.of(ann, true).add("Carpentry", "fixcr4 lute");
	var l2 = list(ann);
	check("hidden", has(l1, "fixcr4 stool") && !has(l1, "fixcr4 lute") && has(l2, "fixcr4 lute"), "stool " + has(l1, "fixcr4 stool") + ", lute before " + has(l1, "fixcr4 lute") + ", after " + has(l2, "fixcr4 lute") + " / " + l2.replace(/\s+/g, " ").slice(-300));
	ann.delItem(B1);
	// MPLEARNRECIPE: one of the skill's own, by name; a whole line; KNOWSRECIPE()
	["CR4A", "CR4B", "CR4C"].forEach(clearVar);
	var own = s(base.get(0).get(0)).replace("%", "").trim();
	run("mplearnrecipe Fixcr4ann Carpentry " + own + "\nmplearnrecipe Fixcr4ann Carpentry fixcr4 drum\\t1\\t10\\t1\\t1\\tGenItem\n"
		+ "if knowsrecipe(Fixcr4ann Carpentry " + own + ")\n mpsetvar FIXTEST CR4A yes\nendif\nif knowsrecipe(Fixcr4ann Carpentry fixcr4 drum)\n mpsetvar FIXTEST CR4B yes\nendif\n"
		+ "if knowsrecipe(Fixcr4ann Carpentry fixcr4 nothing)\n mpsetvar FIXTEST CR4C yes\nendif\n");
	var l3 = list(ann);
	check("mplearnrecipe", gvar("CR4A") == "yes" && gvar("CR4B") == "yes" && gvar("CR4C") == "" && has(l3, "fixcr4 drum") && has(said("Fixcr4ann"), ""), gvar("CR4A") + gvar("CR4B") + "/" + gvar("CR4C"));
	var errs = s(CM.core.ScriptErrors.recent(200, null));
	run("mplearnrecipe Fixcr4ann Carpentry fixcr4 nosuchthing");
	var errs2 = s(CM.core.ScriptErrors.recent(200, null));
	check("no such", has(errs2.substring(errs.length), "no recipe 'fixcr4 nosuchthing'"), errs2.substring(errs.length));
	// LEARN FROM a book: known after the book is gone
	var B2 = book([line("fixcr4 harp")]);
	ann.addItem(B2);
	var C = new java.util.ArrayList(); ["LEARN", "FROM", "pattern"].forEach(function (w) { C.add(w); });
	said("Fixcr4ann");
	CMClass.getCommand("Learn").execute(ann, C, 0);
	var learned = said("Fixcr4ann");
	ann.delItem(B2);
	var l4 = list(ann);
	check("learn from", has(learned, "learn") && has(l4, "fixcr4 harp"), learned + " / " + l4.substring(0, 160));
	// kept in its text
	var K = ann.fetchEffect("Prop_KnownRecipes"), P2 = CMClass.getAbility("Prop_KnownRecipes");
	P2.setMiscText(K.text());
	check("saved", K.isSavable() && P2.knows("CARPENTRY", "fixcr4 harp") && P2.knows("Carpentry", "fixcr4 lute") && P2.linesFor("Carpentry").length == 2, s(K.text()).replace(/\t/g, "\\t"));
	// PROFILERECIPES: the profile's characters share them
	var l5 = list(bo);
	CMLib.sessions().add(sess["Fixcr4ann"]); CMLib.sessions().add(sess["Fixcr4bo"]);
	var P = CMProps.instance(); P.setProperty("PROFILERECIPES", "true");
	var l6, k6;
	try { l6 = list(bo); k6 = KR.knows(bo, "Carpentry", "fixcr4 harp"); }
	finally { P.remove("PROFILERECIPES"); CMLib.sessions().remove(sess["Fixcr4ann"]); CMLib.sessions().remove(sess["Fixcr4bo"]); }
	check("profile", !has(l5, "fixcr4 harp") && has(l6, "fixcr4 harp") && k6, "before " + has(l5, "fixcr4 harp") + ", after " + has(l6, "fixcr4 harp") + ", knows " + k6);
	check("help", has(CMLib.help().getHelpText("PROP_KNOWNRECIPES", M, true), "HIDDEN") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPLEARNRECIPE"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	made.forEach(function (V) { CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy(); });
}
done();
