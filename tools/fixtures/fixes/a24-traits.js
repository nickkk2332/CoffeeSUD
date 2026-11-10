// A24 elite traits (with G08's possession) and G06 director hooks: a director's spawn gives its pack traits and picks each
// actor's own within a power budget and exclusions; the traits' effects work and LOOK reads their lines; a possessing
// trait passes to a packmate when its host dies; MPDIRECTOR START begins a scripted encounter; the room's scripts hear
// DIRECTOR_COMPLETE; WORLD CHECK names a trait that is not installed.
FIXFILE = "a24-traits";
var ED = CM.core.EncounterDirectors, RE = CM.core.RewardEscrow, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files, JString = java.lang.String, STR = CM.Common.interfaces.CharStats.STAT_STRENGTH;
var folder = Files.createTempDirectory("sud-a24-").toFile();
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
function player(name, R) { var m = newMob("GenMob", R); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.baseState().setHitPoints(5000); m.recoverMaxState(); m.resetToMaxState(); actors.push(m); return m; }
function catalogMob(name, hp) { var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setAbility((1 << 23) + (1 << 15) + (hp - 1)); m.recoverPhyStats(); m.text(); CMLib.catalog().addCatalog(m); catalogued.push(name); }
function find(R, name) { var out = []; for (var i = 0; i < R.numInhabitants(); i++) { var M = R.fetchInhabitant(i); if (s(M.Name()) == name) out.push(M); } return out; }
function calm(R) { for (var i = 0; i < R.numInhabitants(); i++) R.fetchInhabitant(i).makePeace(true); }
function traitsOf(M) { var T = M.fetchEffect("Prop_EliteTraits"); return T == null ? [] : s(T.traitIDs()).replace(/[\[\] ]/g, "").split(","); }
try {
	var ex = new java.io.File("../SUDDocs/creator/examples");
	var mods = dir("modules");
	copyDir(new java.io.File(ex, "reward-escrow/modules"), mods);
	copyDir(new java.io.File(ex, "encounter-director/modules"), mods);
	copyDir(new java.io.File(ex, "reward-escrow/rewards"), dir("rewards"));
	var traits = dir("traits");
	write(new java.io.File(traits, "fx.fury.v1.json"), { schema: 1, id: "fx.fury", version: 1, title: "Furious", line: "Its eyes burn.", power: 0, effects: "Prop_Adjuster;STR+3" });
	write(new java.io.File(traits, "fx.storm.v1.json"), { schema: 1, id: "fx.storm", version: 1, title: "Stormcaller", line: "Sparks crawl over its skin.", power: 2, excludes: ["fx.frost"] });
	write(new java.io.File(traits, "fx.frost.v1.json"), { schema: 1, id: "fx.frost", version: 1, title: "Frostbound", line: "Rime cracks as it moves.", power: 1 });
	write(new java.io.File(traits, "fx.giant.v1.json"), { schema: 1, id: "fx.giant", version: 1, title: "Giant", power: 9 });
	write(new java.io.File(traits, "fx.soul.v1.json"), { schema: 1, id: "fx.soul", version: 1, title: "Soul-bound", line: "Something else looks out of it.", power: 1, possess: true });
	catalogMob("a fxa24 ghoul", 60); catalogMob("a fxa24 bearer", 60);
	write(new java.io.File(dir("directors"), "fx.pack.v1.json"), { schema: 1, id: "fx.pack", version: 1, start: "script", budget: { actors: 10, actions: 0, loot: 0 },
		phases: [{ id: "all", enter: { on: "start" }, spawn: [
			{ mob: "a fxa24 ghoul", count: 3, role: "ghoul", traits: { pick: 2, budget: 3, from: ["fx.storm", "fx.frost", "fx.giant"], pack: ["fx.fury"] } },
			{ mob: "a fxa24 bearer", count: 1, role: "bearer", traits: { pick: 1, from: ["fx.soul"] } }] }],
		complete: { when: "cleared" } });
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "rewards.escrow,encounters.director", "");
	currentField.set(null, OM.prepare(plan, null, false)); RE.reset(); ED.reset();
	check("definitions", plan.errors.isEmpty() && ED.enabled() && CM.core.EliteTraits.definitions().errors.isEmpty() && CM.core.EliteTraits.definitions().entries.size() == 5,
		s(plan.problems()) + " / " + s(CM.core.EliteTraits.definitions().errors));

	var hall = scratchRoom(); rooms.push(hall);
	var ann = player("Trait Ann", hall);
	var W = CMClass.getBehavior("EncounterDirector"); W.setParms("ENCOUNTER=fx.pack"); hall.addBehavior(W);
	// a room script hears the end of the run
	clearVar("FXA24DONE");
	var scribe = scriptedMob("Fxa24scribe", "SIGNAL_PROG DIRECTOR_COMPLETE\nmpsetvar FIXTEST FXA24DONE $g\n~\n"); actors.push(scribe);
	hall.bringMobHere(scribe, false);
	W.tick(hall, Tickable.TICKID_ROOM_BEHAVIOR);
	check("script-start", find(hall, "a fxa24 ghoul").length == 0, "a script-started encounter began by itself");
	var starter = scriptedMob("Fxa24starter", "ONCE_PROG 100\nmpdirector START\n~\n"); actors.push(starter); hall.bringMobHere(starter, false);
	tickMob(starter);
	var ghouls = find(hall, "a fxa24 ghoul"), bearer = find(hall, "a fxa24 bearer");
	calm(hall);
	check("started", ghouls.length == 3 && bearer.length == 1, "ghouls " + ghouls.length + " bearer " + bearer.length);
	// the pack's trait on all; each one's own within the budget, never both of an exclusion, never one above the budget
	var allOk = true, why = "";
	ghouls.forEach(function (g) {
		var t = traitsOf(g);
		if (t.indexOf("fx.fury") < 0 || t.indexOf("fx.giant") >= 0 || (t.indexOf("fx.storm") >= 0 && t.indexOf("fx.frost") >= 0)) { allOk = false; why += s(t) + "; "; }
	});
	check("picked", allOk, why);
	var g0 = ghouls[0]; g0.recoverCharStats();
	var proto = CMLib.catalog().getCatalogMob("a fxa24 ghoul");
	check("effects", g0.charStats().getStat(STR) == proto.baseCharStats().getStat(STR) + 3, "str " + g0.charStats().getStat(STR) + " vs " + proto.baseCharStats().getStat(STR));
	// LOOK reads the traits
	var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); ann.setSession(VS); VS.setMob(ann);
	hall.send(ann, CMClass.getMsg(ann, g0, null, CMMsg.MSG_LOOK, null));
	var seen = s(field(VS, "bout").toString());
	check("look", seen.indexOf("Furious") >= 0 && seen.indexOf("Its eyes burn.") >= 0, seen.substring(Math.max(0, seen.length - 400)));
	ann.setSession(null);
	// G08: the soul passes to a packmate when its bearer dies
	CMLib.combat().postDeath(ann, bearer[0], null);
	var holders = find(hall, "a fxa24 ghoul").filter(function (g) { return traitsOf(g).indexOf("fx.soul") >= 0; });
	check("possessed", holders.length == 1, "ghouls holding the soul: " + holders.length);
	// all cleared: complete, and the room's scripts hear it
	find(hall, "a fxa24 ghoul").forEach(function (g) { CMLib.combat().postDeath(ann, g, null); });
	calm(hall);
	W.tick(hall, Tickable.TICKID_ROOM_BEHAVIOR);
	tickMob(scribe); tickMob(scribe);
	check("hook", gvar("FXA24DONE").indexOf("fx.pack") == 0, "the script heard '" + gvar("FXA24DONE") + "' / " + s(W.describe(ann, true)));
	// WORLD CHECK: a trait that is not installed
	write(new java.io.File(dir("directors"), "fx.bad.v1.json"), { schema: 1, id: "fx.bad", version: 1, budget: { actors: 10, actions: 0, loot: 0 },
		phases: [{ id: "p", enter: { on: "start" }, spawn: [{ mob: "a fxa24 ghoul", traits: { from: ["fx.missing"] } }] }], complete: { when: "cleared" } });
	var found = s(ED.FAMILY_ENTRY.check());
	check("check", found.indexOf("fx.bad.v1.json: /phases/0/spawn/0: /traits: no trait fx.missing is installed in traits/") >= 0, found);
} finally {
	for (var i = 0; i < rooms.length; i++) { var R = rooms[i], ms = []; for (var j = 0; j < R.numInhabitants(); j++) ms.push(R.fetchInhabitant(j)); for (var j = 0; j < ms.length; j++) { ms[j].setPlayerStats(null); ms[j].destroy(); R.delInhabitant(ms[j]); } }
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) { actors[i].setPlayerStats(null); actors[i].destroy(); }
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogMob(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < rooms.length; i++) rooms[i].destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); RE.reset(); ED.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
