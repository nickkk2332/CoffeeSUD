// Q12 observability: a stress scenario (hundreds of reactions, offers, earnings and crafts' worth of work) stays within
// the declared work limits; duplicate events are suppressed and counted; when a limit is tightened the report names the
// offending source; the report carries families, sources and counts, never a character's name or a room.
FIXFILE = "q12-stress";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
var AT = CM.core.AbilityTriggers, NR = CM.core.NamedResources, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles;
var RE = CM.core.RewardEscrow, PT = CM.core.ProgressionTracks, MS = CM.core.ModuleStats, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-q12-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function write(f, obj) { f.getParentFile().mkdirs(); Files.write(f.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
var room = scratchRoom(), actors = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); m.baseState().setHitPoints(5000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
function grant(m, ids) { var p = m.fetchEffect("Prop_Triggers"); if (p != null) { p.setMiscText(s(p.text()) + "," + ids); return; } p = CMClass.getAbility("Prop_Triggers"); p.setMiscText("TRIGGERS=" + ids); m.addNonUninvokableEffect(p); }
try {
	var ex = new java.io.File("../docs/creator/examples");
	var packs = ["ability-composition", "combat-profiles", "named-resources", "causal-triggers", "reward-escrow", "progression-tracks"];
	for (var i = 0; i < packs.length; i++) copy(new java.io.File(ex, packs[i]), folder);
	new java.io.File(folder, "README.md").delete();
	write(new java.io.File(folder, "triggers/fix.stress.v1.json"), { schema: 1, id: "fix.stress", version: 1, on: "kill", action: { type: "gain", pool: "example.heat", amount: 1 } });
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "abilities.composition,combat.profiles,abilities.resources,abilities.triggers,rewards.escrow,progression.tracks", "");
	currentField.set(null, OM.prepare(plan, null, false)); AC.reset(); CP.reset(); NR.reset(); AT.reset(); RE.reset(); PT.reset();
	check("modules", plan.errors.isEmpty(), s(plan.problems()));
	MS.reset();

	// the stress: 200 kills, each a causal root with its reactions; each death seen twice
	var ann = actor("Stress Ann"); grant(ann, "fix.stress");
	for (var k = 0; k < 200; k++) {
		var foe = actor("Stress Foe " + k);
		CMLib.combat().postDeath(ann, foe, null);
		AT.died(foe, ann); PT.died(foe, ann); // the same death seen again
		AT.drain(); foe.destroy();
	}
	// 300 reward offers over ten characters, each key offered three times; the ledgers fill and refuse
	var refused = 0, crew = [];
	for (var c = 0; c < 3; c++) crew.push(actor("Stress Crew " + c));
	for (var o = 0; o < 300; o++) { try { RE.offer(crew[Math.floor(o / 3) % 3], "example.trial", "example.room-one", "k" + Math.floor(o / 3)); } catch (e) { refused++; } }
	// 500 earnings with repeated keys, below the track's cap
	var bo = actor("Stress Bo");
	for (var t = 0; t < 500; t++) PT.earn(PT.of(bo), "example.emberwright", 1, "stress:" + (t % 10)); // ten keys: a track remembers its last 24
	var report = s(MS.report());
	check("within-limits", report.indexOf("over limit") < 0 && report.indexOf("triggers | actions in one root | fix.stress:") >= 0
		&& /triggers \| depth \| fix\.stress: (\d+) of 8/.test(report) && /rewards \| ledger entries \| example\.trial: (\d+) of 24/.test(report), report);
	check("work-counted", MS.value("triggers", "fix.stress", "actions") == 200 && MS.value("triggers", "fix.stress", "duplicate events suppressed") >= 200
		&& MS.value("rewards", "example.trial", "duplicate events suppressed") > 0 && MS.value("tracks", "example.emberwright", "duplicate sources suppressed") == 690, report);
	check("refusals-bounded", refused > 0 && RE.count(crew[0], "provisional") <= 24, "refused " + refused + " provisional " + RE.count(crew[0], "provisional"));
	// a tightened limit: the report names the source that passed it
	MS.limit("rewards", "ledger entries", 3);
	try { RE.offer(actor("Stress Late"), "example.trial", "example.room-one", "late-1"); RE.offer(actors[actors.length - 1], "example.trial", "example.room-two", "late-2");
	      RE.offer(actors[actors.length - 1], "example.trial", "example.room-three", "late-3"); RE.offer(actors[actors.length - 1], "example.trial", "example.room-one", "late-4"); } catch (e) { }
	report = s(MS.report());
	check("offender-named", report.indexOf("rewards | example.trial | ledger entries over limit: ") >= 0 && /rewards \| ledger entries \| example\.trial: (\d+) of 3/.test(report), report);
	MS.limit("rewards", "ledger entries", 24);
	// no private data: the report has families, sources and counts only
	check("no-private-data", report.indexOf("Stress Ann") < 0 && report.indexOf("Stress Crew") < 0 && report.indexOf("Stress Late") < 0 && report.indexOf("#") < 0, report);
} finally {
	CMath.setRandState(diceAtStart.longValue()); MS.reset(); // its counts do not leak into later fixtures
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset(); CP.reset(); NR.reset(); AT.reset(); RE.reset(); PT.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
