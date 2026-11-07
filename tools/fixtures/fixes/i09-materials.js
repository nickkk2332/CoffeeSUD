// I09 materials and quality: a material's named numeric traits count only where a base declares a consumer (conductivity to
// electric penetration); a trait nothing reads is shown as doing nothing; a refined quality tier raises only the modifiers its
// base names (defense), and the stored rolls never change; the bonuses read the item's material and quality as they are now.
FIXFILE = "i09-materials";
var diceAtStart = CMath.getRandState();
var IM = CM.core.ItemModifiers, IC = CM.core.ItemCrafting, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-i09-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var R = scratchRoom(), actors = [], items = [];
function actor(name) { var a = newMob("GenMob", R); a.setName(name); actors.push(a); return a; }
function pile(a, material, weight, name) { var i = CMClass.getItem("GenResource"); i.setName(name); i.setMaterial(material); i.basePhyStats().setWeight(weight); i.setBaseValue(weight); i.recoverPhyStats(); a.addItem(i); items.push(i); return i; }
function data(item) { return JSON.parse(s(MD.json(IM.data(item)))); }
function stats(item) { return s(item.fetchEffect("Prop_ItemModifiers").combatStats().describe()); }
function details(item) { return s(item.fetchEffect("Prop_ItemModifiers").combatStatDetails()); }
var RM = CM.Items.interfaces.RawMaterial;
try {
	var ex = new java.io.File("../docs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "workbench"), root);
	WorldFiles.configure(root); CM.core.CombatProfiles.reset(); IM.reset(); CM.core.Affixes.reload();
	var plan = MD.load(root, "items.modifiers", ""); currentField.set(null, OM.prepare(plan, null, false));
	check("definitions-valid", plan.errors.isEmpty() && IM.check().errors.isEmpty(), s(plan.problems()) + " / " + s(IM.check().report()));

	var A = actor("Smith Sora");
	var forge = CMClass.getItem("GenItem"), station = CMClass.getAbility("Prop_Workstation"); station.setMiscText("TAG=ember-forge"); forge.addNonUninvokableEffect(station); R.addItem(forge); items.push(forge);
	var iron = pile(A, RM.RESOURCE_IRON, 10, "some bench iron");
	var bracer = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_bracer", null, A, R).get(0); items.push(bracer); A.addItem(bracer);
	var d0 = data(bracer), pre = -1, suf = -1;
	for (var i = 0; i < d0.modifiers.length; i++) { if (d0.modifiers[i].position == "prefix") pre = i; else suf = i; }
	check("bracer", s(RM.CODES.NAME(bracer.material())) == "BRONZE" && pre >= 0 && suf >= 0, s(IM.inspect(bracer)));
	var fire = d0.modifiers[pre].values["resistance.fire"], pen = d0.modifiers[suf].values["penetration.fire"];

	// the material: a trait a consumer reads, and one nothing reads, said as such
	var look = s(IM.inspect(bracer));
	check("traits-shown", look.indexOf("Material: Ember bronze (BRONZE): conductivity 3: penetration.electric +6 (2 per point); density 2: nothing on this item reads it") >= 0
		&& look.indexOf("Quality: tier 0 of 3; each tier raises modifiers tagged defense by 10%") >= 0, look);
	check("trait-counts", stats(bracer).indexOf("penetration.electric=6") >= 0 && details(bracer).indexOf("material traits: {\"penetration.electric\":6}") >= 0, stats(bracer) + " / " + details(bracer));

	// refine: one quality tier; only the defense modifier counts more; the stored rolls do not change
	var pv = s(IC.preview(A, "workbench.refine", bracer, ""));
	check("refine-preview", pv.indexOf("to quality tier 1 of 3: modifiers tagged defense count 10% more") >= 0, pv);
	IC.prepare(A, "workbench.refine", bracer, ""); var said = s(IC.commit(A, 0));
	var d1 = data(bracer), raised = fire + Math.round(fire * 0.1);
	check("refined", said.indexOf("Committed craft") >= 0 && d1.quality == 1 && d1.modifiers[pre].values["resistance.fire"] == fire && iron.basePhyStats().weight() == 9, said + " / " + JSON.stringify(d1));
	check("quality-counts", stats(bracer).indexOf("resistance.fire=" + raised) >= 0 && stats(bracer).indexOf("penetration.fire=" + pen) >= 0
		&& details(bracer).indexOf("(quality +10%)") >= 0, stats(bracer) + " / expected fire " + raised + " pen " + pen);
	look = s(IM.inspect(bracer));
	check("quality-shown", look.indexOf("Quality: tier 1 of 3; each tier raises modifiers tagged defense by 10%, now +10%") >= 0 && look.indexOf("(quality +10%)") >= 0, look);
	IC.prepare(A, "workbench.refine", bracer, ""); IC.commit(A, 0);
	IC.prepare(A, "workbench.refine", bracer, ""); IC.commit(A, 0);
	check("tier-three", data(bracer).quality == 3 && stats(bracer).indexOf("resistance.fire=" + (fire + Math.round(fire * 0.3))) >= 0, stats(bracer));
	var st = s(MD.json(OM.get("items.modifiers", OM.character(A)))), w = iron.basePhyStats().weight();
	throws("refine-most", function () { IC.preview(A, "workbench.refine", bracer, ""); }, "quality is at its most, tier 3");
	check("refusal-free", iron.basePhyStats().weight() == w && s(MD.json(OM.get("items.modifiers", OM.character(A)))) == st, "something was spent");

	// the bonuses read the material as it is: another resource has no traits, and the item says so
	bracer.setMaterial(RM.RESOURCE_IRON);
	check("material-live", stats(bracer).indexOf("penetration.electric") < 0 && s(IM.inspect(bracer)).indexOf("Material: IRON; it has no traits, so conductivity give nothing") >= 0, stats(bracer) + " / " + s(IM.inspect(bracer)));
	bracer.setMaterial(RM.RESOURCE_BRONZE);
	check("material-back", stats(bracer).indexOf("penetration.electric=6") >= 0, stats(bracer));

	// a base with no quality declared: refining is refused, nothing would read it
	var cloak = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_cloak", null, A, R).get(0); items.push(cloak); A.addItem(cloak);
	var refiner = JSON.parse(s(MD.json(IM.definitions().find("workbench.refine", 0, "operation").raw))); refiner.item_tags = [];
	var anyRefine = new CM.core.ItemModifierRules.Definition(MD.parse(JSON.stringify(refiner)));
	throws("no-quality", function () { CM.core.ItemModifierRules.validate(IM.data(cloak), anyRefine, "-", IM.definitions(), 1); }, "declares no quality: nothing would read it");
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IM.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
