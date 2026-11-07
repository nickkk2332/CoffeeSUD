// A20 combos: a pool that a composed skill fills only when its cast lands (resources.gain_on "hit"), that a cast which
// hurts no one resets ("reset": ["miss"]), that the end of a fight resets ("combat_end"), and that decays on its own turn
// clock between steps (its declared change). Bad gain_on and reset values are refused when the definitions load.
FIXFILE = "a20-combos";
var NR = CM.core.NamedResources, AC = CM.core.AbilityComposition, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, SE = CM.core.ScriptEvents;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-a20-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
var turnsBefore = SE.turns();
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); m.baseState().setMana(1000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
try {
	var example = new java.io.File("../docs/creator/examples");
	var mods = new java.io.File(folder, "modules"); mods.mkdirs();
	var pools = new java.io.File(folder, "pools"); pools.mkdirs();
	var defs = new java.io.File(folder, "compositions"); defs.mkdirs();
	write(new java.io.File(mods, "abilities.composition.json"), JSON.parse(read(new java.io.File(example, "ability-composition/modules/abilities.composition.json"))));
	write(new java.io.File(mods, "abilities.resources.json"), JSON.parse(read(new java.io.File(example, "named-resources/modules/abilities.resources.json"))));
	var ex = new java.io.File(example, "named-resources/pools").listFiles();
	for (var i = 0; i < ex.length; i++) write(new java.io.File(pools, s(ex[i].getName())), JSON.parse(read(ex[i])));
	// the combo: up to 5, back to 0 on a miss or when the fight ends, and one less every 3 turns between steps
	write(new java.io.File(pools, "fix.combo.v1.json"), { schema: 1, id: "fix.combo", version: 1, title: "Combo", max: 5, start: 0,
		change: { amount: -1, every: 3, clock: "turns" }, reset: ["miss", "combat_end"] });
	var ember = JSON.parse(read(new java.io.File(example, "ability-composition/compositions/example.ember.v1.json")));
	ember.resources = { gain: { "fix.combo": 1 }, gain_on: "hit" };
	ember.stats.cooldown = 0; ember.stats.mana = 0;
	write(new java.io.File(defs, "example.ember.v1.json"), ember);

	var plan = MD.load(folder, "abilities.composition,abilities.resources", "");
	check("manifest-valid", plan.errors.isEmpty(), s(plan.problems()));
	currentField.set(null, OM.prepare(plan, null, false)); WorldFiles.configure(folder); AC.reset(); NR.reset();
	check("definitions", NR.definitions().errors.isEmpty() && s(NR.FAMILY_ENTRY.list()).indexOf("back to 0 on a miss and when a fight ends") >= 0, s(NR.FAMILY_ENTRY.list()));

	SE.setTurns(1000);
	var bob = actor("Combo Bob"), target = actor("Combo Target");
	target.baseState().setHitPoints(5000); target.recoverMaxState(); target.resetToMaxState();
	var a = CMClass.getAbility("Skill_Compose"); a.setProficiency(100); bob.addAbility(a); AC.learn(bob, "example.ember");
	function cast() { return a.invoke(bob, CM.core.CMParms.parse("CAST example.ember"), target, false, 0); }
	check("sheet", s(AC.show(bob, "example.ember")).indexOf("once the cast lands") >= 0, s(AC.show(bob, "example.ember")));
	check("landed-1", cast() && NR.value(bob, "fix.combo") == 1, "combo " + NR.value(bob, "fix.combo"));
	check("landed-2", cast() && NR.value(bob, "fix.combo") == 2, "combo " + NR.value(bob, "fix.combo"));

	// a cast that hurts no one gains nothing, and resets the combo
	var nd = CMClass.getAbility("Prop_NoDamage"); bob.addNonUninvokableEffect(nd);
	cast();
	check("miss-resets", NR.value(bob, "fix.combo") == 0, "combo " + NR.value(bob, "fix.combo") + " after a cast that hurt no one");
	bob.delEffect(nd);

	// between steps, the combo decays on its own clock: one less every 3 turns
	cast(); cast(); cast();
	check("built", NR.value(bob, "fix.combo") == 3, "combo " + NR.value(bob, "fix.combo"));
	SE.setTurns(SE.turns() + 3);
	check("decays", NR.value(bob, "fix.combo") == 2, "combo " + NR.value(bob, "fix.combo") + " after 3 turns");
	check("described", s(NR.describe(bob)).indexOf("back to 0 on a miss and when a fight ends") >= 0, s(NR.describe(bob)));

	// the fight ends: the combo resets; a pool without combat_end keeps its value
	NR.gain(bob, "example.heat", 30);
	bob.setVictim(target); target.setVictim(bob);
	check("fighting", bob.getVictim() == target, "bob is not fighting the target: " + bob.getVictim());
	bob.makePeace(true); target.makePeace(true);
	check("combat-end-resets", NR.value(bob, "fix.combo") == 0 && NR.value(bob, "example.heat") == 30, "combo " + NR.value(bob, "fix.combo") + " heat " + NR.value(bob, "example.heat"));

	// a skill without gain_on gains on any cast, even one that hurts no one; the miss still resets the combo
	var spark = JSON.parse(JSON.stringify(ember)); spark.id = "fix.spark"; spark.name = "Spark"; spark.resources = { gain: { "example.cinder": 1 } };
	write(new java.io.File(defs, "fix.spark.v1.json"), spark);
	AC.reset(); AC.learn(bob, "fix.spark");
	cast(); cast();
	bob.addNonUninvokableEffect(nd);
	a.invoke(bob, CM.core.CMParms.parse("CAST fix.spark"), target, false, 0);
	check("gain-on-cast", NR.value(bob, "example.cinder") == 1 && NR.value(bob, "fix.combo") == 0, "cinder " + NR.value(bob, "example.cinder") + " combo " + NR.value(bob, "fix.combo"));
	bob.delEffect(nd); bob.makePeace(true); target.makePeace(true);

	// bad values are refused when the definitions load
	write(new java.io.File(pools, "fix.bad.v1.json"), { schema: 1, id: "fix.bad", version: 1, title: "Bad", max: 5, start: 0, reset: ["dawn"] });
	NR.reset();
	check("bad-reset", s(NR.definitions().errors).indexOf("must name only miss, combat_end, not dawn") >= 0, s(NR.definitions().errors));
	var badSkill = JSON.parse(JSON.stringify(ember)); badSkill.id = "fix.badskill"; badSkill.resources = { gain: { "fix.combo": 1 }, gain_on: "sometimes" };
	write(new java.io.File(defs, "fix.badskill.v1.json"), badSkill);
	AC.reset();
	var errs = s(CM.core.CompositionDefinitions.load(folder).errors);
	check("bad-gain-on", errs.indexOf("resources.gain_on must be \"cast\" or \"hit\"") >= 0, errs);
} finally {
	for (var i = 0; i < actors.length; i++) actors[i].destroy(); room.destroy();
	SE.setTurns(turnsBefore);
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); NR.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
