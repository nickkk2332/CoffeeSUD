// CR19: Prop_SalvageYield (SKILL, YIELD of resources, variants and catalog items with chances, XP, DESTROY) for Scrapping and a crafting skill's DISMANTLE; DISMANTLE by the recipe's materials (DISMANTLEPCT).
FIXFILE = "cr19-salvage";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var RM = CM.Items.interfaces.RawMaterial, P = CMProps.instance();
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr19tinker");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
function learn(id) { var A = CMClass.getAbility(id); A.setProficiency(100); V.addAbility(A); return V.fetchAbility(id); }
var Sc = learn("Scrapping"), Ca = learn("Carpentry");
var things = [], gear = null;
function thing(cls, name, mat, w, where) { var I = CMClass.getItem(cls); I.setName(name); I.setMaterial(mat); I.basePhyStats().setWeight(w); I.recoverPhyStats(); where.addItem(I); things.push(I); return I; }
function yieldOn(I, text) { var A = CMClass.getAbility("Prop_SalvageYield"); A.setMiscText(text); I.addNonUninvokableEffect(A); return A; }
function use(A, words) { var C = new java.util.ArrayList(); words.split(" ").forEach(function (w) { C.add(w); }); said(); A.invoke(V, C, null, false, 0); var E = V.fetchEffect(A.ID()); if (E != null) { setField(E, "messedUp", false); E.unInvoke(); V.delEffect(E); } return said(); }
function pounds(mat) { var n = 0; for (var e = V.items(); e.hasMoreElements();) { var I = e.nextElement(); if (I.material() == mat && (I instanceof RM)) n += I.basePhyStats().weight(); } return n; }
function carried(name) { for (var e = V.items(); e.hasMoreElements();) { var I = e.nextElement(); if (has(I.Name(), name)) return I; } return null; }
function clearRaw() { var L = []; for (var e = V.items(); e.hasMoreElements();) { var I = e.nextElement(); if (I instanceof RM) L.push(I); } L.forEach(function (I) { I.destroy(); }); }
try {
	gear = CMClass.getItem("GenItem"); gear.setName("a fixcr19 soul gear"); CMLib.catalog().addCatalog(gear);
	// Scrapping a ruined automaton: its yield, not a fifth of its weight
	var bot = thing("GenItem", "a ruined fixcr19 automaton", RM.RESOURCE_OAK, 50, X);
	yieldOn(bot, "YIELD=\"oak:3, iron(clockwork):1, catalog:a fixcr19 soul gear@100%, pine:2@0%\" XP=25");
	var xp0 = V.getExperience();
	var r1 = use(Sc, "automaton");
	var iron = null; for (var e = V.items(); e.hasMoreElements();) { var I = e.nextElement(); if (I.material() == RM.RESOURCE_IRON) iron = I; }
	check("scrap", pounds(RM.RESOURCE_OAK) == 3 && iron != null && s(iron.getSubType()).toLowerCase() == "clockwork" && carried("soul gear") != null && pounds(RM.RESOURCE_PINE) == 0
		&& bot.amDestroyed() && V.getExperience() == xp0 + 25 && has(r1, "You get"), r1 + " / oak " + pounds(RM.RESOURCE_OAK) + " / iron " + (iron == null ? "none" : iron.getSubType()) + " / xp " + (V.getExperience() - xp0));
	clearRaw();
	// DESTROY=false: it stays, with nothing more in it
	var wreck = thing("GenItem", "a fixcr19 wreck", RM.RESOURCE_OAK, 1, X);
	yieldOn(wreck, "YIELD=oak:1 DESTROY=false");
	var r2 = use(Sc, "wreck"), r3 = use(Sc, "wreck");
	check("destroy false", pounds(RM.RESOURCE_OAK) == 1 && !wreck.amDestroyed() && X.isContent(wreck) && wreck.fetchEffect("Prop_SalvageYield") == null && has(r3, "enough here"), r2 + " / " + r3);
	clearRaw();
	// DISMANTLE: half the recipe's materials at full proficiency
	var base = Ca.fetchRecipes(), width = base.get(base.size() - 1).size();
	var c = ["fixcr19 thing", "1", "10", "8", "1", "GenContainer"]; while (c.length < width) c.push("");
	var arr = java.lang.reflect.Array.newInstance(java.lang.String, 1); arr[0] = c.join("\t");
	var box = thing("GenContainer", "a fixcr19 thing", RM.RESOURCE_OAK, 8, V);
	var book = CMClass.getItem("GenRecipe"); book.setCommonSkillID("Carpentry"); book.setRecipeCodeLines(arr); V.addItem(book); things.push(book); // it names itself after its recipe, so it goes after the thing
	var d1 = use(Ca, "dismantle thing");
	check("dismantle", pounds(RM.RESOURCE_OAK) == 4 && box.amDestroyed() && has(d1, "apart"), d1 + " / oak " + pounds(RM.RESOURCE_OAK));
	clearRaw();
	var bar = thing("GenItem", "a fixcr19 bar", RM.RESOURCE_IRON, 5, V);
	var d2 = use(Ca, "dismantle bar");
	var box2 = thing("GenContainer", "a fixcr19 thing", RM.RESOURCE_OAK, 8, V);
	P.setProperty("DISMANTLEPCT", "0");
	var d3; try { d3 = use(Ca, "dismantle thing"); } finally { P.remove("DISMANTLEPCT"); }
	check("refused", has(d2, "don't know how to dismantle") && !bar.amDestroyed() && has(d3, "Nothing is dismantled") && !box2.amDestroyed(), d2 + " / " + d3);
	// a Prop_SalvageYield for Carpentry is what DISMANTLE gives; one for Scrapping is not
	var crate = thing("GenContainer", "a fixcr19 crate", RM.RESOURCE_OAK, 8, V);
	yieldOn(crate, "SKILL=Carpentry YIELD=pine:2");
	var d4 = use(Ca, "dismantle crate");
	var lamp = thing("GenItem", "a fixcr19 lamp", RM.RESOURCE_IRON, 5, V);
	yieldOn(lamp, "YIELD=iron:2");
	var d5 = use(Ca, "dismantle lamp");
	check("dismantle prop", pounds(RM.RESOURCE_PINE) == 2 && pounds(RM.RESOURCE_OAK) == 0 && crate.amDestroyed() && !lamp.amDestroyed() && has(d5, "don't know how"), d4 + " / " + d5);
	check("help", has(CMLib.help().getHelpText("PROP_SALVAGEYIELD", M, true), "DISMANTLEPCT"), "no help");
} finally {
	clearRaw();
	things.forEach(function (I) { if (!I.amDestroyed()) I.destroy(); });
	var sg = carried("soul gear"); if (sg != null) sg.destroy();
	if (gear != null && CMLib.catalog().getCatalogItem("a fixcr19 soul gear") != null) CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixcr19 soul gear"));
	V.setPlayerStats(null); V.destroy();
}
done();
