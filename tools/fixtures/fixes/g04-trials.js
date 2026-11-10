// G04 escalating trials (with G08's devices): between rounds the director offers modifiers drawn from its stream; the
// next round waits; TRIALS CHOOSE takes one (later foes tougher, or with traits) and goes on; TRIALS STOP banks what was won
// and ends the trial (DIRECTOR_STOPPED); a chosen modifier's reward is paid on completion; the state is saved.
FIXFILE = "g04-trials";
var ED = CM.core.EncounterDirectors, RE = CM.core.RewardEscrow, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-g04-").toFile();
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
function tick(B, R, n) { for (var i = 0; i < (n || 1); i++) B.tick(R, Tickable.TICKID_ROOM_BEHAVIOR); }
try {
	var ex = new java.io.File("../SUDDocs/creator/examples");
	var mods = dir("modules");
	copyDir(new java.io.File(ex, "reward-escrow/modules"), mods);
	copyDir(new java.io.File(ex, "encounter-director/modules"), mods);
	copyDir(new java.io.File(ex, "reward-escrow/rewards"), dir("rewards"));
	write(new java.io.File(dir("traits"), "fx.fury.v1.json"), { schema: 1, id: "fx.fury", version: 1, title: "Furious", power: 0 });
	catalogMob("a fxg04 rat", 50);
	write(new java.io.File(dir("directors"), "fx.trial.v1.json"), { schema: 1, id: "fx.trial", version: 1, budget: { actors: 5, actions: 0, loot: 5 },
		activity: "example.trial", bank: true,
		phases: [{ id: "r1", enter: { on: "start" }, spawn: [{ mob: "a fxg04 rat", role: "rat" }] },
			{ id: "r2", enter: { on: "cleared" }, spawn: [{ mob: "a fxg04 rat", role: "rat" }] },
			{ id: "r3", enter: { on: "cleared" }, spawn: [{ mob: "a fxg04 rat", role: "rat" }] }],
		complete: { when: "cleared" },
		trial: { offers: 2, modifiers: [{ id: "hardy", title: "Hardy foes", hp: 100 }, { id: "fury", title: "Furious foes", traits: ["fx.fury"] },
			{ id: "bounty", title: "A bounty", reward: "example.room-two" }] } });
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "rewards.escrow,encounters.director", "");
	currentField.set(null, OM.prepare(plan, null, false)); RE.reset(); ED.reset();
	check("definitions", plan.errors.isEmpty() && ED.definitions().errors.isEmpty() && ED.definitions().has("fx.trial"), s(ED.definitions().errors));

	var hall = scratchRoom(); rooms.push(hall);
	var ann = player("Trial Ann", hall);
	var W = CMClass.getBehavior("EncounterDirector"); W.setParms("ENCOUNTER=fx.trial"); hall.addBehavior(W);
	tick(W, hall); calm(hall);
	check("round-one", find(hall, "a fxg04 rat").length == 1, "rats " + find(hall, "a fxg04 rat").length);
	CMLib.combat().postDeath(ann, find(hall, "a fxg04 rat")[0], null); calm(hall);
	tick(W, hall); tick(W, hall);
	var shown = s(W.trial(ann, ""));
	check("offered", shown.indexOf("Offered: 1.") >= 0 && shown.indexOf("2.") >= 0 && find(hall, "a fxg04 rat").length == 0, shown + " / rats " + find(hall, "a fxg04 rat").length);
	check("no-choice-yet", s(W.trial(ann, "CHOOSE 3")).indexOf("Choose 1 to 2") >= 0, s(W.trial(ann, "CHOOSE 3")));
	// the state is saved with the offers
	var state = s(W.getStat("STATE"));
	check("saved", state.indexOf("offers") >= 0 && state.indexOf("offered") >= 0, state.substring(0, 300));
	// choose: round two comes, with the modifier
	var said = s(W.trial(ann, "CHOOSE 1"));
	var pick = said.indexOf("Hardy") >= 0 ? "hardy" : said.indexOf("Furious") >= 0 ? "fury" : "bounty";
	var rat = find(hall, "a fxg04 rat"); calm(hall);
	var applied = rat.length == 1 && (pick == "hardy" ? rat[0].maxState().getHitPoints() == 2 * CMLib.catalog().getCatalogMob("a fxg04 rat").maxState().getHitPoints()
		: pick == "fury" ? rat[0].fetchEffect("Prop_EliteTraits") != null : true);
	check("chosen", said.indexOf("You choose") >= 0 && applied, said + " / " + pick + " rats " + rat.length + (rat.length ? " hp " + rat[0].maxState().getHitPoints() : ""));
	check("shown-chosen", s(W.trial(ann, "")).indexOf("Chosen:") >= 0, s(W.trial(ann, "")));
	// round two cleared: offers again, of the modifiers not yet chosen; STOP banks and ends
	clearVar("FXG04STOP");
	var scribe = scriptedMob("Fxg04scribe", "SIGNAL_PROG DIRECTOR_STOPPED\nmpsetvar FIXTEST FXG04STOP $g\n~\n"); actors.push(scribe); hall.bringMobHere(scribe, false);
	CMLib.combat().postDeath(ann, rat[0], null); calm(hall);
	tick(W, hall); tick(W, hall);
	var again = s(W.trial(ann, ""));
	check("offered-again", again.indexOf("Offered:") >= 0 && again.indexOf(said.replace("You choose ", "").replace(".", "")) < again.indexOf("Offered:"), again);
	var stop = s(W.trial(ann, "STOP"));
	tickMob(scribe); tickMob(scribe);
	check("stopped", stop.indexOf("You end the trial after round 2") >= 0 && find(hall, "a fxg04 rat").length == 0 && s(W.describe(ann, true)).indexOf("stopped") >= 0
		&& gvar("FXG04STOP").indexOf("fx.trial") == 0, stop + " / " + gvar("FXG04STOP") + " / " + s(W.describe(ann, true)).substring(0, 400));
	check("no-more", s(W.trial(ann, "CHOOSE 1")).indexOf("no choice to make") >= 0, s(W.trial(ann, "CHOOSE 1")));
	// WORLD CHECK: a modifier naming a trait or reward that is not there
	write(new java.io.File(dir("directors"), "fx.bad.v1.json"), { schema: 1, id: "fx.bad", version: 1, budget: { actors: 5, actions: 0, loot: 5 },
		phases: [{ id: "p", enter: { on: "start" }, spawn: [{ mob: "a fxg04 rat" }] }], complete: { when: "cleared" },
		trial: { modifiers: [{ id: "x", traits: ["fx.none"], reward: "fx.nothing" }] } });
	var found = s(ED.FAMILY_ENTRY.check());
	check("check", found.indexOf("fx.bad.v1.json: /trial/modifiers/0/reward: no reward fx.nothing") >= 0 && found.indexOf("/trial/modifiers/0/traits: no trait fx.none") >= 0, found);
} finally {
	for (var i = 0; i < rooms.length; i++) { var R = rooms[i], ms = []; for (var j = 0; j < R.numInhabitants(); j++) ms.push(R.fetchInhabitant(j)); for (var j = 0; j < ms.length; j++) { ms[j].setPlayerStats(null); ms[j].destroy(); R.delInhabitant(ms[j]); } }
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) { actors[i].setPlayerStats(null); actors[i].destroy(); }
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogMob(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	for (var i = 0; i < rooms.length; i++) rooms[i].destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); RE.reset(); ED.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
