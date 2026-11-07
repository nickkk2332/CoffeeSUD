// A12 ailment buildup and A13 defense pools, for composed skills under a combat profile: landed hits fill a meter that
// drains each tick; at the threshold its status holds (its effects work), then the target is immune for a while; a hit
// can consume the status (and a modifier reads status:<id>); a cast can cleanse the caster; the guard pool is spent
// before hit points and its recharge waits after a hit; life leech comes back over time, capped a tick; all of it is
// in effects' texts (saved); WORLD CHECK warns of an ailment no profile declares.
FIXFILE = "a12-ailments";
var NR = CM.core.NamedResources, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles, CA = CM.Abilities.Misc.CompositionAilment;
var MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, SE = CM.core.ScriptEvents;
var Files = java.nio.file.Files, JString = java.lang.String, TM = Tickable.TICKID_MOB, DEX = CM.Common.interfaces.CharStats.STAT_DEXTERITY;
var folder = Files.createTempDirectory("sud-a12-").toFile();
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
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); m.baseState().setHitPoints(5000); m.baseState().setMana(5000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
function composer(m, ids) { var a = CMClass.getAbility("Skill_Compose"); a.setProficiency(100); m.addAbility(a); ids.forEach(function (id) { AC.learn(m, id); }); return a; }
// the 95/5 rule leaves a composed cast a 5% fizzle: a fizzle is cast again, so it cannot hide what the test looks for
function cast(a, who, id, at) {
	var done = false;
	for (var i = 0; i < 6; i++) {
		done = a.invoke(who, CM.core.CMParms.parse("CAST " + id), at, false, 0);
		who.makePeace(true); at.makePeace(true);
		if (s(CP.trace(who, 1)).indexOf("proficiency fizzle") < 0) break;
	}
	return done;
}
function ail(m) { return m.fetchEffect("CompositionAilment"); }
try {
	var ex = new java.io.File("../docs/creator/examples");
	var mods = dir("modules");
	copyDir(new java.io.File(ex, "ability-composition/modules"), mods);
	copyDir(new java.io.File(ex, "named-resources/modules"), mods);
	var comps = dir("compositions"), pools = dir("pools");
	write(new java.io.File(pools, "fix.ward.v1.json"), { schema: 1, id: "fix.ward", version: 1, title: "Ward", max: 50, start: 50, change: { amount: 10, every: 1, clock: "turns" } });
	var ember = JSON.parse(read(new java.io.File(ex, "ability-composition/compositions/example.ember.v1.json")));
	ember.stats.cooldown = 0; ember.stats.mana = 0; ember.ailments = { build: { chill: 60 } }; ember.leech = 50;
	write(new java.io.File(comps, "example.ember.v1.json"), ember);
	var shatter = JSON.parse(JSON.stringify(ember)); shatter.id = "fix.shatter"; shatter.name = "Shatter"; delete shatter.leech;
	shatter.ailments = { consume: ["chill"] }; shatter.modifiers = [{ stat: "damage", op: "more", value: 100, condition: "status:chill" }];
	write(new java.io.File(comps, "fix.shatter.v1.json"), shatter);
	var shake = JSON.parse(JSON.stringify(ember)); shake.id = "fix.shake"; shake.name = "Shake off"; delete shake.leech; shake.ailments = { cleanse: ["chill"] };
	write(new java.io.File(comps, "fix.shake.v1.json"), shake);
	var profile = { schema: 1, id: "fix.ail", version: 1, title: "Ailments", conversion: "final", hit: { minimum: 100, maximum: 100 },
		ailments: { chill: { title: "Chilled", threshold: 100, decay: 10, status_ticks: 2, immune_ticks: 3, effects: "Prop_Adjuster;DEX-5" } },
		guard: { pool: "fix.ward", recharge_delay: 3 }, leech: { cap_per_tick: 5 } };
	write(new java.io.File(dir("combat"), "fix.ail.v1.json"), profile);
	var cpm = JSON.parse(read(new java.io.File(ex, "combat-profiles/modules/combat.profiles.json")));
	cpm.settings.profile["default"] = JSON.stringify({ hash: s(CM.core.ItemModifierRules.hash(MD.parse(JSON.stringify(profile)))), id: "fix.ail", version: 1 });
	write(new java.io.File(mods, "combat.profiles.json"), cpm);
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "abilities.composition,combat.profiles,abilities.resources", "");
	currentField.set(null, OM.prepare(plan, null, false)); AC.reset(); CP.reset(); NR.reset();
	check("profile-on", plan.errors.isEmpty() && OM.enabled("combat.profiles") && s(CP.list()).indexOf("ailment Chilled (chill): at 100, losing 10 a tick") >= 0, s(plan.problems()) + " / " + s(CP.list()));

	var ann = actor("Ailing Ann"), bo = actor("Ailing Bo");
	var a = composer(ann, ["example.ember", "fix.shatter"]);
	var dex0 = bo.charStats().getStat(DEX);
	cast(a, ann, "example.ember", bo);
	var tr0 = s(CP.trace(ann, 1)) + " ||| " + s(CP.trace(ann, 2)) + " ||| build " + AC.compiled(ann, "example.ember").buildup();
	check("buildup", ail(bo) != null && ail(bo).meter("chill") == 60, "meter " + (ail(bo) == null ? null : ail(bo).meter("chill")) + " / " + tr0);
	if (ail(bo) == null) throw "no ailment effect";
	ail(bo).tick(bo, TM);
	check("decay", ail(bo).meter("chill") == 50, "meter " + ail(bo).meter("chill"));
	cast(a, ann, "example.ember", bo); bo.recoverCharStats();
	check("status", ail(bo).hasStatus("chill") && ail(bo).meter("chill") == 0 && bo.charStats().getStat(DEX) == dex0 - 5,
		"status " + ail(bo).hasStatus("chill") + " dex " + bo.charStats().getStat(DEX) + "/" + dex0);
	cast(a, ann, "example.ember", bo);
	check("no-buildup-while-on", ail(bo).meter("chill") == 0 && s(CP.trace(ann, 1)).indexOf("already Chilled") >= 0, s(CP.trace(ann, 1)).substring(0, 600));
	// the state is the effect's text, as a save keeps it
	var copy = CMClass.getAbility("CompositionAilment"); copy.setMiscText(ail(bo).text());
	check("saved", copy.hasStatus("chill") && s(copy.accountForYourself()).indexOf("Chilled") >= 0, s(ail(bo).text()) + " -> " + s(copy.text()) + " / " + s(copy.accountForYourself()));
	// a hit that reads the status, and consumes it; then the immunity window
	cast(a, ann, "fix.shatter", bo); bo.recoverCharStats();
	var tr = s(CP.trace(ann, 1));
	check("consumed", !ail(bo).hasStatus("chill") && tr.indexOf("status:chill") >= 0 && tr.indexOf("Chilled consumed") >= 0 && bo.charStats().getStat(DEX) == dex0, tr.substring(0, 800));
	cast(a, ann, "example.ember", bo);
	check("immune", ail(bo).meter("chill") == 0 && s(CP.trace(ann, 1)).indexOf("immune for 3 more ticks") >= 0, s(CP.trace(ann, 1)).substring(0, 600));
	ail(bo).tick(bo, TM); ail(bo).tick(bo, TM); ail(bo).tick(bo, TM);
	cast(a, ann, "example.ember", bo);
	check("immunity-over", ail(bo).meter("chill") == 60, "meter " + ail(bo).meter("chill"));
	// cleansing: a cast that sheds the caster's ailment
	var b = composer(bo, ["fix.shake"]);
	cast(b, bo, "fix.shake", ann);
	check("cleansed", ail(bo) == null || ail(bo).meter("chill") == 0, "still " + (ail(bo) == null ? null : ail(bo).text()));

	// leech: half of the hit points removed come back, at most 5 a tick
	ann.curState().setHitPoints(100);
	cast(a, ann, "example.ember", bo);
	var L = ann.fetchEffect("CompositionLeech");
	check("leech-queued", L != null && L.pending() > 0 && ann.curState().getHitPoints() == 100, "leech " + (L == null ? null : L.text()) + " / " + s(CP.trace(ann, 1)).substring(0, 600));
	var pend = L.pending(); L.tick(ann, TM);
	check("leech-capped", ann.curState().getHitPoints() == 100 + Math.min(5, pend) && L.pending() == pend - Math.min(5, pend), "hp " + ann.curState().getHitPoints() + " pending " + L.pending());

	// the guard pool: spent before hit points, and its recharge waits 3 turns after a hit
	SE.setTurns(1000);
	var cara = actor("Guarded Cara"); NR.grant(cara, "fix.ward");
	var hp0 = cara.curState().getHitPoints();
	cast(a, ann, "example.ember", cara);
	var took = 50 - NR.value(cara, "fix.ward");
	check("guard", took > 0 && cara.curState().getHitPoints() == hp0 && s(CP.trace(ann, 1)).indexOf("guard pool Ward took") >= 0, "ward took " + took + " hp " + cara.curState().getHitPoints() + "/" + hp0 + " / " + s(CP.trace(ann, 1)).substring(0, 600));
	var after = NR.value(cara, "fix.ward");
	SE.setTurns(1002);
	check("recharge-waits", NR.value(cara, "fix.ward") == after, "ward " + NR.value(cara, "fix.ward") + " after 2 turns, was " + after);
	SE.setTurns(1004);
	check("recharge-resumes", NR.value(cara, "fix.ward") == Math.min(50, after + 10), "ward " + NR.value(cara, "fix.ward") + " after 4 turns, was " + after);

	// WORLD CHECK: an ailment no profile declares
	var venom = JSON.parse(JSON.stringify(shake)); venom.id = "fix.venom"; venom.ailments = { build: { venom: 10 } };
	write(new java.io.File(comps, "fix.venom.v1.json"), venom);
	var found = s(CP.checkAilments(CP.check()));
	check("check", found.indexOf("fix.venom.v1.json: /ailments: no combat profile declares the ailment venom") >= 0, found);
} finally {
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy(); room.destroy();
	SE.setTurns(turnsBefore);
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); CP.reset(); NR.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
