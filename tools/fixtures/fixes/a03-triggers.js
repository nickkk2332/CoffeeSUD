// A03 causal triggers: on-block and on-kill reactions, one action budget per causal root, a depth limit, a trigger
// at most once per root (two ripostes stop), summon ownership, cooldowns and committed events kept across a restore.
FIXFILE = "a03-triggers";
var AT = CM.core.AbilityTriggers, NR = CM.core.NamedResources, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles;
var MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, SE = CM.core.ScriptEvents, MS = CM.core.ModuleStats;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-a03-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
var turnsBefore = SE.turns();
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function copyDir(from, to) { var fs = from.listFiles(); for (var i = 0; i < fs.length; i++) write(new java.io.File(to, s(fs[i].getName())), JSON.parse(read(fs[i]))); }
function dir(name) { var d = new java.io.File(folder, name); d.mkdirs(); return d; }
var room = scratchRoom(), actors = [], catalogued = null;
function actor(name) {
	var m = newMob("GenMob", room); m.setName(name);
	m.baseState().setHitPoints(5000); m.baseState().setMana(5000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m;
}
// one Prop_Triggers per character (the engine keeps one effect of an ID): a further grant adds to it
function grant(m, ids) {
	var p = m.fetchEffect("Prop_Triggers");
	if (p != null) { p.setMiscText(s(p.text()) + "," + ids); return; }
	p = CMClass.getAbility("Prop_Triggers"); p.setMiscText("TRIGGERS=" + ids); m.addNonUninvokableEffect(p);
}
function composer(m) { var a = CMClass.getAbility("Skill_Compose"); a.setProficiency(100); m.addAbility(a); AC.learn(m, "example.ember"); return a; }
function blocker(m) { var p = CMClass.getAbility("Prop_CombatStats"); p.setMiscText('{"schema":1,"block":100,"spell_block":100}'); m.addNonUninvokableEffect(p); }
function last() { return s(java.lang.String.join(" | ", AT.lastRoot())); }
var plan = null;
function boot(budget, depth, snapshot) {
	var m = JSON.parse(read(new java.io.File(folder, "modules/abilities.triggers.json")));
	m.settings.action_budget["default"] = budget; m.settings.max_depth["default"] = depth;
	write(new java.io.File(folder, "modules/abilities.triggers.json"), m);
	plan = MD.load(folder, "abilities.composition,combat.profiles,abilities.resources,abilities.triggers", "");
	currentField.set(null, OM.prepare(plan, snapshot, snapshot != null)); AC.reset(); CP.reset(); NR.reset(); AT.reset();
	return plan;
}
try {
	var ex = new java.io.File("../docs/creator/examples");
	var mods = dir("modules");
	copyDir(new java.io.File(ex, "ability-composition/modules"), mods);
	copyDir(new java.io.File(ex, "named-resources/modules"), mods);
	copyDir(new java.io.File(ex, "causal-triggers/modules"), mods);
	copyDir(new java.io.File(ex, "named-resources/pools"), dir("pools"));
	copyDir(new java.io.File(ex, "causal-triggers/triggers"), dir("triggers"));
	var comps = dir("compositions");
	var ember = JSON.parse(read(new java.io.File(ex, "ability-composition/compositions/example.ember.v1.json")));
	ember.stats.cooldown = 0; write(new java.io.File(comps, "example.ember.v1.json"), ember);
	// a profile where every composed hit lands and is fully blocked by a blocker
	var profile = { schema: 1, id: "fix.wall", version: 1, title: "Every hit blocked", conversion: "final", hit: { minimum: 100, maximum: 100 }, block_cap: 100, block_reduction: 100 };
	write(new java.io.File(dir("combat"), "fix.wall.v1.json"), profile);
	var cpm = JSON.parse(read(new java.io.File(ex, "combat-profiles/modules/combat.profiles.json")));
	cpm.settings.profile["default"] = JSON.stringify({ hash: s(CM.core.ItemModifierRules.hash(MD.parse(JSON.stringify(profile)))), id: "fix.wall", version: 1 });
	write(new java.io.File(mods, "combat.profiles.json"), cpm);
	// test triggers: a cooldown on the turns clock, a summon
	write(new java.io.File(folder, "triggers/fix.patience.v1.json"), { schema: 1, id: "fix.patience", version: 1, on: "kill", cooldown: { amount: 5, clock: "turns" }, action: { type: "gain", pool: "example.heat", amount: 10 } });
	write(new java.io.File(folder, "triggers/fix.counter.v1.json"), { schema: 1, id: "fix.counter", version: 1, on: "block", action: { type: "cast", skill: "example.ember", target: "other" } });
	write(new java.io.File(folder, "triggers/fix.wisp.v1.json"), { schema: 1, id: "fix.wisp", version: 1, on: "kill", action: { type: "summon", mob: "a fixa03 wisp", ticks: 5 } });
	// A20: set and drain
	write(new java.io.File(folder, "triggers/fix.zero.v1.json"), { schema: 1, id: "fix.zero", version: 1, on: "kill", action: { type: "set", pool: "example.heat", amount: 0 } });
	write(new java.io.File(folder, "triggers/fix.bleed.v1.json"), { schema: 1, id: "fix.bleed", version: 1, on: "kill", action: { type: "drain", pool: "example.heat", amount: 7 } });
	WorldFiles.configure(folder);

	// the manifest's contract: bounded integer settings
	var bad = JSON.parse(read(new java.io.File(mods, "abilities.triggers.json"))); delete bad.settings.max_depth;
	write(new java.io.File(mods, "bad.json"), bad); new java.io.File(mods, "bad.json").delete();
	var badDir = Files.createTempDirectory("sud-a03-bad-").toFile(); var badMods = new java.io.File(badDir, "modules"); badMods.mkdirs();
	write(new java.io.File(badMods, "abilities.triggers.json"), bad);
	check("contract", s(MD.load(badDir, "abilities.triggers", "").problems()).indexOf("requires bounded integer settings.max_depth") >= 0, s(MD.load(badDir, "abilities.triggers", "").problems()));
	new java.io.File(badMods, "abilities.triggers.json").delete(); badMods.delete(); badDir.delete();

	boot(8, 3, null);
	check("manifest-valid", plan.errors.isEmpty() && AT.enabled(), s(plan.problems()));
	check("profile-on", OM.enabled("combat.profiles"), "combat.profiles: " + s(currentField.get(null).problem("combat.profiles")));
	var lib = AT.definitions();
	check("definitions", lib.errors.isEmpty() && lib.entries.size() == 8, s(lib.report()));

	// on kill: the killer's trigger, through the real death; the same death again changes nothing
	var ann = actor("Trigger Ann"), victim = actor("Trigger Victim");
	grant(ann, "example.harvest");
	NR.grant(ann, "example.cinder");
	CMLib.combat().postDeath(ann, victim, null);
	AT.drain();
	check("kill-gains", NR.value(ann, "example.cinder") == 1, "cinder " + NR.value(ann, "example.cinder") + " / " + last());
	AT.died(victim, ann); AT.drain();
	check("no-replay", NR.value(ann, "example.cinder") == 1 && last().indexOf("already committed") >= 0, "cinder " + NR.value(ann, "example.cinder") + " / " + last());
	// a restored campaign keeps the committed event: the same kill cannot be replayed
	var snap = s(OM.snapshot());
	boot(8, 3, snap);
	AT.died(victim, ann); AT.drain();
	check("no-replay-after-restore", NR.value(ann, "example.cinder") == 1, "cinder " + NR.value(ann, "example.cinder"));

	// a summoned creature's kill counts for its owner's include_summons triggers only
	var ben = actor("Trigger Ben"); grant(ben, "example.harvest"); NR.grant(ben, "example.cinder");
	var pet = actor("Trigger Pet");
	var sm = CMClass.getAbility("Prop_Summoned"); sm.setMiscText("ID=test FOLLOW"); sm.setInvoker(ann); pet.addNonUninvokableEffect(sm); pet.setFollowing(ann);
	var prey = actor("Trigger Prey");
	AT.died(prey, pet); AT.drain();
	check("summon-kill-owner", NR.value(ann, "example.cinder") == 2 && NR.value(ben, "example.cinder") == 0, "ann " + NR.value(ann, "example.cinder") + " ben " + NR.value(ben, "example.cinder"));
	// a trigger's summon belongs to the trigger's owner
	var wispProto = CMClass.getMOB("GenMob"); wispProto.setName("a fixa03 wisp"); CMLib.catalog().addCatalog(wispProto); catalogued = "a fixa03 wisp";
	grant(ben, "fix.wisp");
	var prey2 = actor("Trigger Prey Two");
	AT.died(prey2, ben); AT.drain();
	var wisp = room.fetchInhabitant("a fixa03 wisp");
	check("summon-owned", wisp != null && wisp.amFollowing() == ben && wisp.fetchEffect("Prop_Summoned") != null && wisp.fetchEffect("Prop_Summoned").invoker() == ben,
		"wisp follows " + (wisp == null ? "-" : wisp.amFollowing()) + " summoned " + (wisp == null ? "-" : wisp.fetchEffect("Prop_Summoned")) + " / " + last());
	if (wisp != null) actors.push(wisp);

	// a cooldown on the turns clock, kept across a restore
	grant(ann, "fix.patience"); NR.grant(ann, "example.heat");
	SE.setTurns(1000);
	var p1 = actor("Patience One"); AT.died(p1, ann); AT.drain();
	check("cooldown-first", NR.value(ann, "example.heat") == 10, "heat " + NR.value(ann, "example.heat"));
	snap = s(OM.snapshot()); boot(8, 3, snap);
	SE.setTurns(1002);
	var p2 = actor("Patience Two"); AT.died(p2, ann); AT.drain();
	check("cooldown-kept", NR.value(ann, "example.heat") == 10 && last().indexOf("cooling down") >= 0, "heat " + NR.value(ann, "example.heat") + " / " + last());
	SE.setTurns(1005);
	var p3 = actor("Patience Three"); AT.died(p3, ann); AT.drain();
	check("cooldown-over", NR.value(ann, "example.heat") == 20, "heat " + NR.value(ann, "example.heat"));

	// A20: a trigger that sets a pool, and one that drains what it can
	var cid = actor("Trigger Cid"); grant(cid, "fix.zero"); NR.grant(cid, "example.heat"); NR.set(cid, "example.heat", 40);
	AT.died(actor("Zero Prey"), cid); AT.drain();
	check("action-set", NR.value(cid, "example.heat") == 0 && last().indexOf("set example.heat to 0") >= 0, "heat " + NR.value(cid, "example.heat") + " / " + last());
	var dee = actor("Trigger Dee"); grant(dee, "fix.bleed"); NR.grant(dee, "example.heat"); NR.set(dee, "example.heat", 5);
	AT.died(actor("Bleed Prey"), dee); AT.drain();
	check("action-drain", NR.value(dee, "example.heat") == 0 && last().indexOf("drained 5 example.heat") >= 0, "heat " + NR.value(dee, "example.heat") + " / " + last());

	// (the root casts are auto, which cannot fizzle: the dice depend on the tests run before this one)
	// two ripostes: a block makes a riposte, whose hit is blocked and makes the other's; then each has run once in the root
	var xan = actor("Riposte Xan"), yul = actor("Riposte Yul");
	var xs = composer(xan); composer(yul); blocker(xan); blocker(yul);
	grant(xan, "example.riposte"); grant(yul, "example.riposte");
	var castOk = xs.invoke(xan, CM.core.CMParms.parse("CAST example.ember"), yul, true, 0);
	var chain = last() + " / cast " + castOk;
	check("mutual-stops", chain.indexOf("Riposte Yul / example.riposte (block): used example.ember on Riposte Xan") >= 0
		&& chain.indexOf("Riposte Xan / example.riposte (block): used example.ember on Riposte Yul") >= 0
		&& chain.indexOf("already ran in this root") >= 0, chain + " / yul " + s(CP.trace(yul, 1)).substring(0, 900));
	xan.makePeace(true); yul.makePeace(true);
	// the depth limit: a reaction to a reaction is refused at depth 1 (the campaign's setting, as WORLD MODULES CONFIGURE sets it)
	OM.configure("abilities.triggers", "max_depth", "1");
	var xan2 = actor("Counter Xan"), yul2 = actor("Counter Yul");
	var xs2 = composer(xan2); composer(yul2); blocker(xan2); blocker(yul2); grant(xan2, "fix.counter"); grant(yul2, "fix.counter");
	var ok2 = xs2.invoke(xan2, CM.core.CMParms.parse("CAST example.ember"), yul2, true, 0);
	check("depth", last().indexOf("Counter Yul / fix.counter (block): used example.ember on Counter Xan") >= 0 && last().indexOf("refused: deeper than 1") >= 0, last() + " / cast " + ok2 + " granted " + AT.grantedIDs(yul2) + " trace " + s(CP.trace(xan2, 1)).substring(0, 400));
	xan2.makePeace(true); yul2.makePeace(true);
	// the action budget: one action in the root
	OM.configure("abilities.triggers", "max_depth", "3"); OM.configure("abilities.triggers", "action_budget", "1");
	var xan3 = actor("Budget Xan"), yul3 = actor("Budget Yul");
	var xs3 = composer(xan3); composer(yul3); blocker(xan3); blocker(yul3); grant(xan3, "fix.counter"); grant(yul3, "fix.counter");
	xs3.invoke(xan3, CM.core.CMParms.parse("CAST example.ember"), yul3, true, 0);
	check("budget", last().indexOf("Budget Yul / fix.counter (block): used example.ember on Budget Xan") >= 0 && last().indexOf("refused: the root's action budget is spent") >= 0, last());
	xan3.makePeace(true); yul3.makePeace(true);
	OM.configure("abilities.triggers", "action_budget", "8");

	// a cost: the trigger is refused without it, and pays it once the chance passes
	var cara = actor("Trigger Cara"); grant(cara, "example.second-wind"); NR.grant(cara, "example.focus"); NR.set(cara, "example.focus", 0);
	var c1 = actor("Cost Victim"); AT.died(c1, cara); AT.drain();
	check("cost-refused", last().indexOf("refused: cannot pay") >= 0, last());
	check("streams", CM.core.RandomStreams.counter("triggers") == 0, "a refused trigger drew from its stream");

	// inspection: TRIGGERS and the checks
	check("describe", s(AT.describe(ann)).indexOf("Cinder harvest (example.harvest): on kill, gains 1 example.cinder") >= 0, s(AT.describe(ann)));
	write(new java.io.File(folder, "triggers/fix.broken.v1.json"), { schema: 1, id: "fix.broken", version: 1, on: "block", cost: { "fix.nopool": 3 }, action: { type: "cast", skill: "fix.noskill" } });
	var found = s(java.lang.String.join(" | ", AT.FAMILY_ENTRY.check()));
	check("check-refs", found.indexOf("triggers/fix.broken.v1.json: /action/skill: no composed skill fix.noskill") >= 0
		&& found.indexOf("triggers/fix.broken.v1.json: /cost/fix.nopool: no pool fix.nopool") >= 0, found);
	write(new java.io.File(folder, "triggers/fix.broken.v1.json"), { schema: 1, id: "fix.broken", version: 1, on: "kill", action: { type: "cast", skill: "example.ember", target: "other" } });
	found = s(java.lang.String.join(" | ", AT.FAMILY_ENTRY.check()));
	check("check-kill-target", found.indexOf("fix.broken.v1.json: /action/target: a kill's other is the dead") >= 0, found);
	check("stats", MS.value("triggers", "example.harvest", "actions") >= 2 && MS.value("triggers", "example.harvest", "duplicate events suppressed") >= 1, s(MS.report()));
} finally {
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	if (catalogued != null) { var c = CMLib.catalog().getCatalogMob(catalogued); if (c != null) CMLib.catalog().delCatalog(c); }
	room.destroy();
	SE.setTurns(turnsBefore);
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); CP.reset(); NR.reset(); AT.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
