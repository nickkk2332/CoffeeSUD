// I02 loot policies, Q07 loot view: a seeded policy with rarities, tier limits by level, tags, a power budget and a
// finite guarantee counter; its preview and its samples agree about eligibility; a sample predicts the next real drops
// exactly and moves nothing; the counter forces the guaranteed rarity and resets; an inactive module keeps the roll;
// LOOT shows a container's loot by rarity through each character's filter, in plain text, for no game time.
FIXFILE = "i02-loot";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
var IL = CM.core.ItemLoot, IM = CM.core.ItemModifiers, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, RS = CM.core.RandomStreams, MS = CM.core.ModuleStats;
var Files = java.nio.file.Files, JString = java.lang.String;
var root = Files.createTempDirectory("sud-i02-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function write(f, obj) { Files.write(f.toPath(), new JString(typeof obj == "string" ? obj : JSON.stringify(obj)).getBytes("UTF-8")); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var R = scratchRoom(), actors = [], items = [];
function actor(name) { var a = newMob("GenMob", R); a.setName(name); actors.push(a); return a; }
function drop(policy, level) { var I = CMClass.getArmor("GenArmor"); I.setName("a fixi02 mantle"); I.setDisplayText("A fixi02 mantle lies here."); I.basePhyStats().setLevel(level || 1); I.recoverPhyStats(); items.push(I); IM.initialize(I, "POLICY=" + policy, 0, "fixture"); return I; }
function rec(I) { return JSON.parse(s(MD.json(IM.data(I)))); }
function rarityOf(I) { var d = IL.dropOf(I); return d == null ? "" : s(d[2]); }
function affixes(I) { var m = rec(I).modifiers, out = []; for (var i = 0; i < m.length; i++) out.push(s(m[i].pin.id)); return out; }
var plan = null;
function boot(modules) { plan = MD.load(root, modules, ""); currentField.set(null, OM.prepare(plan, null, false)); IL.reset(); IM.reset(); }
try {
	var ex = new java.io.File("../docs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "loot-policies"), root);
	var loot = new java.io.File(root, "loot");
	// a policy whose rare drop comes only from the guarantee, and one whose budget binds
	write(new java.io.File(loot, "fix.lucky.v1.json"), { schema: 1, id: "fix.lucky", version: 1, base: "embertrial.mantle",
		rarities: [{ id: "plain", title: "Plain", weight: 100, modifiers: 0 }, { id: "rare", title: "Rare", weight: 0.000001, modifiers: 2 }], guarantee: { rarity: "rare", after: 4 } });
	write(new java.io.File(loot, "fix.budget.v1.json"), { schema: 1, id: "fix.budget", version: 1, base: "embertrial.mantle",
		rarities: [{ id: "rare", weight: 1, modifiers: 2 }], budget: 2 });
	WorldFiles.configure(root); CM.core.CombatProfiles.reset(); CM.core.Affixes.reload();
	boot("items.modifiers,items.loot");
	check("manifest-valid", plan.errors.isEmpty() && IL.enabled() && IL.definitions().errors.isEmpty() && IL.definitions().entries.size() == 3, s(plan.problems()) + " / " + s(IL.definitions().report()));
	var found = s(java.lang.String.join(" | ", IL.FAMILY_ENTRY.check()));
	check("check-clean", found.length == 0, found);

	// the preview: chances, the tier limit at the level, eligibility with reasons; it draws nothing
	RS.seed(); // the campaign's seed is chosen once, from its dice, the first time any stream is used
	var before = RS.counter("loot"), dice = CMath.getRandState();
	var p1 = s(IL.preview("example.coffer", 1)), p10 = s(IL.preview("example.coffer", 10));
	check("preview", p1.indexOf("Plain (plain): 60.0%, up to 0 modifier(s)") >= 0 && p1.indexOf("Rare (rare): 10.0%, up to 2 modifier(s)") >= 0
		&& p1.indexOf("embertrial.fireguard2 (Flameward, prefix tier 2): not eligible: tier 2 is above the limit of 1 at level 1") >= 0
		&& p1.indexOf("embertrial.fireguard1 (Wardwoven, prefix tier 1): eligible, 50.0% of first picks") >= 0
		&& p1.indexOf("Guarantee: after 4 drop(s) without Rare, the next is Rare") >= 0, p1);
	check("preview-level", p10.indexOf("embertrial.fireguard2 (Flameward, prefix tier 2): eligible") >= 0 && p10.indexOf("tier 2 and below") >= 0, p10);
	// samples: they agree with the preview, and move neither the stream nor the counter
	var s1 = s(IL.sample("example.coffer", 400, 1)), s10 = s(IL.sample("example.coffer", 400, 10));
	check("sample-agrees", s1.indexOf("Every sampled modifier is one the preview lists as eligible.") >= 0 && s1.indexOf("embertrial.fireguard2:") < 0
		&& s10.indexOf("Every sampled modifier is one the preview lists as eligible.") >= 0 && s10.indexOf("embertrial.fireguard2:") >= 0, s1 + "\n" + s10);
	check("inspection-draws-nothing", RS.counter("loot") == before && dice.equals(CMath.getRandState()) && IL.since("example.coffer") == 0, "loot " + before + " -> " + RS.counter("loot"));

	// a sample predicts the next real drops exactly: the same rarities and modifiers
	var predicted = s(IL.sample("example.coffer", 6, 1));
	var made = { plain: 0, magic: 0, rare: 0 }, mods = {};
	for (var i = 0; i < 6; i++) { var I = drop("example.coffer", 1); made[rarityOf(I)]++; var a = affixes(I); for (var j = 0; j < a.length; j++) mods[a[j]] = (mods[a[j]] || 0) + 1; }
	var same = predicted.indexOf("Plain: " + made.plain + " (") >= 0 && predicted.indexOf("Magic: " + made.magic + " (") >= 0 && predicted.indexOf("Rare: " + made.rare + " (") >= 0;
	for (var k in mods) same = same && predicted.indexOf(k + ": " + mods[k]) >= 0;
	check("sample-predicts-drops", same && RS.counter("loot") > before, predicted + "\nmade " + JSON.stringify(made) + " " + JSON.stringify(mods));
	check("drop-record", rec(I).source.indexOf("loot:example.coffer.v1:") == 0 && rec(I).base.id == "embertrial.mantle", JSON.stringify(rec(I)));

	// the guarantee: three plain drops, then the fourth is rare; the counter resets; the run comes again
	var seq = [];
	for (var g = 0; g < 8; g++) seq.push(rarityOf(drop("fix.lucky", 1)));
	check("guarantee", seq.join(",") == "plain,plain,plain,rare,plain,plain,plain,rare" && IL.since("fix.lucky") == 0
		&& MS.value("loot", "fix.lucky", "guaranteed drops") == 2, seq.join(",") + " since " + IL.since("fix.lucky"));
	var gi = drop("fix.lucky", 1); var gp = s(IL.preview("fix.lucky", 1));
	check("guarantee-preview", IL.since("fix.lucky") == 1 && gp.indexOf("Drops since the last: 1.") >= 0, gp);

	// tier limits and the power budget hold on every drop
	var within = true, note = "";
	for (var b = 0; b < 30; b++) {
		var B = drop("fix.budget", 10), m = rec(B).modifiers, sum = 0;
		for (var t = 0; t < m.length; t++) sum += m[t].tier;
		if (sum > 2) { within = false; note = JSON.stringify(m); }
	}
	var low = true;
	for (var c = 0; c < 30; c++) { var L = drop("example.coffer", 1); if (affixes(L).indexOf("embertrial.fireguard2") >= 0) { low = false; } }
	check("budget-and-tiers", within && low, note);

	// the module off: the drop is refused as unavailable, so a loot table keeps its roll
	boot("items.modifiers");
	var off = CMClass.getArmor("GenArmor"); off.setName("a fixi02 mantle"); items.push(off);
	var unavailable = false; try { IM.initialize(off, "POLICY=example.coffer", 0, "fixture"); } catch (e) { unavailable = IM.unavailable(e.javaException || e); }
	check("inactive-keeps-roll", unavailable && !IM.isManaged(off), "refused as unavailable: " + unavailable);
	boot("items.modifiers,items.loot");

	// Q07: LOOT, a coffer's loot by rarity, through a character's own filter
	var ann = actor("Loot Ann"), bo = actor("Loot Bo");
	var coffer = CMClass.getItem("GenContainer"); coffer.setName("a fixi02 coffer"); coffer.setCapacity(500); coffer.setDoorsNLocks(true, true, true, false, false, false); R.addItem(coffer); items.push(coffer);
	var inCoffer = [drop("fix.lucky", 1), drop("fix.lucky", 1), drop("fix.lucky", 1), drop("fix.lucky", 1)]; // a new campaign's counter: three plain, then rare
	for (var q = 0; q < inCoffer.length; q++) { R.addItem(inCoffer[q]); inCoffer[q].setContainer(coffer); }
	var pebble = CMClass.getItem("GenItem"); pebble.setName("a fixi02 pebble"); R.addItem(pebble); pebble.setContainer(coffer); items.push(pebble);
	var cmd = CMClass.getCommand("LootCmd"), told = [];
	function view(who) { return s(IL.view(who, coffer)); }
	var all = view(ann);
	check("loot-view", all.indexOf("In a fixi02 coffer:") >= 0 && all.indexOf("Rare: a ") >= 0 && all.split("Plain: ").length == 4 && all.indexOf("1 other item(s).") >= 0
		&& all.indexOf("Embers (penetration fire ") >= 0 && all.indexOf(", no modifiers") >= 0, all);
	IL.setFilter(ann, java.util.Arrays.asList("rare"));
	var rare = view(ann), other = view(bo);
	check("loot-filter", rare.indexOf("Rare: ") >= 0 && rare.indexOf("Plain: ") < 0 && rare.indexOf("3 tracked item(s) your filter leaves out") >= 0
		&& other.split("Plain: ").length == 4, rare + "\n--\n" + other);
	throws("loot-filter-unknown", function () { IL.setFilter(ann, java.util.Arrays.asList("legendary")); }, "no installed loot policy has a rarity 'legendary'");
	check("loot-free", cmd.checkedActionsCost(ann, CM.core.CMParms.parse("LOOT coffer")) == 0 && cmd.checkedActionsCost(ann, CM.core.CMParms.parse("LOOT FILTER ALL")) == 0, "LOOT costs time");
	IL.setFilter(ann, java.util.Arrays.asList("ALL"));
	check("loot-filter-all", view(ann).split("Plain: ").length == 4, view(ann));
	coffer.setDoorsNLocks(true, false, true, false, false, false);
	check("loot-closed", view(ann).indexOf("is closed") >= 0, view(ann));

	// checks: a policy on a base that is not installed, a guarantee of a rarity it lacks
	write(new java.io.File(loot, "fix.nobase.v1.json"), { schema: 1, id: "fix.nobase", version: 1, base: "fix.missing", rarities: [{ id: "plain" }] });
	write(new java.io.File(loot, "fix.badluck.v1.json"), { schema: 1, id: "fix.badluck", version: 1, base: "embertrial.mantle", rarities: [{ id: "plain" }], guarantee: { rarity: "rare", after: 3 } });
	found = s(java.lang.String.join(" | ", IL.FAMILY_ENTRY.check()));
	check("check-refs", found.indexOf("loot/fix.nobase.v1.json: /base: no modifier base fix.missing in itemmods/") >= 0
		&& found.indexOf("loot/fix.badluck.v1.json: /guarantee/rarity: no rarity rare in this policy") >= 0, found);
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IL.reset(); IM.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
