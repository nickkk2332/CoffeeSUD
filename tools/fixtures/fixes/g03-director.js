// G03 encounter director, Q08 budgets, Q07 warnings: a three-phase boss and a defense wave from one schema; each phase
// spawns once per run; a removed actor is not defeated; the saved state resumes; a retreat resets; a fallen keeper
// fails; rewards go through escrow once; the scale is chosen once; budgets refuse an encounter that cannot fit.
FIXFILE = "g03-director";
var ED = CM.core.EncounterDirectors, RE = CM.core.RewardEscrow, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, MS = CM.core.ModuleStats;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-g03-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function copyDir(from, to) { var fs = from.listFiles(); for (var i = 0; i < fs.length; i++) write(new java.io.File(to, s(fs[i].getName())), JSON.parse(read(fs[i]))); }
function dir(name) { var d = new java.io.File(folder, name); d.mkdirs(); return d; }
var rooms = [], actors = [], catalogued = [];
function newRoom() { var R = scratchRoom(); rooms.push(R); return R; }
function player(name, R) { var m = newMob("GenMob", R); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.baseState().setHitPoints(500); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
function npc(name, R) { var m = newMob("GenMob", R); m.setName(name); actors.push(m); return m; }
// a generic mob's hit points are rolled from its hit dice when it comes to life: 1d1+(hp-1) is a fixed roll
function catalogMob(name, hp) { var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setAbility((1 << 23) + (1 << 15) + (hp - 1)); m.recoverPhyStats(); m.text(); CMLib.catalog().addCatalog(m); catalogued.push(name); }
function director(R, id) { var B = CMClass.getBehavior("EncounterDirector"); B.setParms("ENCOUNTER=" + id); R.addBehavior(B); return B; }
function tick(B, R, n) { for (var i = 0; i < (n || 1); i++) B.tick(R, Tickable.TICKID_ROOM_BEHAVIOR); }
function find(R, name) { var out = []; for (var i = 0; i < R.numInhabitants(); i++) { var M = R.fetchInhabitant(i); if (s(M.Name()) == name) out.push(M); } return out; }
function hp(M, pct) { M.curState().setHitPoints(Math.floor(M.maxState().getHitPoints() * pct / 100)); }
function calm(R) { for (var i = 0; i < R.numInhabitants(); i++) R.fetchInhabitant(i).makePeace(true); }
var plan = null;
try {
	var ex = new java.io.File("../docs/creator/examples");
	var mods = dir("modules");
	copyDir(new java.io.File(ex, "reward-escrow/modules"), mods);
	copyDir(new java.io.File(ex, "encounter-director/modules"), mods);
	copyDir(new java.io.File(ex, "reward-escrow/rewards"), dir("rewards"));
	var encs = dir("directors");
	copyDir(new java.io.File(ex, "encounter-director/directors"), encs);
	catalogMob("the Cinder Warden", 400); catalogMob("a cinder imp", 50); catalogMob("a raider", 40); catalogMob("a raider captain", 80);
	// an encounter that cannot fit its budget is refused when it is read
	write(new java.io.File(encs, "fix.crowd.v1.json"), { schema: 1, id: "fix.crowd", version: 1, budget: { actors: 2 },
		phases: [{ id: "all", enter: { on: "start" }, spawn: [{ mob: "a raider", count: 3, role: "raider" }] }], complete: { when: "cleared" } });
	WorldFiles.configure(folder);
	plan = MD.load(folder, "rewards.escrow,encounters.director", "");
	currentField.set(null, OM.prepare(plan, null, false)); RE.reset(); ED.reset();
	check("manifest-valid", plan.errors.isEmpty() && ED.enabled(), s(plan.problems()));
	var errs = s(java.lang.String.join(" | ", ED.definitions().errors));
	check("budget-refused", errs.indexOf("directors/fix.crowd.v1.json: /budget/actors: phase all spawns 3, more than the budget of 2 actors at once") >= 0
		&& ED.definitions().entries.size() == 2, errs);
	new java.io.File(encs, "fix.crowd.v1.json").delete(); ED.reset();

	// the boss: it wakes when a player comes in
	var hall = newRoom(), ann = player("Director Ann", hall);
	var W = director(hall, "example.warden");
	tick(W, hall);
	var warden = find(hall, "the Cinder Warden");
	check("wakes", warden.length == 1 && s(W.describe(ann, false)).indexOf("Warning: The Cinder Warden raises its hammer.") >= 0, s(W.describe(ann, true)));
	check("scale-one", warden[0].maxState().getHitPoints() == 400, "hp " + warden[0].maxState().getHitPoints());
	calm(hall);
	// below 66%: the imps, once
	hp(warden[0], 60); tick(W, hall);
	check("phase-two", find(hall, "a cinder imp").length == 2 && s(W.describe(ann, false)).indexOf("Phase 2 of 3") >= 0, s(W.describe(ann, true)));
	tick(W, hall, 3);
	check("spawn-once", find(hall, "a cinder imp").length == 2, "imps " + find(hall, "a cinder imp").length);
	calm(hall);
	// the saved state: a new director from it resumes without spawning again
	var state = s(W.getStat("STATE"));
	var W2 = CMClass.getBehavior("EncounterDirector"); W2.setParms("ENCOUNTER=example.warden"); W2.setStat("STATE", state);
	hall.delBehavior(W); hall.addBehavior(W2); W = W2;
	tick(W, hall, 2);
	check("restored", find(hall, "a cinder imp").length == 2 && find(hall, "the Cinder Warden").length == 1 && s(W.describe(ann, false)).indexOf("Phase 2 of 3") >= 0,
		s(W.describe(ann, true)));
	// a removed imp is removed, not defeated
	find(hall, "a cinder imp")[0].destroy(); tick(W, hall);
	check("removed", s(W.describe(ann, false)).indexOf("a cinder imp (imp): removed") >= 0, s(W.describe(ann, false)));
	// below 33%: the third phase and its warning
	hp(warden[0], 30); tick(W, hall);
	check("phase-three", s(W.describe(ann, false)).indexOf("Warning: The Warden's armor cracks") >= 0 && s(W.describe(ann, true)).indexOf("actions 2 of 2") >= 0, s(W.describe(ann, true)));
	// the boss defeated: complete, its reward offered once through escrow
	calm(hall);
	CMLib.combat().postDeath(ann, warden[0], null);
	tick(W, hall);
	check("complete", s(W.describe(ann, false)).indexOf("(complete)") >= 0 && RE.count(ann, "provisional") == 1, s(W.describe(ann, true)) + " / " + s(RE.describe(ann)));
	tick(W, hall, 3);
	check("reward-once", RE.count(ann, "provisional") == 1, "rewards " + RE.describe(ann));

	// two players: the scale is chosen once, when the run begins
	var hall2 = newRoom(), bo = player("Director Bo", hall2), cy = player("Director Cy", hall2);
	var W3 = director(hall2, "example.warden"); tick(W3, hall2);
	var w2 = find(hall2, "the Cinder Warden");
	check("scale-two", w2.length == 1 && w2[0].maxState().getHitPoints() == 500, "hp " + (w2.length ? w2[0].maxState().getHitPoints() : "none"));
	calm(hall2);
	// a retreat: no one there for five ticks resets the run, and its spawns leave
	var away = newRoom(); away.bringMobHere(bo, false); away.bringMobHere(cy, false);
	tick(W3, hall2, 5);
	check("retreat-resets", find(hall2, "the Cinder Warden").length == 0 && s(W3.describe(bo, true)).indexOf("(idle)") >= 0, s(W3.describe(bo, true)));

	// the defense: waves when the last is cleared, the keeper must live
	var gate = newRoom(), keeper = npc("the old keeper", gate), dan = player("Director Dan", gate);
	var S = director(gate, "example.siege"); tick(S, gate);
	check("wave-one", find(gate, "a raider").length == 2, "raiders " + find(gate, "a raider").length);
	calm(gate);
	var r = find(gate, "a raider"); CMLib.combat().postDeath(dan, r[0], null); CMLib.combat().postDeath(dan, r[1], null);
	tick(S, gate);
	check("wave-two", find(gate, "a raider").length == 2 && s(S.describe(dan, false)).indexOf("Warning: More raiders!") >= 0, s(S.describe(dan, true)));
	calm(gate);
	// the keeper falls: the encounter is lost, and the trial's provisional rewards with it
	RE.offer(dan, "example.trial", "example.room-one", "room-one");
	CMLib.combat().postDeath(find(gate, "a raider")[0], keeper, null);
	tick(S, gate);
	check("protect-fails", s(S.describe(dan, false)).indexOf("(failed)") >= 0 && RE.count(dan, "forfeited") == 1, s(S.describe(dan, true)) + " / " + s(RE.describe(dan)));
	calm(gate);

	check("stats", MS.value("encounters", "example.warden", "actors removed") >= 1 && MS.value("encounters", "example.warden", "completions") == 1, s(MS.report()));
	var found = s(java.lang.String.join(" | ", ED.FAMILY_ENTRY.check()));
	check("check-clean", found.length == 0, found);
	write(new java.io.File(encs, "fix.typo.v1.json"), { schema: 1, id: "fix.typo", version: 1, budget: { actors: 2, loot: 1 }, activity: "example.trial",
		phases: [{ id: "a", enter: { on: "start" }, spawn: [{ mob: "a fixg03 nobody", role: "boss" }] },
		         { id: "b", enter: { on: "hp_below", role: "chief", percent: 50 }, reward: "fix.none" }], complete: { when: "defeated", role: "boss" } });
	found = s(java.lang.String.join(" | ", ED.FAMILY_ENTRY.check()));
	check("check-refs", found.indexOf("fix.typo.v1.json: /phases/0/spawn/0/mob: no catalog mob 'a fixg03 nobody'") >= 0
		&& found.indexOf("fix.typo.v1.json: /phases/1/reward: no reward fix.none") >= 0
		&& found.indexOf("fix.typo.v1.json: /phases/1/enter/role: no earlier phase spawns the role chief") >= 0, found);
} finally {
	for (var i = 0; i < rooms.length; i++) { var R = rooms[i], ms = []; for (var j = 0; j < R.numInhabitants(); j++) ms.push(R.fetchInhabitant(j)); for (var j = 0; j < ms.length; j++) { ms[j].setPlayerStats(null); ms[j].destroy(); R.delInhabitant(ms[j]); } }
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) { actors[i].setPlayerStats(null); actors[i].destroy(); }
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogMob(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < rooms.length; i++) rooms[i].destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); RE.reset(); ED.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
