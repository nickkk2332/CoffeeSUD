// A15 named resources: pools and charges, all-or-nothing costs, clamping, lazy change on the chosen clock (the same
// after a save and restore), maximum bonuses and a lower new version, reservations, composed costs and gains, checks.
FIXFILE = "a15-resources";
var NR = CM.core.NamedResources, AC = CM.core.AbilityComposition, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, SE = CM.core.ScriptEvents;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-a15-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
var turnsBefore = SE.turns();
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
function costs(map) { var m = new java.util.TreeMap(); for (var k in map) m.put(k, java.lang.Long.valueOf(map[k])); return m; }
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); m.baseState().setMana(1000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
try {
	var example = new java.io.File("../docs/creator/examples");
	var mods = new java.io.File(folder, "modules"); mods.mkdirs();
	var pools = new java.io.File(folder, "pools"); pools.mkdirs();
	var defs = new java.io.File(folder, "compositions"); defs.mkdirs();
	write(new java.io.File(mods, "abilities.composition.json"), JSON.parse(read(new java.io.File(example, "ability-composition/modules/abilities.composition.json"))));
	var manifest = JSON.parse(read(new java.io.File(example, "named-resources/modules/abilities.resources.json")));
	write(new java.io.File(mods, "abilities.resources.json"), manifest);
	var ex = new java.io.File(example, "named-resources/pools").listFiles();
	for (var i = 0; i < ex.length; i++) write(new java.io.File(pools, s(ex[i].getName())), JSON.parse(read(ex[i])));
	// a pool on the turns clock, for a clock the test can move
	write(new java.io.File(pools, "fix.strain.v1.json"), { schema: 1, id: "fix.strain", version: 1, title: "Strain", max: 30, start: 0, change: { amount: -2, every: 1, clock: "turns" } });
	var ember = JSON.parse(read(new java.io.File(example, "ability-composition/compositions/example.ember.v1.json")));
	ember.resources = { cost: { "example.focus": 20 }, gain: { "example.heat": 15, "example.cinder": 1 } };
	ember.stats.cooldown = 0;
	write(new java.io.File(defs, "example.ember.v1.json"), ember);

	// the module's contract is checked
	var bad = JSON.parse(JSON.stringify(manifest)); delete bad.state.pools;
	write(new java.io.File(mods, "abilities.resources.json"), bad);
	check("contract", s(MD.load(folder, "abilities.resources", "").problems()).indexOf("requires string state.pools") >= 0, s(MD.load(folder, "abilities.resources", "").problems()));
	write(new java.io.File(mods, "abilities.resources.json"), manifest);

	var plan = MD.load(folder, "abilities.composition,abilities.resources", "");
	check("manifest-valid", plan.errors.isEmpty(), s(plan.problems()));
	currentField.set(null, OM.prepare(plan, null, false)); WorldFiles.configure(folder); AC.reset(); NR.reset();
	check("enabled", NR.enabled() && s(OM.snapshot()).indexOf('"requires_handler":"abilities.resources"') >= 0, "the native handler is not recorded");
	var lib = NR.definitions();
	check("definitions", lib.errors.isEmpty() && lib.entries.size() == 4, s(lib.report()));

	var alice = actor("Pooler Alice"), bob = actor("Pooler Bob");
	// a pool starts at its start value; gains clamp at the maximum
	check("grant", NR.grant(alice, "example.focus") && NR.value(alice, "example.focus") == 50 && !NR.grant(alice, "example.focus"), "focus " + NR.value(alice, "example.focus"));
	check("gain-clamps", NR.gain(alice, "example.heat", 70) == 70 && NR.gain(alice, "example.heat", 70) == 30 && NR.value(alice, "example.heat") == 100, "heat " + NR.value(alice, "example.heat"));
	check("isolated", NR.held(bob, "example.heat") == null, "bob shares alice's heat");
	// costs are all checked first: one short, none spent
	throws("all-or-nothing", function () { NR.spend(alice, costs({ "example.focus": 10, "example.heat": 500 }), "test"); }, "you need 500 Heat");
	check("nothing-spent", NR.value(alice, "example.focus") == 50 && NR.value(alice, "example.heat") == 100, "a refused cost spent");
	NR.spend(alice, costs({ "example.focus": 10, "example.heat": 40 }), "test");
	check("spent", NR.value(alice, "example.focus") == 40 && NR.value(alice, "example.heat") == 60, "focus " + NR.value(alice, "example.focus") + " heat " + NR.value(alice, "example.heat"));
	throws("no-pool", function () { NR.spend(bob, costs({ "example.focus": 1 }), "test"); }, "you have no Focus");
	// reservations: what is reserved cannot be spent
	NR.reserve(alice, "example.focus", 30);
	throws("reserved", function () { NR.spend(alice, costs({ "example.focus": 20 }), "test"); }, "you have 10");
	throws("not-reservable", function () { NR.reserve(alice, "example.heat", 1); }, "cannot be reserved");
	NR.release(alice, "example.focus", 30);
	check("released", NR.held(alice, "example.focus").available() == 40, "after release " + NR.held(alice, "example.focus").available());

	// the change is settled lazily from the chosen clock; reading writes nothing; a save keeps the same value
	SE.setTurns(100);
	NR.gain(alice, "fix.strain", 20);
	var snap = s(OM.snapshot());
	SE.setTurns(104);
	check("lazy-change", NR.value(alice, "fix.strain") == 12, "strain " + NR.value(alice, "fix.strain") + " after 4 turns");
	check("read-only", s(OM.snapshot()) == snap, "reading the pool changed the saved state");
	NR.describe(alice);
	check("describe-read-only", s(OM.snapshot()) == snap && s(NR.describe(alice)).indexOf("Strain: 12 of 30 (loses 2 every turn)") >= 0, s(NR.describe(alice)));
	var restored = OM.prepare(plan, snap, true); currentField.set(null, restored);
	check("restored-same", NR.value(alice, "fix.strain") == 12, "after restore " + NR.value(alice, "fix.strain"));
	SE.setTurns(200);
	check("floor", NR.value(alice, "fix.strain") == 0, "strain fell below 0: " + NR.value(alice, "fix.strain"));

	// a bonus to the maximum, and its removal clamping the value
	var amulet = CMClass.getItem("GenItem"); amulet.setName("a fixa15 amulet");
	var pm = CMClass.getAbility("Prop_ResourceMax"); pm.setMiscText("example.heat=+50"); amulet.addNonUninvokableEffect(pm);
	alice.addItem(amulet); amulet.wearAt(CM.Items.interfaces.Wearable.WORN_NECK);
	NR.set(alice, "example.heat", 140);
	check("max-bonus", NR.value(alice, "example.heat") == 140 && NR.held(alice, "example.heat").max == 150, "heat " + NR.value(alice, "example.heat"));
	amulet.unWear();
	check("max-clamps", NR.value(alice, "example.heat") == 100, "heat " + NR.value(alice, "example.heat") + " after the amulet came off");
	// a newer version with a lower maximum: the character's pool moves to it and clamps
	write(new java.io.File(pools, "example.heat.v2.json"), { schema: 1, id: "example.heat", version: 2, title: "Heat", max: 80, start: 0 });
	NR.reset();
	check("new-version-clamps", NR.value(alice, "example.heat") == 80 && NR.held(alice, "example.heat").def.version == 2, "heat " + NR.value(alice, "example.heat"));
	// a version edited in place is refused (once the character's pool has been written at it)
	NR.set(alice, "example.heat", 70);
	write(new java.io.File(pools, "example.heat.v2.json"), { schema: 1, id: "example.heat", version: 2, title: "Heat", max: 90, start: 0 });
	NR.reset();
	throws("edited-refused", function () { NR.value(alice, "example.heat"); }, "changed without a new version");
	write(new java.io.File(pools, "example.heat.v2.json"), { schema: 1, id: "example.heat", version: 2, title: "Heat", max: 80, start: 0 });
	NR.reset();

	// a composed skill: its cost is checked before the cast, paid with it; its gains come after
	var a = CMClass.getAbility("Skill_Compose"); a.setProficiency(100); bob.addAbility(a); AC.learn(bob, "example.ember");
	var target = actor("Pool Target"); target.baseState().setHitPoints(5000); target.recoverMaxState(); target.resetToMaxState();
	NR.grant(bob, "example.focus"); NR.set(bob, "example.focus", 10);
	var mana = bob.curState().getMana();
	check("cost-refused", !a.invoke(bob, CM.core.CMParms.parse("CAST example.ember"), target, false, 0) && bob.curState().getMana() == mana && NR.value(bob, "example.focus") == 10,
		"focus " + NR.value(bob, "example.focus") + " mana " + bob.curState().getMana() + "/" + mana);
	NR.set(bob, "example.focus", 30);
	check("cast", a.invoke(bob, CM.core.CMParms.parse("CAST example.ember"), target, false, 0), "the paid cast failed");
	check("paid-and-gained", NR.value(bob, "example.focus") == 10 && NR.value(bob, "example.heat") == 15 && NR.value(bob, "example.cinder") == 1,
		"focus " + NR.value(bob, "example.focus") + " heat " + NR.value(bob, "example.heat") + " cinder " + NR.value(bob, "example.cinder"));
	check("sheet", s(AC.show(bob, "example.ember")).indexOf("Resource costs") >= 0, s(AC.show(bob, "example.ember")));
	target.makePeace(true); bob.makePeace(true);

	// WORLD CHECK ONLY=POOLS: a missing pool, and a turns clock in a module that allows real time
	ember.resources.cost["example.missing"] = 5;
	write(new java.io.File(defs, "example.ember.v1.json"), ember);
	var found = s(java.lang.String.join("|", NR.FAMILY_ENTRY.check()));
	check("check-missing-pool", found.indexOf("compositions/example.ember.v1.json: /resources: no pool example.missing") >= 0, found);
	check("check-turns-clock", found.indexOf("pools/fix.strain.v1.json: /change/clock: turns do not pass in real time") >= 0, found);

	// the module off: the pools keep their records, and cannot be used
	var offPlan = MD.load(folder, "abilities.composition", ""); currentField.set(null, OM.prepare(offPlan, s(OM.snapshot()), true));
	throws("disabled", function () { NR.value(alice, "example.focus"); }, "not active");
	check("suspended-kept", s(OM.snapshot()).indexOf("example.focus") >= 0, "the suspended pools were dropped");
} finally {
	for (var i = 0; i < actors.length; i++) actors[i].destroy(); room.destroy();
	SE.setTurns(turnsBefore);
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); NR.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
