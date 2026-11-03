// CR3: Prop_Workstation (TAG, SKILLS, SPEED, PROF, MAXLEVEL, QUALITY) for a crafter in the room; a tagged item is a station with no bonus; STATION= on a recipe and a GenCraftSkill; TAG(x) components; the wear(n) fate.
FIXFILE = "cr3-stations";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location(), WS = CM.core.Workstations;
var V = newMob("GenMob", R);
V.setName("Fixcr3smith");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
var A = CMClass.getAbility("Carpentry"); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("Carpentry");
var B = CMClass.getAbility("Blacksmithing");
var X = scratchRoom(), Y = scratchRoom();
var things = [];
function item(name, room) { var I = CMClass.getItem("GenItem"); I.setName(name); room.addItem(I); things.push(I); return I; }
function craft(words) { var C = new java.util.ArrayList(); words.split(" ").forEach(function (w) { C.add(w); }); said(); A.invoke(V, C, null, false, 0); var E = V.fetchEffect("Carpentry"); if (E != null) { E.unInvoke(); V.delEffect(E); } return said(); }
try {
	var forge = item("a dwarven fixcr3 bench", X);
	var W = CMClass.getAbility("Prop_Workstation");
	W.setMiscText("TAG=forge,anvil SKILLS=Carpentry SPEED=25 PROF=+10 MAXLEVEL=40 QUALITY=+1");
	forge.addNonUninvokableEffect(W);
	check("parse", W.isA("ANVIL") && W.isA("forge") && !W.isA("loom") && W.helps(A) && !W.helps(B) && W.speed() == 25 && W.prof() == 10 && W.maxLevel() == 40 && W.quality() == 1, W.text());
	// in the room with it: its bonuses for Carpentry, none for Blacksmithing, none elsewhere
	X.bringMobHere(V, false);
	var inX = [WS.speed(V, A), WS.prof(V, A), WS.maxLevel(V, A), WS.quality(V, A), WS.speed(V, B)];
	var lvX = A.xlevel(V), durX = call(A, "getDuration", [java.lang.Integer.valueOf(100), V, java.lang.Integer.valueOf(1), java.lang.Integer.valueOf(1)]);
	Y.bringMobHere(V, false);
	var inY = [WS.speed(V, A), WS.maxLevel(V, A)];
	var lvY = A.xlevel(V), durY = call(A, "getDuration", [java.lang.Integer.valueOf(100), V, java.lang.Integer.valueOf(1), java.lang.Integer.valueOf(1)]);
	check("bonuses", inX.join() == "25,10,40,1,0" && inY.join() == "0,0" && lvX == 40 && lvY == 10 && durX == Math.max(1, durY - Math.round(durY * 0.25)), inX.join() + " / " + inY.join() + " / level " + lvX + "," + lvY + " / ticks " + durX + "," + durY);
	// a tagged item is a station with no bonus
	var anvil = item("a fixcr3 travel anvil", Y); anvil.addTag("anvil");
	check("tagged", WS.has(Y, "anvil") && !WS.has(Y, "forge") && WS.has(X, "forge") && WS.speed(V, A) == 0 && WS.refusal(V, "anvil") == null && has(WS.refusal(V, "forge"), "You need a forge here."),
		WS.has(Y, "anvil") + " " + WS.refusal(V, "forge"));
	// STATION= on a recipe
	var base = A.fetchRecipes(), width = base.get(base.size() - 1).size();
	var c = ["fixcr3 rack", "1", "10", "1", "1", "GenItem"]; while (c.length < width) c.push(""); c.push("STATION=forge");
	var arr = java.lang.reflect.Array.newInstance(java.lang.String, 1); arr[0] = c.join("\t");
	var book = CMClass.getItem("GenRecipe"); book.setCommonSkillID("Carpentry"); book.setRecipeCodeLines(arr); V.addItem(book); things.push(book);
	var r1 = craft("fixcr3 rack");
	X.bringMobHere(V, false);
	var r2 = craft("fixcr3 rack");
	check("station key", has(r1, "You need a forge here.") && !has(r2, "need a forge"), r1 + " / " + r2);
	// STATION on a data skill
	var G = CMClass.getAbility("GenCraftSkill").copyOf();
	G.setStat("CLASS", "FixCr3Weaving");
	G.setStat("STATION", "loom");
	check("gen station", s(G.getStat("STATION")) == "loom" && s(call(G, "skillStation", [])) == "loom" && has(G.getStat("ALLXML"), "<STATION>loom</STATION>"), s(G.getStat("STATION")));
	// TAG(x) components and wear(n)
	var map = new java.util.HashMap();
	var err = CMLib.ableComponents().addAbilityComponent("FIXCR3CHISEL=(INVENTORY:wear(2):1:TAG(chisel):)", map);
	var req = map.get("FIXCR3CHISEL");
	var coded = s(CMLib.ableComponents().getAbilityComponentCodedString(req)), desc = s(CMLib.ableComponents().getAbilityComponentDesc(null, req));
	check("parse tag", err == null && req != null && req.size() == 1 && s(req.get(0).getType()) == "TAG" && s(req.get(0).getStringType()) == "chisel" && req.get(0).getWear() == 2 && !req.get(0).isConsumed()
		&& has(coded, "wear(2)") && has(coded, "TAG(CHISEL)") && has(desc, "something tagged chisel"), err + " / " + coded + " / " + desc);
	var map2 = new java.util.HashMap(); CMLib.ableComponents().addAbilityComponent("FIXCR3B=" + coded, map2);
	var r2c = map2.get("FIXCR3B");
	check("round trip", r2c != null && s(r2c.get(0).getType()) == "TAG" && r2c.get(0).getWear() == 2, s(coded));
	var none = CMLib.ableComponents().componentCheck(V, req, false);
	var chisel = CMClass.getItem("GenItem"); chisel.setName("a fixcr3 chisel"); chisel.addTag("chisel"); chisel.setUsesRemaining(100); V.addItem(chisel); things.push(chisel);
	var found = CMLib.ableComponents().componentCheck(V, req, false);
	CMLib.ableComponents().destroyAbilityComponents(found);
	var u1 = chisel.usesRemaining(), kept = V.isMine(chisel) && !chisel.amDestroyed();
	chisel.setUsesRemaining(2);
	CMLib.ableComponents().destroyAbilityComponents(CMLib.ableComponents().componentCheck(V, req, false));
	check("wear", none == null && found != null && kept && u1 == 98 && chisel.amDestroyed(), "none " + none + ", kept " + kept + ", uses " + u1 + ", broke " + chisel.amDestroyed());
	check("help", has(CMLib.help().getHelpText("PROP_WORKSTATION", M, true), "MAXLEVEL"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	V.setPlayerStats(null); V.destroy();
	X.destroy(); Y.destroy();
}
done();
