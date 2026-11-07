// I04 declarative crafting operations: a three-operation workbench (protect, replace, upgrade) and one staged production
// recipe. Previews explain invalid targets and protected groups; a failed validation consumes nothing; a post-commit
// listener cannot retry the craft; replace removes and adds in one settlement; upgrade keeps every modifier; each stage of
// the recipe settles once, its progress on the work piece, and only the last stage draws the product's rolls.
FIXFILE = "i04-workbench";
var IM = CM.core.ItemModifiers, IC = CM.core.ItemCrafting, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, RS = CM.core.RandomStreams;
var Files = java.nio.file.Files, JString = java.lang.String;
var root = Files.createTempDirectory("sud-i04-").toFile();
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
// a managed item by the name it was made with (its shown name carries its modifiers)
function made(a, name) { for (var i = 0; i < a.numItems(); i++) { var I = a.getItem(i); if (IM.isManaged(I) && data(I).base_name == name) return I; } return null; }
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
	var ops = s(IC.operations());
	check("workbench-listed", ops.indexOf("workbench.replace - Unpick and reweave one modifier (replace)") >= 0 && ops.indexOf("workbench.upgrade - Extend the cloak (upgrade)") >= 0
		&& ops.indexOf("workbench.protect - Bind one modifier (protect)") >= 0 && ops.indexOf("workbench.cloak-work - Make a woven cloak (staged recipe; MODCRAFT BEGIN workbench.cloak-work)") >= 0, ops);

	var A = actor("Bench Ann");
	var forge = CMClass.getItem("GenItem"), station = CMClass.getAbility("Prop_Workstation"); station.setMiscText("TAG=ember-forge"); forge.addNonUninvokableEffect(station); R.addItem(forge); items.push(forge);
	var iron = pile(A, RM.RESOURCE_IRON, 20, "some bench iron");
	var cloak = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_cloak", null, A, R).get(0); items.push(cloak); A.addItem(cloak);
	var d0 = data(cloak), pre = -1, suf = -1;
	for (var i = 0; i < d0.modifiers.length; i++) { if (d0.modifiers[i].position == "prefix") pre = i; else suf = i; }
	check("cloak-made", d0.modifiers.length == 2 && pre >= 0 && suf >= 0 && d0.modifiers[suf].group == "fire-penetration", s(IM.inspect(cloak)));
	var prefixName = s(d0.modifiers[pre].name);

	// protect the prefix: the workbench's first operation
	IC.prepare(A, "workbench.protect", cloak, s(pre + 1)); var said = s(IC.commit(A, 0));
	check("protect", data(cloak).modifiers[pre]["protected"] === true && said.indexOf("Committed craft") >= 0, said);

	// previews explain: a protected target, a protected group in the way, a target that is not there; nothing is spent or drawn
	var w = iron.basePhyStats().weight(), st = state(A), dice = CMath.getRandState();
	throws("preview-protected", function () { IC.preview(A, "workbench.replace", cloak, (pre + 1) + " embertrial.fireguard1"); }, "modifier is protected: " + prefixName + ", group fire-resistance; unprotect it first");
	throws("preview-protected-group", function () { IC.preview(A, "workbench.replace", cloak, (suf + 1) + " embertrial.fireguard2"); }, "exclusive group fire-resistance already present: " + prefixName + " (protected)");
	throws("preview-no-target", function () { IC.preview(A, "workbench.replace", cloak, "9 embertrial.piercing"); }, "no such modifier number");
	throws("prepare-refused", function () { IC.prepare(A, "workbench.replace", cloak, (pre + 1) + " embertrial.fireguard1"); }, "is protected");
	check("refusals-consume-nothing", iron.basePhyStats().weight() == w && state(A) == st && dice.equals(CMath.getRandState()) && data(cloak).revision == d0.revision + 1, "something was spent");

	// replace: remove and add in one settlement; a listener after the commit cannot retry it or start another
	var heard = 0, retried = "", restarted = false;
	cm.on("itemCrafted", function (who, target, receipt) {
		if (who !== A) return;
		heard++;
		var r = JSON.parse(s(receipt));
		retried = s(IC.commit(A, r.request));
		try { IC.prepare(A, "workbench.protect", cloak, "1"); } catch (e) { restarted = true; }
		return false;
	}, "fix-i04-listener");
	var before = data(cloak);
	var preview = s(IC.preview(A, "workbench.replace", cloak, (suf + 1) + " embertrial.piercing"));
	IC.prepare(A, "workbench.replace", cloak, (suf + 1) + " embertrial.piercing"); var serial = JSON.parse(state(A)).serial;
	said = s(IC.commit(A, serial));
	var after = data(cloak);
	check("replace", preview.indexOf("Roll ranges: {\"penetration.fire\"") >= 0 && said.indexOf("Committed craft #" + serial) >= 0 && after.revision == before.revision + 1
		&& after.modifiers.length == 2 && after.modifiers[suf].uid != before.modifiers[suf].uid && after.modifiers[pre].uid == before.modifiers[pre].uid
		&& iron.basePhyStats().weight() == w - 2, said + " / " + JSON.stringify(after.modifiers));
	check("listener-cannot-retry", heard == 1 && retried.indexOf("Already settled craft #" + serial) >= 0 && restarted && data(cloak).revision == after.revision && iron.basePhyStats().weight() == w - 2,
		"heard " + heard + " retried '" + retried + "' restarted " + restarted);
	check("commit-once", s(IC.commit(A, serial)).indexOf("Already settled") >= 0 && iron.basePhyStats().weight() == w - 2, "a second commit spent");

	// upgrade: the base's successor, every modifier kept
	var up = s(IC.preview(A, "workbench.upgrade", cloak, ""));
	check("upgrade-preview", up.indexOf("Upgrades to workbench.greatcloak.v1, keeping every modifier and its rolls.") >= 0, up);
	IC.prepare(A, "workbench.upgrade", cloak, ""); IC.commit(A, 0);
	var big = data(cloak);
	check("upgrade", big.base.id == "workbench.greatcloak" && big.prefixes == 2 && JSON.stringify(big.modifiers) == JSON.stringify(after.modifiers) && iron.basePhyStats().weight() == w - 5,
		JSON.stringify(big));
	throws("upgrade-no-successor", function () { IC.preview(A, "workbench.upgrade", cloak, ""); }, "workbench.greatcloak.v1 declares no successor to upgrade to");
	// a failed validation: too little iron; nothing changes
	iron.basePhyStats().setWeight(1); iron.recoverPhyStats(); st = state(A);
	throws("short-material", function () { IC.prepare(A, "workbench.replace", cloak, (suf + 1) + " embertrial.piercing"); }, "need 2 weight unit(s) of plain IRON");
	check("short-consumes-nothing", state(A) == st && iron.basePhyStats().weight() == 1, "changed");
	iron.basePhyStats().setWeight(20); iron.recoverPhyStats();

	// the staged recipe: a work piece, three stages settled one at a time, the product at the last
	var P = CMClass.getArmor("GenArmor"); P.setName("a woven cloak"); P.setDisplayText("A woven cloak lies here."); P.recoverPhyStats(); CMLib.catalog().addCatalog(P); P.destroy(); catalogued = "a woven cloak";
	var cotton = pile(A, RM.RESOURCE_COTTON, 3, "some bench cotton");
	s(IC.begin(A, "workbench.cloak-work"));
	var piece = carried(A, "an unfinished cloak"); if (piece != null) items.push(piece);
	var status = s(IC.status(A));
	check("begin", piece != null && piece.fetchEffect("Prop_WorkOrder") != null
		&& status.indexOf("Work piece an unfinished cloak: Make a woven cloak, 0 of 3 stage(s) done; next Spin the cloth (2 COTTON at ember-forge)") >= 0, status);
	var sp = s(IC.preview(A, "workbench.cloak-work", piece, ""));
	check("stage-preview", sp.indexOf("stage 1 of 3 (Spin the cloth)") >= 0 && sp.indexOf("cost 2 weight unit(s) of plain COTTON") >= 0, sp);
	dice = CMath.getRandState(); var lootAt = RS.counter("loot");
	IC.prepare(A, "workbench.cloak-work", piece, ""); serial = JSON.parse(state(A)).serial; said = s(IC.commit(A, serial));
	check("stage-one", said.indexOf("stage 1 of 3 (Spin the cloth)") >= 0 && cotton.basePhyStats().weight() == 1 && JSON.parse(s(piece.fetchEffect("Prop_WorkOrder").text())).stage == 1
		&& dice.equals(CMath.getRandState()) && RS.counter("loot") == lootAt, said);
	check("stage-once", s(IC.commit(A, serial)).indexOf("Already settled") >= 0 && cotton.basePhyStats().weight() == 1, "stage settled twice");
	IC.prepare(A, "workbench.cloak-work", piece, ""); IC.commit(A, 0);
	check("stage-two", JSON.parse(s(piece.fetchEffect("Prop_WorkOrder").text())).stage == 2 && s(IC.status(A)).indexOf("2 of 3 stage(s) done; next Rivet the clasp (1 IRON") >= 0, s(IC.status(A)));
	IC.prepare(A, "workbench.cloak-work", piece, ""); said = s(IC.commit(A, 0));
	var product = made(A, "a woven cloak"); if (product != null) items.push(product);
	check("product", said.indexOf("is finished") >= 0 && product != null && IM.isManaged(product) && data(product).modifiers.length == 1 && data(product).base.id == "workbench.cloak"
		&& s(data(product).source).indexOf("recipe:workbench.cloak-work.v1") == 0 && piece.amDestroyed() && carried(A, "an unfinished cloak") == null, said + " / " + (product == null ? "no product" : s(IM.inspect(product))));
	check("no-piece-left", s(IC.status(A)).indexOf("Work piece") < 0, s(IC.status(A)));
} finally {
	CM.core.JSEvents.off("itemcrafted", "fix-i04-listener");
	if (catalogued != null) { var c = CMLib.catalog().getCatalogItem(catalogued); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IM.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
