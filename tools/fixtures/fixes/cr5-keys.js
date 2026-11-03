// CR5: recipe requirement keys in a column of their own (or GenCraftSkill's KEY_VALUE_PARMS): MASK= hides a recipe, ROOMMASK= and TOOL= refuse with FAILMSG=, XP= on success, TATTOO= on the first; none is an item stat.
FIXFILE = "cr5-keys";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), RK = CM.core.RecipeKeys;
var made = [];
var V = newMob("GenMob", R);
V.setName("Fixcr5wright");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var seen = 0;
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
var A = CMClass.getAbility("Carpentry"); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("Carpentry");
function craft(words) { var C = new java.util.ArrayList(); words.split(" ").forEach(function (w) { C.add(w); }); said(); A.invoke(V, C, null, false, 0); var E = V.fetchEffect("Carpentry"); if (E != null) { E.unInvoke(); V.delEffect(E); } return said(); }
try {
	// the keys, from a column of their own
	var row = new java.util.ArrayList();
	["fixcr5 runeblade", "1", "10", "2", "1", "GenItem", "", "", "", "", "MASK=\"-LEVEL +>5\" ROOMMASK=\"-LOCATION +ROOMID(Deep#3)\" TOOL=\"rune chisel\" XP=200 TATTOO=RUNE_FORGED FAILMSG=\"Only the Deepforge burns hot enough.\""].forEach(function (c) { row.add(c); });
	var k = RK.of(row);
	check("keys", s(k.get("MASK")) == "-LEVEL +>5" && s(k.get("ROOMMASK")) == "-LOCATION +ROOMID(Deep#3)" && s(k.get("TOOL")) == "rune chisel" && s(k.get("XP")) == "200"
		&& s(k.get("TATTOO")) == "RUNE_FORGED" && has(k.get("FAILMSG"), "Deepforge"), s(k));
	var kv = CM.core.CMParms.parseEQParms("VALUE=12 MASK=\"-LEVEL +>5\" XP=3 WEIGHT=2");
	RK.strip(kv);
	check("not stats", s(kv.get("VALUE")) == "12" && s(kv.get("WEIGHT")) == "2" && kv.get("MASK") == null && kv.get("XP") == null, s(kv));
	// a recipe book of carpentry with three keyed recipes, as long as the skill's own rows
	var base = A.fetchRecipes(), width = base.get(base.size() - 1).size();
	function line(name, keys) { var c = [name, "1", "10", "1", "1", "GenItem"]; while (c.length < width) c.push(""); c.push(keys); return c.join("\t"); }
	var lines = [line("fixcr5 stool", "MASK=\"-LEVEL +>50\""), line("fixcr5 bench", "ROOMMASK=\"-LOCATION +ROOMID(Fixcr5Nowhere#1)\" FAILMSG=\"Only in the guild hall.\""),
		line("fixcr5 chest", "TOOL=\"fixcr5 plane\" XP=50 TATTOO=FIXCR5_CHEST")];
	var arr = java.lang.reflect.Array.newInstance(java.lang.String, lines.length);
	for (var i = 0; i < lines.length; i++) arr[i] = lines[i];
	var book = CMClass.getItem("GenRecipe"); book.setCommonSkillID("Carpentry"); book.setRecipeCodeLines(arr); V.addItem(book);
	var list = craft("list");
	check("mask hides", !has(list, "fixcr5 stool") && has(list, "fixcr5 bench") && has(list, "fixcr5 chest"), list.substring(0, 300));
	var b = craft("fixcr5 bench");
	check("roommask", has(b, "Only in the guild hall."), b);
	var st = craft("fixcr5 stool");
	check("hidden", has(st, "don't know how") && !has(st, "guild hall"), st);
	var c1 = craft("fixcr5 chest");
	var plane = CMClass.getItem("GenItem"); plane.setName("a fixcr5 plane"); V.addItem(plane);
	var c2 = craft("fixcr5 chest");
	check("tool", has(c1, "You need a fixcr5 plane to make that.") && !has(c2, "fixcr5 plane"), c1 + " / " + c2);
	// a success: XP and, the first time, the tattoo
	var mine = call(A, "addRecipes", [V, base]);
	A.matchingRecipes(mine, "fixcr5 chest", false);
	var xp0 = V.getExperience();
	var chest = CMClass.getItem("GenItem"); chest.setName("a fixcr5 chest");
	setField(A, "buildingI", chest);
	var won = call(A, "dropAWinner", [V, chest]);
	var xp1 = V.getExperience(), tat = V.findTattoo("FIXCR5_CHEST") != null;
	var chest2 = CMClass.getItem("GenItem"); chest2.setName("a fixcr5 chest");
	setField(A, "buildingI", chest2);
	call(A, "dropAWinner", [V, chest2]);
	var n = 0; for (var e = V.tattoos(); e.hasMoreElements();) if (s(e.nextElement().name()) == "FIXCR5_CHEST") n++;
	check("reward", won && xp1 >= xp0 + 50 && tat && n == 1, "won " + won + ", xp " + xp0 + " -> " + xp1 + ", tattoo " + tat + " x" + n);
	chest.destroy(); chest2.destroy(); plane.destroy(); book.destroy();
	check("help", has(CMLib.help().getHelpText("RECIPEKEYS", M, true), "ROOMMASK"), "no help");
} finally {
	V.setPlayerStats(null); V.destroy();
}
done();
