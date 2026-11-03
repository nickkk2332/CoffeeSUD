// CR21: LEARN for a data crafting skill (GenCraftSkill): CANLEARN turns it on, LEARNMASK narrows the items it learns from, and the item is taken apart into a blank recipe book.
FIXFILE = "cr21-learn";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var R = M.location();
var RF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/skills"), "fixcr21.txt");
RF.getParentFile().mkdirs();
java.nio.file.Files.write(RF.toPath(), new java.lang.String("% rune stone\t1\t10\t2\t5\tGenItem\t\t\t\t\t\t\n").getBytes("UTF-8"));
var G = CMClass.getAbility("GenCraftSkill").copyOf();
G.setStat("CLASS", "FixCr21Runecarving");
G.setStat("NAME", "Fixcr21 Runecarving");
G.setStat("TRIGSTR", "FIXRUNECARVE");
G.setStat("FILENAME", "fixcr21.txt");
G.setStat("MATLIST", "ROCK");
var V = newMob("GenMob", R);
V.setName("Fixcr21carver");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
G.setProficiency(100); V.addAbility(G);
var A = V.fetchAbility("FixCr21Runecarving");
var things = [];
function learn() { var C = new java.util.ArrayList(); C.add("learn"); C.add("rune"); said(); var ok = A.invoke(V, C, null, false, 0); return [ok, said()]; }
try {
	var stone = CMClass.getItem("GenItem"); stone.setName("a granite rune stone"); stone.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_GRANITE); stone.basePhyStats().setLevel(1); stone.recoverPhyStats(); V.addItem(stone); things.push(stone);
	var book = CMClass.getItem("GenRecipe"); book.setName("a fixcr21 blank book"); book.setCommonSkillID(""); book.setTotalRecipePages(5); V.addItem(book); things.push(book);
	check("fields", s(A.getStat("CANLEARN")) == "false" && s(A.getStat("LEARNMASK")) == "" && !A.supportsDeconstruction(), s(A.getStat("CANLEARN")));
	var r0 = learn();
	check("off", !r0[0] && has(r0[1], "don't know how to learn new recipes"), r0[1]);
	A.setStat("CANLEARN", "true");
	A.setStat("LEARNMASK", "-TAGS +rune");
	var r1 = learn();
	check("mask", !r1[0] && has(r1[1], "can't learn anything about") && A.supportsDeconstruction() && has(A.getStat("ALLXML"), "<CANLEARN>true</CANLEARN>"), r1[1]);
	stone.addTag("rune");
	var r2 = learn();
	var E = V.fetchEffect("FixCr21Runecarving");
	if (E != null) { setField(E, "messedUp", false); E.unInvoke(); V.delEffect(E); }
	var lines = book.getRecipeCodeLines();
	check("learned", r2[0] && E != null && lines.length == 1 && has(lines[0], "rune stone") && stone.amDestroyed(), r2[1] + " / " + lines.length + " / " + stone.amDestroyed());
	check("help", has(CMLib.help().getHelpText("CRAFTSKILL", M, true), "CANLEARN") || has(CMLib.help().getHelpText("GENCRAFTSKILL", M, true), "CANLEARN"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	V.setPlayerStats(null); V.destroy();
	RF["delete"]();
}
done();
