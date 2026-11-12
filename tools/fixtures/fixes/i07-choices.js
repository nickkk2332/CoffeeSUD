// I07 and I17, saved choices (items.choices): a veiled modifier from a loot rarity, revealed at a station into three legal
// alternatives drawn once from the offers stream and kept on the item; opening it again shows the same; a pick settles one, once,
// and the first reveal of an affix goes in the codex with the discovery reward, never twice. An offering takes an item for good,
// draws three replacements kept on the character, and is settled by a pick or abandoned by its refund rule.
FIXFILE = "i07-choices";
var diceAtStart = CMath.getRandState();
var IM = CM.core.ItemModifiers, IC = CM.core.ItemChoices, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, RS = CM.core.RandomStreams;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-i07-").toFile();
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
function choices(a) { return JSON.parse(s(MD.json(OM.get("items.choices", OM.character(a))))); }
function station(tag) { var I = CMClass.getItem("GenItem"), P = CMClass.getAbility("Prop_Workstation"); P.setMiscText("TAG=" + tag); I.addNonUninvokableEffect(P); R.addItem(I); items.push(I); return I; }
function amberUnits(a) { var n = 0; for (var i = 0; i < a.numItems(); i++) { var I = a.getItem(i); if (I.material() == RM.RESOURCE_AMBER && s(I.ID()) == "GenResource") n += I.basePhyStats().weight(); } return n; }
function charm(a) { var I = CM.core.GenCalls.items("randareas/saved-choices.xml", "choices_veiled", null, a, R).get(0); items.push(I); a.addItem(I); return I; }
// a charm whose veil is a prefix: the charm's other prefixes (three, outside the one's group) can be its options
function prefixCharm(a) { for (var k = 0; k < 40; k++) { var I = charm(a); var d = data(I); if (d.veiled && d.veiled[0].position == "prefix" && d.modifiers.length == 1 && d.modifiers[0].position == "prefix") return I; a.delItem(I); I.destroy(); } return null; }
function carriedNamed(a, part) { for (var i = 0; i < a.numItems(); i++) if (s(a.getItem(i).Name()).indexOf(part) >= 0) return a.getItem(i); return null; }
var RM = CM.Items.interfaces.RawMaterial;
try {
	var ex = new java.io.File("../SUDDocs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "loot-policies/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "saved-choices"), root);
	WorldFiles.configure(root); CM.core.CombatProfiles.reset(); IM.reset(); CM.core.Affixes.reload(); CM.core.ItemLoot.reset(); IC.reset();
	var plan = MD.load(root, "items.modifiers,items.loot,items.choices", ""); currentField.set(null, OM.prepare(plan, null, false));
	check("definitions-valid", plan.errors.isEmpty() && IM.check().errors.isEmpty() && IC.FAMILY_ENTRY.check().isEmpty() && CM.core.ItemLoot.FAMILY_ENTRY.check().isEmpty(),
		s(plan.problems()) + " / " + s(IM.check().report()) + " / " + s(IC.FAMILY_ENTRY.check()));
	var listed = s(IC.FAMILY_ENTRY.list());
	check("listed", listed.indexOf("choices.seer.v1 (The seer's reading): reveal at seer-table, 3 option(s), cost 1 weight unit(s) of plain AMBER; a first reveal of an affix gives 25 XP") >= 0
		&& listed.indexOf("choices.altar.v1 (The ember altar): offering at ember-altar, 3 option(s), cost 1 weight unit(s) of plain AMBER; makes an altar charm by base choices.charm; abandoning gives back the cost") >= 0, listed);

	var A = actor("Seer Sela");
	var amber = pile(A, RM.RESOURCE_AMBER, 6, "some ember amber");
	station("seer-table"); station("ember-altar");

	// a loot rarity's last pick comes veiled: it holds its slot, gives nothing, and ITEMMODS says so
	var C = prefixCharm(A);
	check("veiled-drop", C != null && data(C).veiled.length == 1 && !data(C).veiled[0].options, C == null ? "no prefix-veiled charm in 40 drops" : JSON.stringify(data(C)));
	var look = s(IM.inspect(C));
	check("inspect-veil", look.indexOf("Veiled 1. a veiled prefix (a Veiled drop): not yet revealed; it gives nothing until it is") >= 0, look);
	var group = s(data(C).modifiers[0].group);

	// the preview: what the reveal can draw, and nothing paid or drawn
	var at = RS.counter("offers"), dice = CMath.getRandState();
	var pv = s(IC.preview(A, "choices.seer", C, ""));
	check("reveal-preview", pv.indexOf("It reveals veiled modifier 1 (a prefix): up to 3 different affixes") >= 0 && pv.indexOf("cannot be drawn: not a prefix") >= 0
		&& pv.indexOf("cannot be drawn: exclusive group " + group + " already present") >= 0 && pv.indexOf("The first time you reveal an affix:") >= 0 && pv.indexOf("Nothing was paid or drawn.") >= 0, pv);
	check("preview-free", RS.counter("offers") == at && amberUnits(A) == 6 && dice.equals(CMath.getRandState()), "something was paid or drawn");

	// the reveal: three legal alternatives, distinct, drawn once from the offers stream and kept on the item
	var said = s(IC.open(A, "choices.seer", C, ""));
	var veil = data(C).veiled[0], opts = veil.options || [], ids = {};
	for (var i = 0; i < opts.length; i++) ids[opts[i].pin.id] = true;
	check("revealed", said.indexOf("You reveal veiled modifier 1 of") >= 0 && opts.length == 3 && Object.keys(ids).length == 3 && veil.by.id == "choices.seer"
		&& RS.counter("offers") > at && amberUnits(A) == 5 && opts.every(function (o) { return o.position == "prefix" && o.group != group; }), said + " / " + JSON.stringify(veil));
	var kept = JSON.stringify(opts), counter = RS.counter("offers");
	throws("reveal-once", function () { IC.open(A, "choices.seer", C, ""); }, "is revealed already: its options are kept");
	check("reopen-same", JSON.stringify(data(C).veiled[0].options) == kept && RS.counter("offers") == counter && amberUnits(A) == 5, "the options changed or something was paid");
	// what restoring does: the item's record read again from its text, after the families forgot what they had loaded
	IM.reset(); IC.reset(); CM.core.ItemLoot.reset();
	check("restored-same", JSON.stringify(data(C).veiled[0].options) == kept, "the options changed on reading back");
	var menu = s(IC.list(A));
	check("choices-list", menu.indexOf("veiled 1 (a prefix): revealed; CHOICES PICK") >= 0 && menu.indexOf(s(opts[1].name)) >= 0, menu);

	// a pick settles one; the others are gone; the first reveal of an affix goes in the codex with the reward
	said = s(IC.pick(A, C, 2));
	var d2 = data(C), got = null;
	for (var i = 0; i < d2.modifiers.length; i++) if (d2.modifiers[i].pin.id == opts[1].pin.id) got = d2.modifiers[i];
	check("picked", said.indexOf("You settle") >= 0 && said.indexOf("goes in your codex: ") >= 0 && !d2.veiled && d2.modifiers.length == 2 && got != null
		&& JSON.stringify(got.values) == JSON.stringify(opts[1].values), said + " / " + JSON.stringify(d2));
	check("codex", JSON.parse(choices(A).codex)[opts[1].pin.id] == opts[1].name, JSON.stringify(choices(A)));
	throws("pick-once", function () { IC.pick(A, C, 1); }, "has no veiled modifier");

	// the same affix revealed again is known already: no second reward
	var C2 = prefixCharm(A), rec = data(C2), same = JSON.parse(JSON.stringify(got));
	same.uid = s(java.util.UUID.randomUUID().toString());
	if (rec.modifiers[0].group == same.group) { C2 = prefixCharm(A); rec = data(C2); }
	rec.veiled[0].options = [same]; rec.veiled[0].by = veil.by;
	IM.data(C2); C2.fetchEffect("Prop_ItemModifiers").setMiscText(JSON.stringify(rec));
	said = s(IC.pick(A, C2, 1));
	check("known-once", said.indexOf("You knew it already.") >= 0 && said.indexOf("codex:") < 0, said);

	// an offering: it takes a charm for good and draws three replacements, kept on the character
	var P = CMClass.getArmor("GenArmor"); P.setName("an altar charm"); P.setDisplayText("An altar charm lies here."); P.recoverPhyStats(); CMLib.catalog().addCatalog(P); P.destroy(); catalogued = "an altar charm";
	var gift = charm(A);
	var plainThing = CMClass.getItem("GenItem"); plainThing.setName("a plain pebble"); A.addItem(plainThing); items.push(plainThing);
	throws("offer-refuses", function () { IC.preview(A, "choices.altar", plainThing, ""); }, "it takes tracked items tagged [charm]");
	var op = s(IC.preview(A, "choices.altar", gift, ""));
	check("offer-preview", op.indexOf("It takes " + s(gift.Name()) + " for good, and offers 3 of an altar charm") >= 0 && op.indexOf("Abandoning gives back the cost.") >= 0 && amberUnits(A) == 5, op);
	at = RS.counter("offers");
	said = s(IC.open(A, "choices.altar", gift, ""));
	var open = JSON.parse(choices(A).offering);
	check("offered", said.indexOf("it is gone") >= 0 && gift.amDestroyed() && gift.owner() != A && open.options.length == 3 && amberUnits(A) == 4 && RS.counter("offers") > at, said);
	var gift2 = charm(A);
	throws("one-offering", function () { IC.open(A, "choices.altar", gift2, ""); }, "an offering is open already");
	var keptOffer = JSON.stringify(open.options);
	IC.reset();
	check("offer-kept", JSON.stringify(JSON.parse(choices(A).offering).options) == keptOffer && s(IC.list(A)).indexOf("Open offering of") >= 0, s(IC.list(A)));
	said = s(IC.pickOffering(A, 2));
	var took = carriedNamed(A, "altar charm"); if (took != null) items.push(took);
	check("offer-picked", said.indexOf("You take") >= 0 && took != null && IM.isManaged(took) && s(took.Name()) == s(open.options[1].name)
		&& JSON.stringify(data(took).modifiers.map(function (m) { return m.values; })) == JSON.stringify(open.options[1].modifiers.map(function (m) { return m.values; }))
		&& choices(A).offering == "", said + " / " + (took == null ? "none" : s(took.Name()) + " vs " + open.options[1].name));
	throws("offer-pick-once", function () { IC.pickOffering(A, 1); }, "no offering is open");

	// abandoning: the refund rule gives the cost back; the offered item stays gone
	IC.open(A, "choices.altar", gift2, "");
	check("offered-again", gift2.amDestroyed() && amberUnits(A) == 3, "amber " + amberUnits(A));
	said = s(IC.abandon(A));
	check("abandoned", said.indexOf("The cost comes back.") >= 0 && amberUnits(A) == 4 && choices(A).offering == "" && gift2.amDestroyed(), said + " / amber " + amberUnits(A));
	throws("abandon-nothing", function () { IC.abandon(A); }, "no offering is open");
} finally {
	CMath.setRandState(diceAtStart.longValue());
	if (catalogued != null) { var c = CMLib.catalog().getCatalogItem(catalogued); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) { var a = actors[i]; for (var k = a.numItems() - 1; k >= 0; k--) { var I = a.getItem(k); if (I != null && !I.amDestroyed()) I.destroy(); } if (!a.amDestroyed()) a.destroy(); }
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IM.reset(); IC.reset(); CM.core.ItemLoot.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
