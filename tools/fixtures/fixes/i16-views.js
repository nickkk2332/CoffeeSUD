// I16 inventory views: LOOT SELF shows everything a character carries (worn and in a container too) through their own LOOT
// filter, each item by rarity, tracked or untracked, and names each item the filter leaves out with why; the filter takes
// UNTRACKED (untracked items) and TRACKED (tracked items no policy dropped) beside the rarities, in LOOT of a container too.
FIXFILE = "i16-views";
var diceAtStart = CMath.getRandState();
var IM = CM.core.ItemModifiers, IL = CM.core.ItemLoot, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-i16-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
function filter(a, words) { var L = new java.util.ArrayList(); for (var i = 0; i < words.length; i++) L.add(words[i]); return s(IL.setFilter(a, L)); }
var R = scratchRoom(), actors = [], items = [];
try {
	var ex = new java.io.File("../SUDDocs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "loot-policies"), root);
	copy(new java.io.File(ex, "workbench"), root);
	WorldFiles.configure(root); CM.core.CombatProfiles.reset(); IM.reset(); IL.reset(); CM.core.Affixes.reload();
	var plan = MD.load(root, "items.modifiers,items.loot", ""); currentField.set(null, OM.prepare(plan, null, false));
	check("definitions-valid", plan.errors.isEmpty(), s(plan.problems()));

	var A = newMob("GenMob", R); A.setName("Packer Pim"); actors.push(A);
	var dropped = CM.core.GenCalls.items("randareas/loot-policies.xml", "lootpolicy_mantle", null, A, R).get(0); items.push(dropped); A.addItem(dropped);
	var made = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_cloak", null, A, R).get(0); items.push(made); A.addItem(made);
	var pack = CMClass.getItem("GenContainer"); pack.setName("a fixi16 pack"); pack.setCapacity(50); A.addItem(pack); items.push(pack);
	var rope = CMClass.getItem("GenItem"); rope.setName("a fixi16 rope"); A.addItem(rope); rope.setContainer(pack); items.push(rope);
	var ring = CMClass.getItem("GenItem"); ring.setName("a fixi16 ring"); ring.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_LEFT_FINGER); A.addItem(ring); ring.wearAt(CM.Items.interfaces.Wearable.WORN_LEFT_FINGER); items.push(ring);
	var rarity = s(IL.dropOf(dropped)[2]);

	// every item, by what it is, with where it is
	var all = s(IL.selfView(A));
	check("everything", all.indexOf("You carry:") >= 0 && all.indexOf("Tracked: " + s(made.Name())) >= 0 && all.indexOf("Untracked: a fixi16 rope (in a fixi16 pack)") >= 0
		&& all.indexOf("Untracked: a fixi16 ring (worn)") >= 0 && all.indexOf(": " + s(dropped.Name())) >= 0 && all.indexOf("Left out") < 0, all);

	// a filter of untracked items: each other item named with why it is left out
	said = filter(A, ["untracked"]);
	var plainView = s(IL.selfView(A));
	check("plain-only", said.indexOf("LOOT now shows untracked items") >= 0 && plainView.indexOf("Untracked: a fixi16 rope") >= 0 && plainView.indexOf("Tracked: ") < 0
		&& plainView.indexOf("Left out by your filter") >= 0 && plainView.indexOf(s(made.Name()) + ": Tracked (tracked), and your filter shows untracked") >= 0
		&& plainView.indexOf(s(dropped.Name()) + ": ") >= 0 && plainView.indexOf("(" + rarity + "), and your filter shows untracked") >= 0, plainView);

	// tracked items no policy dropped, and a rarity
	filter(A, ["tracked", rarity]);
	var tv = s(IL.selfView(A));
	check("tracked-and-rarity", tv.indexOf("Tracked: " + s(made.Name())) >= 0 && tv.indexOf(s(dropped.Name())) < tv.indexOf("Left out") && tv.indexOf("a fixi16 rope (in a fixi16 pack): Untracked (untracked)") >= 0, tv);

	// the container view takes the same words: plain items shown when the filter names them
	filter(A, ["untracked"]);
	var cv = s(IL.view(A, pack));
	check("container-plain", cv.indexOf("In a fixi16 pack:") >= 0 && cv.indexOf("Untracked: a fixi16 rope") >= 0, cv);
	throws("unknown-word", function () { filter(A, ["shiny"]); }, "untracked");
	filter(A, ["all"]);
	check("all-again", s(IL.selfView(A)).indexOf("Left out") < 0, s(IL.selfView(A)));
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); IM.reset(); IL.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset();
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
