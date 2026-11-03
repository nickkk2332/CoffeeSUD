// CR18: [SKILL] EXPERIMENT [MATERIALS]: a HIDDEN=1 recipe with EXPERIMENT= is found by offering just those; half of them is a near miss with HINT=; a miss uses one up; CRAFTEXPERIMENTS a game day.
FIXFILE = "cr18-experiment";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), P = CMProps.instance();
var RF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/skills"), "fixcr18.txt");
RF.getParentFile().mkdirs();
java.nio.file.Files.write(RF.toPath(), new java.lang.String("a mug of tea\t1\t10\t1\t5\tGenItem\t\t\t\t\t\t\n"
	+ "a mug of spiced cider\t1\t10\t1\t5\tGenItem\t\t\t\t\t\tHIDDEN=1 EXPERIMENT=honey,apple,mead XP=40 HINT=\"It needs something warming...\"\n").getBytes("UTF-8"));
var G = CMClass.getAbility("GenCraftSkill").copyOf();
G.setStat("CLASS", "FixCr18Brewing"); G.setStat("NAME", "Fixcr18 Brewing"); G.setStat("TRIGSTR", "FIXBREW"); G.setStat("FILENAME", "fixcr18.txt"); G.setStat("MATLIST", "VEGETATION");
var made = [], things = [], sess = {}, seen = {};
function crafter(name) {
	var V = newMob("GenMob", R); V.setName(name);
	V.basePhyStats().setLevel(10); V.recoverPhyStats();
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V); sess[name] = VS; seen[name] = 0;
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var A = G.copyOf(); A.setProficiency(100); V.addAbility(A);
	made.push(V);
	return V;
}
function said(V) { var n = s(V.Name()), all = s(field(sess[n], "bout")); var o = all.substring(seen[n]); seen[n] = all.length; return o; }
function give(V, name) { var I = CMClass.getItem("GenItem"); I.setName(name); V.addItem(I); things.push(I); return I; }
function brew(V, words) { var C = new java.util.ArrayList(); words.split(" ").forEach(function (w) { C.add(w); }); said(V); V.fetchAbility("FixCr18Brewing").invoke(V, C, null, false, 0); return said(V); }
try {
	var V = crafter("Fixcr18brewer");
	var l0 = brew(V, "list");
	var honey = give(V, "a pot of honey"), apple = give(V, "a red apple"), mead = give(V, "a mug of mead"), cone = give(V, "a pine cone");
	var n1 = brew(V, "experiment honey apple");
	check("near", has(n1, "It needs something warming...") && has(n1, "used up") && honey.amDestroyed() && !has(l0, "spiced cider") && has(l0, "a mug of tea"), n1);
	honey = give(V, "a pot of honey");
	var m1 = brew(V, "experiment honey cone");
	check("miss", has(m1, "Nothing comes of it.") && honey.amDestroyed(), m1);
	honey = give(V, "a pot of honey");
	var xp0 = V.getExperience();
	var f1 = brew(V, "experiment honey apple mead");
	var l1 = brew(V, "list");
	check("found", has(f1, "You discover how to make a mug of spiced cider!") && V.getExperience() >= xp0 + 40 && CM.core.KnownRecipes.knows(V, "FixCr18Brewing", "a mug of spiced cider")
		&& has(l1, "spiced cider") && !honey.amDestroyed(), f1 + " / xp " + xp0 + " -> " + V.getExperience());
	var a1 = brew(V, "experiment honey apple mead");
	check("known", has(a1, "Nothing comes of it."), a1);
	var b1 = brew(V, "experiment fixcr18nothing");
	check("not carried", has(b1, "You don't have any fixcr18nothing to try."), b1);
	// CRAFTEXPERIMENTS: tries a game day
	var W = crafter("Fixcr18novice"); give(W, "a red apple"); give(W, "a red apple");
	P.setProperty("CRAFTEXPERIMENTS", "1");
	var t1, t2;
	try { t1 = brew(W, "experiment apple"); t2 = brew(W, "experiment apple"); } finally { P.remove("CRAFTEXPERIMENTS"); }
	check("limit", !has(t1, "tried enough") && has(t2, "You have tried enough for one day."), t1 + " / " + t2);
	check("help", has(CMLib.help().getHelpText("RECIPEKEYS", M, true), "EXPERIMENT"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	made.forEach(function (V) { V.setPlayerStats(null); V.destroy(); });
	RF["delete"]();
}
done();
