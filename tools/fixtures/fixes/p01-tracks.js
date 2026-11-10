// P01 progression tracks, Q06 track migration, Q07 the text tree: a character discipline, an organization research
// project and an item mastery track earning on their own records; a source key granting once; milestones (combat stats,
// unlocks); a reset; a track that replaces an old one taking over its records against a pre-change save.
FIXFILE = "p01-tracks";
var PT = CM.core.ProgressionTracks, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, MS = CM.core.ModuleStats;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-p01-").toFile();
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
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var room = scratchRoom(), actors = [], clan = null, clan2 = null;
function actor(name) { var m = newMob("GenMob", room); m.setName(name); actors.push(m); return m; }
function pts(owner, id) { return PT.progress(owner, id)[0]; }
function lvl(owner, id) { return PT.progress(owner, id)[1]; }
var plan = null;
function boot(snapshot) { plan = MD.load(folder, "progression.tracks", ""); currentField.set(null, OM.prepare(plan, snapshot, snapshot != null)); PT.reset(); }
try {
	var ex = new java.io.File("../SUDDocs/creator/examples/progression-tracks");
	var mods = dir("modules"); copyDir(new java.io.File(ex, "modules"), mods);
	var tracks = dir("tracks"); copyDir(new java.io.File(ex, "tracks"), tracks);
	write(new java.io.File(tracks, "fix.old.v1.json"), { schema: 1, id: "fix.old", version: 1, owner: "character", earn: [{ on: "script" }], curve: { type: "linear", per_level: 5, cap: 4 } });
	WorldFiles.configure(folder);
	// the contract: the three owner scopes
	var bad = JSON.parse(read(new java.io.File(mods, "progression.tracks.json"))); bad.scopes = ["character"];
	var badDir = Files.createTempDirectory("sud-p01-bad-").toFile(); var badMods = new java.io.File(badDir, "modules"); badMods.mkdirs();
	write(new java.io.File(badMods, "progression.tracks.json"), bad);
	check("contract", s(MD.load(badDir, "progression.tracks", "").problems()).indexOf("requires organization scope") >= 0, s(MD.load(badDir, "progression.tracks", "").problems()));
	new java.io.File(badMods, "progression.tracks.json").delete(); badMods.delete(); badDir.delete();
	boot(null);
	check("manifest-valid", plan.errors.isEmpty() && PT.enabled() && PT.definitions().errors.isEmpty() && PT.definitions().entries.size() == 4, s(plan.problems()) + " / " + s(PT.definitions().report()));

	// a character's discipline: kills and casts, a key once, a milestone with stats
	var ada = actor("Track Ada"), foe1 = actor("Track Foe One");
	var A = PT.of(ada);
	CMLib.combat().postDeath(ada, foe1, null);
	check("kill-earns", pts(A, "example.emberwright") == 5, "embers " + pts(A, "example.emberwright"));
	PT.died(foe1, ada);
	check("key-once", pts(A, "example.emberwright") == 5 && MS.value("tracks", "example.emberwright", "duplicate sources suppressed") == 1, "embers " + pts(A, "example.emberwright"));
	PT.cast(ada, "example.ember"); PT.cast(ada, "example.ember"); PT.cast(ada, "example.ember");
	check("cast-earns", pts(A, "example.emberwright") == 11 && lvl(A, "example.emberwright") == 1, "embers " + pts(A, "example.emberwright") + " level " + lvl(A, "example.emberwright"));
	check("milestone-stats", ada.fetchEffect("Prop_Tracks") != null && s(PT.stats(A).describe()).indexOf("penetration") >= 0, s(PT.stats(A).describe()));
	PT.cast(ada, "fix.other");
	check("cast-skill-only", pts(A, "example.emberwright") == 11, "another skill earned");
	// the cap, and a reset
	PT.earn(A, "example.emberwright", 500, null);
	check("cap", pts(A, "example.emberwright") == 60 && lvl(A, "example.emberwright") == 3 && PT.unlocked(A, "emberwright"), "embers " + pts(A, "example.emberwright"));
	PT.resetTrack(A, "example.emberwright");
	check("reset", pts(A, "example.emberwright") == 0 && !PT.unlocked(A, "emberwright") && s(PT.stats(A).describe()).indexOf("penetration") < 0, "after reset " + pts(A, "example.emberwright"));

	// an organization's research: contributions and members' kills, its own record
	clan = CMClass.getCommon("DefaultClan"); clan.setName("Fixp01 Foundry Guild"); clan.setGovernmentID(0); CMLib.clans().addClan(clan);
	var bo = actor("Track Bo"); bo.setClan(clan.clanID(), clan.getGovernment().getAcceptPos());
	var G = PT.of(clan);
	CMLib.beanCounter().addMoney(bo, CMLib.beanCounter().getCurrency(bo), 1200.0);
	var said = s(PT.contribute(bo, "example.foundry", 1205));
	check("contribute", pts(G, "example.foundry") == 120 && said.indexOf("120 research") >= 0, said + " / " + pts(G, "example.foundry"));
	var foe2 = actor("Track Foe Two"); PT.died(foe2, bo);
	check("member-kill", pts(G, "example.foundry") == 121 && pts(PT.of(bo), "example.emberwright") == 5, "guild " + pts(G, "example.foundry") + " bo " + pts(PT.of(bo), "example.emberwright"));
	check("not-unlocked", !PT.unlockedFor(bo, "guild-forge"), "unlocked too soon");
	CMLib.beanCounter().addMoney(bo, CMLib.beanCounter().getCurrency(bo), 1000.0);
	PT.contribute(bo, "example.foundry", 1000);
	check("unlocked", pts(G, "example.foundry") == 150 && PT.unlockedFor(bo, "guild-forge"), "guild " + pts(G, "example.foundry"));
	throws("complete", function () { PT.contribute(bo, "example.foundry", 100); }, "is complete");
	check("givers", s(PT.tree(G)).indexOf("Given by: Track Bo 150") >= 0 || s(PT.tree(G)).indexOf("Given by: Track Bo 149") >= 0, s(PT.tree(G)));
	// a member of two organizations names the one they give to; the gift is credited there, not to the last one joined
	clan2 = CMClass.getCommon("DefaultClan"); clan2.setName("Fixp01 Second Guild"); clan2.setGovernmentID(0); CMLib.clans().addClan(clan2);
	var di = actor("Track Di"); di.setClan(clan.clanID(), clan.getGovernment().getAcceptPos()); di.setClan(clan2.clanID(), clan2.getGovernment().getAcceptPos());
	CMLib.beanCounter().addMoney(di, CMLib.beanCounter().getCurrency(di), 200.0);
	throws("several-organizations", function () { PT.contribute(di, "example.foundry", 100); }, "name one");
	var said2 = s(PT.contribute(di, "example.foundry", 100, "Fixp01 Second Guild"));
	check("named-organization", pts(PT.of(clan2), "example.foundry") == 10 && said2.indexOf("Fixp01 Second Guild") >= 0 && pts(G, "example.foundry") == 150,
		said2 + " / second " + pts(PT.of(clan2), "example.foundry") + " / first " + pts(G, "example.foundry"));
	throws("not-a-member", function () { PT.contribute(bo, "example.foundry", 100, "Fixp01 Second Guild"); }, "not in");

	// an item's mastery: kills while it is wielded, on the item, not its wielder; another copy has its own
	var cy = actor("Track Cy");
	var blade = CMClass.getWeapon("GenWeapon"); blade.setName("a fixp01 blade");
	var tp = CMClass.getAbility("Prop_Tracks"); tp.setMiscText("TRACKS=example.blade-mastery"); blade.addNonUninvokableEffect(tp);
	cy.addItem(blade); blade.wearAt(CM.Items.interfaces.Wearable.WORN_WIELD);
	var other = blade.copyOf(); other.setName("a fixp01 second blade"); cy.addItem(other); other.unWear(); // a copy keeps where it was worn
	for (var i = 0; i < 3; i++) PT.died(actor("Track Foe Item " + i), cy);
	var B = PT.of(blade);
	check("item-earns", pts(B, "example.blade-mastery") == 3 && lvl(B, "example.blade-mastery") == 1, "notches " + pts(B, "example.blade-mastery"));
	check("item-own-record", pts(PT.of(other), "example.blade-mastery") == 0 && pts(PT.of(cy), "example.emberwright") == 15, "copy " + pts(PT.of(other), "example.blade-mastery"));
	check("item-stats", s(blade.fetchEffect("Prop_Tracks").combatStats().describe()).indexOf("accuracy") >= 0, s(blade.fetchEffect("Prop_Tracks").combatStats().describe()));
	blade.unWear(); PT.died(actor("Track Foe Unwielded"), cy);
	check("item-wielded-only", pts(B, "example.blade-mastery") == 3, "an unwielded blade earned");

	// the tree, in plain text
	var tree = s(PT.describe(cy));
	check("tree", tree.indexOf("Emberwright discipline: level 1 of 3, 20 embers (10 to level 2)") >= 0 && tree.indexOf("[x] 1. Kindled") >= 0 && tree.indexOf("[ ] 3. Emberwright") >= 0
		&& tree.indexOf("a fixp01 blade's tracks:") >= 0, tree);

	// Q06: the old track's record moves to the track that replaces it, against the save made before the change
	PT.earn(PT.of(ada), "fix.old", 7, null);
	var snap = s(OM.snapshot());
	new java.io.File(tracks, "fix.old.v1.json").delete();
	write(new java.io.File(tracks, "fix.new.v1.json"), { schema: 1, id: "fix.new", version: 1, owner: "character", earn: [{ on: "script" }], curve: { type: "linear", per_level: 5, cap: 4 }, replaces: ["fix.old"] });
	boot(snap);
	check("migrated", pts(PT.of(ada), "fix.new") == 7 && lvl(PT.of(ada), "fix.new") == 1 && s(PT.describe(ada)).indexOf("fix.old") < 0, s(PT.describe(ada)));
	var again = s(OM.snapshot()); boot(again);
	check("migrated-once", pts(PT.of(ada), "fix.new") == 7, "points " + pts(PT.of(ada), "fix.new"));

	// checks: a replaced track still installed, a cast of a skill that is not installed
	write(new java.io.File(tracks, "fix.old.v1.json"), { schema: 1, id: "fix.old", version: 1, owner: "character", earn: [{ on: "cast", skill: "fix.noskill" }], curve: { type: "linear", per_level: 5, cap: 4 } });
	var found = s(java.lang.String.join(" | ", PT.FAMILY_ENTRY.check()));
	check("check", found.indexOf("tracks/fix.new.v1.json: /replaces: fix.old is still installed") >= 0 && found.indexOf("tracks/fix.old.v1.json: /earn/0/skill: no composition fix.noskill") >= 0, found);
	write(new java.io.File(tracks, "fix.bad.v1.json"), { schema: 1, id: "fix.bad", version: 1, owner: "organization", curve: { type: "table", points: [5, 3] } });
	found = s(java.lang.String.join(" | ", PT.FAMILY_ENTRY.check()));
	check("check-curve", found.indexOf("tracks/fix.bad.v1.json: /curve/points/1: each level needs more points than the one before") >= 0, found);
} finally {
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	if (clan != null) CMLib.clans().removeClan(clan);
	if (clan2 != null) CMLib.clans().removeClan(clan2);
	room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); PT.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
