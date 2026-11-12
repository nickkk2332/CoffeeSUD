// I06 targeted crafting: operations that draw their modifiers (imbue adds one, reforge redraws every unprotected one), shaped by
// excluded tags, weight multipliers and a guaranteed family, from the operation and from catalysts named at PREPARE (paid with the
// craft). The preview shows each affix's chance or why it cannot be drawn; a pool that is empty, or a guarantee that cannot hold,
// is refused before anything is paid or drawn; a recipe takes catalysts at its last stage, which draws the product.
FIXFILE = "i06-targeted";
var diceAtStart = CMath.getRandState();
var IM = CM.core.ItemModifiers, IC = CM.core.ItemCrafting, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-i06-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var R = scratchRoom(), actors = [], items = [], catalogued = null;
function actor(name) { var a = newMob("GenMob", R); a.setName(name); actors.push(a); return a; }
function pile(a, material, weight, name) { var i = CMClass.getItem("GenResource"); i.setName(name); i.setMaterial(material); i.basePhyStats().setWeight(weight); i.setBaseValue(weight); i.recoverPhyStats(); a.addItem(i); items.push(i); return i; }
function data(item) { return JSON.parse(s(MD.json(IM.data(item)))); }
function state(a) { return s(MD.json(OM.get("items.modifiers", OM.character(a)))); }
function carried(a, name) { for (var i = 0; i < a.numItems(); i++) if (s(a.getItem(i).Name()) == name) return a.getItem(i); return null; }
function made(a, name) { for (var i = 0; i < a.numItems(); i++) { var I = a.getItem(i); if (IM.isManaged(I) && data(I).base_name == name) return I; } return null; }
function ids(item) { var d = data(item), out = []; for (var i = 0; i < d.modifiers.length; i++) out.push(s(d.modifiers[i].pin.id)); return out.join(","); }
var RM = CM.Items.interfaces.RawMaterial;
try {
	var ex = new java.io.File("../SUDDocs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "workbench"), root);
	WorldFiles.configure(root); CM.core.CombatProfiles.reset(); IM.reset(); CM.core.Affixes.reload();
	var plan = MD.load(root, "items.modifiers", ""); currentField.set(null, OM.prepare(plan, null, false));
	check("definitions-valid", plan.errors.isEmpty() && IM.check().errors.isEmpty(), s(plan.problems()) + " / " + s(IM.check().report()));
	var ops = s(IC.operations());
	check("listed", ops.indexOf("workbench.imbue - Weave in a drawn modifier (imbue); 2 IRON; station ember-forge; catalysts workbench.coal, workbench.ash, workbench.amber") >= 0
		&& ops.indexOf("catalyst workbench.coal - Forge coal; 1 COAL; offense x4\n") >= 0 && ops.indexOf("catalyst workbench.amber - Ember amber; 1 AMBER; the first modifier drawn is of the defense family\n") >= 0
		&& ops.indexOf("catalysts at the last stage workbench.coal, workbench.ash, workbench.amber") >= 0, ops);

	var A = actor("Weaver Wen");
	var forge = CMClass.getItem("GenItem"), station = CMClass.getAbility("Prop_Workstation"); station.setMiscText("TAG=ember-forge"); forge.addNonUninvokableEffect(station); R.addItem(forge); items.push(forge);
	var iron = pile(A, RM.RESOURCE_IRON, 30, "some bench iron"), coal = pile(A, RM.RESOURCE_COAL, 3, "some forge coal");
	var ash = pile(A, RM.RESOURCE_ASH, 3, "some ward ash"), amber = pile(A, RM.RESOURCE_AMBER, 3, "some ember amber");
	var cloak = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_plain", null, A, R).get(0); items.push(cloak); A.addItem(cloak);
	check("plain-cloak", data(cloak).modifiers.length == 0, s(IM.inspect(cloak)));
	var other = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_cloak", null, A, R).get(0); items.push(other); A.addItem(other);

	// the player's preview: each affix's chance in the draw, or why it cannot be drawn; nothing is spent or drawn
	var dice = CMath.getRandState(), st = state(A);
	var p0 = s(IC.preview(A, "workbench.imbue", cloak, ""));
	check("preview-plain", p0.indexOf("Distribution: no exclusions, multipliers or guarantee") >= 0 && p0.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): 45.5%") >= 0
		&& p0.indexOf("embertrial.fireguard2 (Flameward, prefix tier 2): 9.1%") >= 0 && p0.indexOf("embertrial.piercing (Embers, suffix tier 1): 45.5%") >= 0, p0);
	var p1 = s(IC.preview(A, "workbench.imbue", cloak, "WITH workbench.coal"));
	check("preview-weights", p1.indexOf("cost 2 weight unit(s) of plain IRON and 1 weight unit(s) of plain COAL, with the catalyst(s) Forge coal") >= 0
		&& p1.indexOf("Distribution: offense x4 (catalyst workbench.coal)") >= 0 && p1.indexOf("embertrial.piercing (Embers, suffix tier 1): 76.9% (weight 10, offense catalyst workbench.coal x4)") >= 0
		&& p1.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): 19.2%") >= 0, p1);
	var p2 = s(IC.preview(A, "workbench.imbue", cloak, "with workbench.ash"));
	check("preview-excluded", p2.indexOf("embertrial.piercing (Embers, suffix tier 1): cannot be drawn: tag offense is excluded by catalyst workbench.ash") >= 0
		&& p2.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): 83.3%") >= 0, p2);
	var p3 = s(IC.preview(A, "workbench.imbue", cloak, "WITH workbench.amber"));
	check("preview-guarantee", p3.indexOf("the first modifier drawn is of the defense family (catalyst workbench.amber)") >= 0
		&& p3.indexOf("embertrial.piercing (Embers, suffix tier 1): cannot be drawn: not of the guaranteed defense family (catalyst workbench.amber)") >= 0, p3);
	var p4 = s(IC.preview(A, "workbench.imbue", cloak, "WITH workbench.coal, workbench.amber"));
	check("preview-together", p4.indexOf("offense x4 (catalyst workbench.coal); the first modifier drawn is of the defense family (catalyst workbench.amber)") >= 0
		&& p4.indexOf("cost 2 weight unit(s) of plain IRON and 1 weight unit(s) of plain COAL and 1 weight unit(s) of plain AMBER") >= 0, p4);
	throws("unknown-catalyst", function () { IC.preview(A, "workbench.imbue", cloak, "WITH workbench.nothing"); }, "no catalyst workbench.nothing");
	throws("catalyst-twice", function () { IC.preview(A, "workbench.imbue", cloak, "WITH workbench.coal workbench.coal"); }, "name each catalyst once");
	throws("imbue-takes-no-selector", function () { IC.preview(A, "workbench.imbue", cloak, "embertrial.piercing"); }, "imbue takes no modifier or affix");
	throws("catalyst-not-taken", function () { IC.preview(A, "workbench.protect", other, "1 WITH workbench.coal"); }, "Bind one modifier takes no catalysts, not workbench.coal");
	coal.basePhyStats().setWeight(0); coal.recoverPhyStats();
	throws("catalyst-unpaid", function () { IC.preview(A, "workbench.imbue", cloak, "WITH workbench.coal"); }, "need 1 weight unit(s) of plain COAL");
	coal.basePhyStats().setWeight(3); coal.recoverPhyStats();
	check("previews-free", dice.equals(CMath.getRandState()) && state(A) == st && iron.basePhyStats().weight() == 30 && coal.basePhyStats().weight() == 3 && data(cloak).revision == 0, "something was spent or drawn");

	// imbue with ward ash: offense left out, so a fire-resistance prefix; iron and ash paid in one settlement, the catalyst on the receipt
	IC.prepare(A, "workbench.imbue", cloak, "WITH workbench.ash"); var serial = JSON.parse(state(A)).serial;
	var said = s(IC.commit(A, serial)), d1 = data(cloak), receipt = JSON.parse(s(IC.receipt(A, serial)));
	check("imbue", said.indexOf("consumed 2 IRON and 1 ASH") >= 0 && said.indexOf("; drew ") >= 0 && d1.modifiers.length == 1 && d1.modifiers[0].group == "fire-resistance"
		&& iron.basePhyStats().weight() == 28 && ash.basePhyStats().weight() == 2 && receipt.catalysts && receipt.catalysts[0].id == "workbench.ash", said + " / " + JSON.stringify(receipt));
	check("commit-once", s(IC.commit(A, serial)).indexOf("Already settled") >= 0 && ash.basePhyStats().weight() == 2, "settled twice");

	// an empty pool, or a guarantee that cannot hold, is refused before anything is paid or drawn
	dice = CMath.getRandState(); st = state(A);
	throws("empty-pool", function () { IC.prepare(A, "workbench.imbue", cloak, "WITH workbench.ash"); }, "no affix can be drawn for this item:");
	throws("empty-pool-why", function () { IC.preview(A, "workbench.imbue", cloak, "WITH workbench.ash"); }, "embertrial.piercing (Embers, suffix tier 1): cannot be drawn: tag offense is excluded by catalyst workbench.ash");
	throws("guarantee-cannot-hold", function () { IC.prepare(A, "workbench.imbue", cloak, "WITH workbench.amber"); }, "no affix of the guaranteed defense family (catalyst workbench.amber) can be drawn for this item");
	check("refusals-free", dice.equals(CMath.getRandState()) && state(A) == st && iron.basePhyStats().weight() == 28 && ash.basePhyStats().weight() == 2 && amber.basePhyStats().weight() == 3, "something was spent");

	// imbue again with nothing named: only the suffix is left; then the cloak is full
	IC.prepare(A, "workbench.imbue", cloak, ""); said = s(IC.commit(A, 0));
	check("imbue-suffix", said.indexOf("drew Embers (embertrial.piercing)") >= 0 && data(cloak).modifiers.length == 2, said);
	throws("cloak-full", function () { IC.preview(A, "workbench.imbue", cloak, ""); }, "embertrial.piercing (Embers, suffix tier 1): cannot be drawn: exclusive group fire-penetration already present: Embers");

	// reforge: every unprotected modifier drawn again; a protected one stays
	IC.prepare(A, "workbench.protect", cloak, "1"); IC.commit(A, 0);
	var before = data(cloak);
	var rp = s(IC.preview(A, "workbench.reforge", cloak, ""));
	check("reforge-preview", rp.indexOf("Distribution: defense x2 (operation workbench.reforge)") >= 0 && rp.indexOf("Reforge draws 1 modifier(s) in place of the unprotected ones; the first:") >= 0
		&& rp.indexOf("embertrial.piercing (Embers, suffix tier 1): 100.0%") >= 0 && rp.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): cannot be drawn: exclusive group fire-resistance already present") >= 0, rp);
	IC.prepare(A, "workbench.reforge", cloak, ""); said = s(IC.commit(A, 0));
	var after = data(cloak);
	check("reforge", said.indexOf("drew Embers (embertrial.piercing)") >= 0 && after.modifiers.length == 2 && after.modifiers[0].uid == before.modifiers[0].uid
		&& after.modifiers[0]["protected"] === true && after.modifiers[1].uid != before.modifiers[1].uid && iron.basePhyStats().weight() == 28 - 2 - 1 - 3, said + " / " + JSON.stringify(after.modifiers));
	throws("reforge-guarantee", function () { IC.preview(A, "workbench.reforge", cloak, "WITH workbench.amber"); }, "no affix of the guaranteed defense family");
	IC.prepare(A, "workbench.protect", cloak, "2"); IC.commit(A, 0);
	throws("reforge-all-protected", function () { IC.preview(A, "workbench.reforge", cloak, ""); }, "every modifier is protected; nothing would be reforged");

	// a recipe takes catalysts at its last stage, which draws the product
	var P = CMClass.getArmor("GenArmor"); P.setName("a woven cloak"); P.setDisplayText("A woven cloak lies here."); P.recoverPhyStats(); CMLib.catalog().addCatalog(P); P.destroy(); catalogued = "a woven cloak";
	var cotton = pile(A, RM.RESOURCE_COTTON, 2, "some bench cotton");
	IC.begin(A, "workbench.ward-cloak");
	var piece = carried(A, "a warded weave"); if (piece != null) items.push(piece);
	throws("catalyst-early", function () { IC.preview(A, "workbench.ward-cloak", piece, "WITH workbench.amber"); }, "catalysts go in at the last stage (2), which draws the product");
	IC.prepare(A, "workbench.ward-cloak", piece, ""); IC.commit(A, 0);
	var sp = s(IC.preview(A, "workbench.ward-cloak", piece, ""));
	check("recipe-preview", sp.indexOf("The product's first modifier:") >= 0 && sp.indexOf("embertrial.piercing (Embers, suffix tier 1): 45.5%") >= 0, sp);
	var sc = s(IC.preview(A, "workbench.ward-cloak", piece, "WITH workbench.amber workbench.ash"));
	check("recipe-preview-catalysts", sc.indexOf("excludes offense (catalyst workbench.ash); the first modifier drawn is of the defense family (catalyst workbench.amber)") >= 0
		&& sc.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): 83.3%") >= 0, sc);
	IC.prepare(A, "workbench.ward-cloak", piece, "WITH workbench.amber workbench.ash"); said = s(IC.commit(A, 0));
	var product = made(A, "a woven cloak"); if (product != null) items.push(product);
	check("recipe-product", said.indexOf("consumed 1 COTTON and 1 AMBER and 1 ASH") >= 0 && product != null && data(product).base.id == "workbench.greatcloak"
		&& data(product).modifiers.length == 1 && data(product).modifiers[0].group == "fire-resistance" && amber.basePhyStats().weight() == 2 && ash.basePhyStats().weight() == 1,
		said + " / " + (product == null ? "no product" : ids(product)));
} finally {
	CMath.setRandState(diceAtStart.longValue());
	if (catalogued != null) { var c = CMLib.catalog().getCatalogItem(catalogued); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IM.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
