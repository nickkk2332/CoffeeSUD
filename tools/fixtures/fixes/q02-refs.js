// Q02 cross-family references: one check follows a support's fit, a trigger's skill, a reward's items, an encounter's
// mobs, an expedition's destination and return room, a loot policy's base and a recipe's product together; each broken
// reference names its file and JSON path; a mob that carries scripts is reported as unknown, not as safe or broken.
FIXFILE = "q02-refs";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
var CR = CM.core.CrossRefs, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, WC = CM.core.WorldCheck;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-q02-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function write(f, obj) { f.getParentFile().mkdirs(); Files.write(f.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function has(text, part) { return text.indexOf(part) >= 0; }
var catalogued = [];
function catalogMob(name, scripted) {
	var m = CMClass.getMOB("GenMob"); m.setName(name);
	if (scripted) { var B = CMClass.getBehavior("Scriptable"); B.setParms("GREET_PROG 100\nsay hello\n~\n"); m.addBehavior(B); }
	m.text(); CMLib.catalog().addCatalog(m); catalogued.push(name);
}
try {
	var ex = new java.io.File("../docs/creator/examples");
	var packs = ["ability-composition", "named-resources", "causal-triggers", "reward-escrow", "encounter-director", "expeditions", "progression-tracks", "item-modifiers", "workbench", "loot-policies"];
	for (var i = 0; i < packs.length; i++) copy(new java.io.File(ex, packs[i]), folder);
	new java.io.File(folder, "README.md").delete();
	// broken references, one of each kind, and a scripted mob
	write(new java.io.File(folder, "compositions/fix.orphan.v1.json"), { schema: 1, kind: "support", id: "fix.orphan", version: 1, name: "An orphan support", requires_all: ["nothing-has-this"], modifiers: [{ stat: "damage", op: "more", value: 10 }] });
	write(new java.io.File(folder, "triggers/fix.badskill.v1.json"), { schema: 1, id: "fix.badskill", version: 1, on: "block", action: { type: "cast", skill: "fix.noskill", target: "other" } });
	write(new java.io.File(folder, "rewards/fix.badbundle.v1.json"), { schema: 1, id: "fix.badbundle", version: 1, kind: "bundle", items: [{ name: "a fixq02 nothing" }] });
	write(new java.io.File(folder, "directors/fix.scripted.v1.json"), { schema: 1, id: "fix.scripted", version: 1, budget: { actors: 2, loot: 1 }, activity: "example.trial",
		phases: [{ id: "a", enter: { on: "start" }, spawn: [{ mob: "a fixq02 scripted imp", role: "imp" }], reward: "fix.missingreward" }], complete: { when: "cleared" } });
	catalogMob("a fixq02 scripted imp", true);
	WorldFiles.configure(folder);
	currentField.set(null, OM.prepare(MD.load(folder, "", ""), null, false));

	var report = s(CR.report());
	check("support-craft-expedition", has(report, "ERROR compositions/fix.orphan.v1.json: /requires_all: support fix.orphan fits no installed skill")
		&& has(report, "ERROR itemmods/workbench.cloak-work.v1.json: /result/catalog: no catalog item 'a woven cloak'")
		&& has(report, "ERROR expeditions/example.depths.v1.json: /destination/area: no area Ember Depths")
		&& has(report, "ERROR expeditions/example.depths.v1.json: /return_room: no room Camp#0"), report);
	check("trigger-reward-encounter", has(report, "ERROR triggers/fix.badskill.v1.json: /action/skill: no composition fix.noskill is installed in compositions/")
		&& has(report, "ERROR rewards/fix.badbundle.v1.json: /items/0/name: no catalog item 'a fixq02 nothing'")
		&& has(report, "ERROR directors/fix.scripted.v1.json: /phases/0/reward: no reward bundle fix.missingreward in rewards/"), report);
	check("unknown-script", has(report, "UNKNOWN directors/fix.scripted.v1.json: /phases/0/spawn/0/mob: 'a fixq02 scripted imp' carries scripts; what they do is not checked")
		&& !has(report, "ERROR directors/fix.scripted.v1.json: /phases/0/spawn/0/mob"), report);
	check("found-ones", has(report, "References between the module families: ") && !has(report, "/base: unavailable base embertrial.mantle")
		&& !has(report, "ERROR loot/example.coffer.v1.json") && !has(report, "ERROR tracks/example.emberwright"), report);
	check("counts", /(\d+) followed, (\d+) found, (\d+) broken, (\d+) unknown/.test(report) && Number(RegExp.$3) >= 8 && Number(RegExp.$4) >= 1 && Number(RegExp.$2) > 10, report.split("\n")[0]);
	// WORLD CHECK ONLY=REFS: the same findings, as warnings in a world that selects none of the families
	var parts = new java.util.HashSet(); parts.add("REFS");
	var findings = WC.run(null, parts), text = [], errors = 0;
	for (var f = 0; f < findings.size(); f++) { text.push(s(findings.get(f).what)); if (s(findings.get(f).severity) == "ERROR") errors++; }
	check("world-check", text.join("\n").indexOf("triggers/fix.badskill.v1.json: /action/skill") >= 0 && text.join("\n").indexOf("unknown: directors/fix.scripted.v1.json") >= 0 && errors == 0, text.join("\n"));
	check("in-parts", WC.parts().contains("REFS"), s(WC.parts()));
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogMob(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign);
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
