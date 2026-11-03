// CR17: quality tiers (resources/skills/craftquality.txt): the first tier that rolls under its chance names and changes what a player makes; @x3 is a station's QUALITY; NOQUALITY=1 and a data skill's QUALITY turn it off; the shipped file has none.
FIXFILE = "cr17-quality";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var CQ = CM.core.CraftQuality;
var QF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/skills"), "craftquality.txt");
QF.getParentFile().mkdirs();
var X = scratchRoom(), Y = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr17wright");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var A = CMClass.getAbility("Carpentry"); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("Carpentry");
var things = [];
function thing(name) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " is here."); I.setBaseValue(100); I.basePhyStats().setLevel(5); I.recoverPhyStats(); things.push(I); return I; }
function make(name) { var I = thing(name); setField(A, "buildingI", I); call(A, "dropAWinner", [V, I]); return I; }
try {
	java.nio.file.Files.write(QF.toPath(), new java.lang.String("# fixcr17 tiers\nfixcr17lucky\t@x3*100\t2\t\t\t\t2\tProp_HaveResister(fire 10)\nfixcr17fine\t100\t1.5\t\t\t\t1\n").getBytes("UTF-8"));
	var T = CQ.tiers();
	check("tiers", T.size() == 2 && s(T.get(0).name) == "fixcr17lucky" && T.get(0).value == 2 && T.get(0).level == 2 && s(T.get(0).effects) == "Prop_HaveResister(fire 10)" && T.get(1).armor == 1, "" + T.size());
	// no station: the lucky tier's chance is 0, so it is fine
	var a = make("a fixcr17 chest");
	check("fine", has(a.Name(), "fixcr17fine") && has(a.displayText(), "fixcr17fine") && a.baseGoldValue() == 150 && a.basePhyStats().level() == 6 && a.fetchEffect("Prop_HaveResister") == null,
		a.Name() + " / " + a.displayText() + " / " + a.baseGoldValue() + " / " + a.basePhyStats().level());
	// a station with QUALITY=1 helping: lucky
	var bench = CMClass.getItem("GenItem"); bench.setName("a fixcr17 bench"); X.addItem(bench); things.push(bench);
	var W = CMClass.getAbility("Prop_Workstation"); W.setMiscText("TAG=bench SKILLS=Carpentry QUALITY=1"); bench.addNonUninvokableEffect(W);
	var b = make("a fixcr17 crate");
	var R = b.fetchEffect("Prop_HaveResister");
	check("station", has(b.Name(), "fixcr17lucky") && !has(b.Name(), "fixcr17fine") && b.baseGoldValue() == 200 && b.basePhyStats().level() == 7 && R != null && s(R.text()) == "fire 10", b.Name() + " / " + b.baseGoldValue() + " / " + (R == null ? "none" : R.text()));
	Y.bringMobHere(V, false);
	// NOQUALITY=1 on the recipe
	var base = A.fetchRecipes(), width = base.get(base.size() - 1).size();
	var c = ["fixcr17 stool", "1", "10", "1", "1", "GenItem"]; while (c.length < width) c.push(""); c.push("NOQUALITY=1");
	var arr = java.lang.reflect.Array.newInstance(java.lang.String, 1); arr[0] = c.join("\t");
	var book = CMClass.getItem("GenRecipe"); book.setCommonSkillID("Carpentry"); book.setRecipeCodeLines(arr); V.addItem(book); things.push(book);
	A.matchingRecipes(call(A, "addRecipes", [V, base]), "fixcr17 stool", false);
	var n = make("a fixcr17 stool");
	check("noquality", s(n.Name()) == "a fixcr17 stool" && n.baseGoldValue() == 100, n.Name());
	// a monster crafter rolls none
	A.matchingRecipes(call(A, "addRecipes", [V, base]), "fixcr17 chest", false);
	var ps = V.playerStats(); V.setPlayerStats(null);
	var m; try { m = make("a fixcr17 chest"); } finally { V.setPlayerStats(ps); }
	check("players only", s(m.Name()) == "a fixcr17 chest", m.Name());
	// a data skill's QUALITY
	var G = CMClass.getAbility("GenCraftSkill").copyOf();
	G.setStat("CLASS", "FixCr17Joinery");
	var q0 = s(G.getStat("QUALITY")), on0 = s(call(G, "qualityOn", []));
	G.setStat("QUALITY", "false");
	check("data skill", q0 == "true" && on0 == "true" && s(G.getStat("QUALITY")) == "false" && s(call(G, "qualityOn", [])) == "false" && has(G.getStat("ALLXML"), "<QUALITY>false</QUALITY>"), q0 + " " + on0 + " / " + G.getStat("QUALITY"));
	// the shipped file: every tier in comments
	QF["delete"]();
	check("shipped", CQ.tiers().size() == 0, "" + CQ.tiers().size());
	check("help", has(CMLib.help().getHelpText("CRAFTQUALITY", M, true), "@x3") && has(CMLib.help().getHelpText("RECIPEKEYS", M, true), "NOQUALITY"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	V.setPlayerStats(null); V.destroy();
	QF["delete"]();
}
done();
