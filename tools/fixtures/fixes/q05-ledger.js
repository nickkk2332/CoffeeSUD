// Q05 ledgers: a character's (controller, grants by module, pending rewards, recent events with their sources, the
// last refused transition with its owner, rule and recovery), an item's and a relationship's; reading one changes
// nothing. (The expedition ledger is played in tools/expedition-integration.py.)
FIXFILE = "q05-ledger";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
var LG = CM.core.Ledgers, PV = CM.core.Provenance, RE = CM.core.RewardEscrow, PT = CM.core.ProgressionTracks;
var MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-q05-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function write(f, obj) { f.getParentFile().mkdirs(); Files.write(f.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var room = scratchRoom(), actors = [], items = [];
function player(name) { var m = newMob("GenMob", room); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); actors.push(m); return m; }
try {
	var ex = new java.io.File("../docs/creator/examples");
	copy(new java.io.File(ex, "reward-escrow"), folder);
	copy(new java.io.File(ex, "progression-tracks"), folder);
	new java.io.File(folder, "README.md").delete();
	write(new java.io.File(folder, "modules/fix.bond.json"), { schema: 1, id: "fix.bond", version: 1, title: "A bond between two", clock_modes: ["TURNS", "REALTIME"],
		scopes: ["relationship"], on_disable: "suspend", state: { trust: { type: "integer", "default": 0, min: 0, max: 10 } } });
	write(new java.io.File(folder, "rewards/fix.far.v1.json"), { schema: 1, id: "fix.far", version: 1, kind: "activity", on_leave: "bank", claim_rooms: ["Fixq05 Shrine#0"] });
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "rewards.escrow,progression.tracks,fix.bond", "");
	currentField.set(null, OM.prepare(plan, null, false)); RE.reset(); PT.reset(); PV.reset();
	check("modules", plan.errors.isEmpty(), s(plan.problems()));

	var ann = player("Ledger Ann"), bo = player("Ledger Bo"); bo.setFollowing(ann);
	var r1 = RE.offer(ann, "example.trial", "example.room-one", "room-one");
	PT.earn(PT.of(ann), "example.emberwright", 12, "fix:q05");
	var far = RE.offer(ann, "fix.far", "example.room-two", "far"); RE.end(ann, "fix.far", "complete");
	throws("refused", function () { RE.claim(ann, far); }, "claimed at");
	OM.put("fix.bond", OM.relationship(ann, bo), MD.parse('{"trust":3}'));
	var blade = CMClass.getWeapon("GenWeapon"); blade.setName("a fixq05 blade"); var tp = CMClass.getAbility("Prop_Tracks"); tp.setMiscText("TRACKS=example.blade-mastery");
	blade.addNonUninvokableEffect(tp); ann.addItem(blade); items.push(blade);

	var dice = CMath.getRandState(), snap = s(OM.snapshot());
	var L = s(LG.character(ann)), I = s(LG.item(blade)), B = s(LG.between(ann, bo));
	check("controller", L.indexOf("Ledger of Ledger Ann (@instance:") == 0 && L.indexOf("Controller: a player character not played at a terminal now") >= 0, L);
	check("grants", L.indexOf("[rewards.escrow]") >= 0 && L.indexOf("Spoils of room one") >= 0 && L.indexOf("[progression.tracks]") >= 0 && L.indexOf("Emberwright discipline: level 1") >= 0, L);
	check("events", L.indexOf("rewards.escrow (example.room-one.v1): reward " + r1 + " offered in example.trial, provisional") >= 0
		&& L.indexOf("progression.tracks (example.emberwright.v1): milestone 1 reached: Kindled") >= 0, L);
	check("refusal", L.indexOf("Last refused: rewards.escrow refused it for Ledger Ann (rule claim of reward " + far + "):") >= 0 && L.indexOf("claimed at Fixq05 Shrine#0") >= 0
		&& L.indexOf("To recover: put right what stops it") >= 0, L);
	check("item", I.indexOf("Ledger of a fixq05 blade") == 0 && I.indexOf("Held by: Ledger Ann") >= 0 && I.indexOf("[progression.tracks]") >= 0 && I.indexOf("example.blade-mastery") >= 0, I);
	check("between", B.indexOf("Ledger Bo follows Ledger Ann.") >= 0 && B.indexOf("[fix.bond] Ledger Ann to Ledger Bo: {\"trust\":3}") >= 0, B);
	check("between-none", s(LG.between(bo, blade)).indexOf("No module keeps a record between them.") >= 0, s(LG.between(bo, blade)));
	check("reads-nothing", dice.equals(CMath.getRandState()) && s(OM.snapshot()) == snap, "a ledger changed something");
	// the command: LEDGER, for no game time; a player reads their party's, not a stranger's
	var stranger = player("Ledger Cy"), cmd = CMClass.getCommand("LedgerCmd");
	check("free", cmd.checkedActionsCost(ann, CM.core.CMParms.parse("LEDGER")) == 0, "LEDGER costs time");
	var told = [];
	var tellField = null;
	PV.event(stranger, "fix", "fix.source", "an event");
	check("provenance-bounded", PV.recent(stranger).size() == 1 && PV.MAX_EVENTS == 16, s(PV.recent(stranger).size()));
	for (var e = 0; e < 40; e++) PV.event(stranger, "fix", "fix.source", "event " + e);
	check("provenance-cap", PV.recent(stranger).size() == 16 && s(PV.recent(stranger).get(15).what) == "event 39", s(PV.recent(stranger).size()));
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) { actors[i].setPlayerStats(null); actors[i].destroy(); }
	room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); RE.reset(); PT.reset(); PV.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
