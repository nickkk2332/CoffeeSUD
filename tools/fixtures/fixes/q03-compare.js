// Q03 build comparison: COMPOSE COMPARE puts two composed skills side by side against a target with the combat
// profile's exact chances; the expected damage is the outcomes weighted by their exact probabilities; nothing is
// rolled, paid or cooled down; the target's own stats show only in the creator's view.
FIXFILE = "q03-compare";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
var BC = CM.core.BuildCompare, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles, CR = CM.core.CombatRules;
var MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, RS = CM.core.RandomStreams;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-q03-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function write(f, obj) { f.getParentFile().mkdirs(); Files.write(f.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); m.baseState().setMana(500); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
function stats(m, json) { var p = CMClass.getAbility("Prop_CombatStats"); p.setMiscText(JSON.stringify(json)); m.addNonUninvokableEffect(p); }
function sum(map) { var t = 0, it = map.values().iterator(); while (it.hasNext()) t += Number(it.next()); return t; }
try {
	var ex = new java.io.File("../docs/creator/examples");
	copy(new java.io.File(ex, "ability-composition"), folder);
	copy(new java.io.File(ex, "combat-profiles"), folder);
	new java.io.File(folder, "README.md").delete();
	write(new java.io.File(folder, "compositions/fix.strike.v1.json"), { schema: 1, kind: "skill", id: "fix.strike", version: 1, name: "Heavy strike",
		tags: ["attack", "melee"], damage: { physical: 20 }, stats: { mana: 2, actions: 1, cooldown: 0, range: 1 } });
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "abilities.composition,combat.profiles", "");
	currentField.set(null, OM.prepare(plan, null, false)); AC.reset(); CP.reset();
	check("modules", plan.errors.isEmpty() && OM.enabled("combat.profiles"), s(plan.problems()));

	var ann = actor("Compare Ann"), foe = actor("a fixq03 target");
	var A = CMClass.getAbility("Skill_Compose"); A.setProficiency(100); ann.addAbility(A);
	AC.learn(ann, "example.ember"); AC.learn(ann, "fix.strike"); AC.link(ann, "example.ember", "example.power");
	stats(foe, { schema: 1, evasion: 100, block: 0, spell_block: 0, resistance: { fire: 25 } });
	stats(ann, { schema: 1, crit_chance: 10 });

	var dice = CMath.getRandState(), snap = s(OM.snapshot()), mana = ann.curState().getMana(), loot = RS.counter("loot");
	var said = s(BC.compare(ann, "example.ember", "fix.strike", foe));
	check("side-by-side", said.indexOf("Build comparison against a fixq03 target") >= 0 && said.indexOf("example.ember (Ember bolt): a spell") >= 0
		&& said.indexOf("fix.strike (Heavy strike): a weapon skill") >= 0 && said.indexOf("hit no roll (a spell)") >= 0
		&& said.indexOf("expected damage per cast") >= 0 && said.indexOf("more expected damage per cast") >= 0, said);
	// the chances are exact: the outcomes weighted by their probabilities
	var p = CP.current(), target = CP.stats(foe, p).stats, strike = BC.outcome(ann, AC.compiled(ann, "fix.strike"), target, p);
	var ph = CR.probability(strike.hit), pc = CR.probability(strike.critical);
	var expect = ph * ((1 - pc) * sum(strike.lands) + pc * sum(strike.crits));
	check("exact", strike.hit > 5 && strike.hit < 95 && Math.abs(strike.expected - expect) < 1e-9 && strike.block == 0 && sum(strike.crits) > sum(strike.lands)
		&& said.indexOf("hit " + Math.round(ph * 100) + "%") >= 0, "hit " + strike.hit + " crit " + strike.critical + " expected " + strike.expected + " vs " + expect);
	var ember = BC.outcome(ann, AC.compiled(ann, "example.ember"), target, p);
	check("resisted-spell", ember.spell && CR.probability(ember.hit) == 1 && sum(ember.lands) > 0 && sum(ember.lands) < 18, "ember lands " + sum(ember.lands));
	// nothing rolled, paid, cooled down or saved
	check("changes-nothing", dice.equals(CMath.getRandState()) && s(OM.snapshot()) == snap && ann.curState().getMana() == mana && RS.counter("loot") == loot
		&& AC.remaining(ann, "example.ember") == 0, "something moved");
	// against the profile's defaults; and the creator's view alone shows the target's stats
	var plain = s(BC.compare(ann, "example.ember", "fix.strike", null));
	check("defaults", plain.indexOf("against a target with the profile's defaults") >= 0, plain);
	check("views", (said.indexOf("Creator view: the target's stats:") >= 0) == BC.creatorView() && (said.indexOf("resistance.fire=25") >= 0) == BC.creatorView(), said);
	// the command takes no actions, and answers through the player's COMPOSE
	check("free", A.checkedCastingCost(ann, CM.core.CMParms.parse("COMPOSE COMPARE example.ember fix.strike")) == 0, "COMPARE costs actions");
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); CP.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
