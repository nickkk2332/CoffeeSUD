// I18 reward escrow: provisional, banked, forfeited and claimed rewards; one entry per event key; leaving, failing,
// dying and restoring as the activity declares; claims made in one transaction, refused whole; the three-room trial.
FIXFILE = "i18-escrow";
var RE = CM.core.RewardEscrow, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, MS = CM.core.ModuleStats;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-i18-").toFile();
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
var room = scratchRoom(), actors = [], catalogued = [];
function actor(name) { var m = newMob("GenMob", room); m.setName(name); actors.push(m); return m; }
function named(m, name) { var n = 0; for (var i = 0; i < m.numItems(); i++) if (s(m.getItem(i).Name()) == name) n++; return n; }
function money(m) { return CMLib.beanCounter().getTotalAbsoluteValue(m, CMLib.beanCounter().getCurrency(m)); }
var plan = null;
function boot(snapshot) {
	plan = MD.load(folder, "rewards.escrow", "");
	currentField.set(null, OM.prepare(plan, snapshot, snapshot != null)); RE.reset();
}
try {
	var ex = new java.io.File("../docs/creator/examples/reward-escrow");
	copyDir(new java.io.File(ex, "modules"), dir("modules"));
	var rewards = dir("rewards");
	copyDir(new java.io.File(ex, "rewards"), rewards);
	// a bundle with an item, one with an item the catalog lacks, and activities with other policies
	var token = CMClass.getItem("GenItem"); token.setName("a fixi18 token"); CMLib.catalog().addCatalog(token); catalogued.push("a fixi18 token");
	write(new java.io.File(rewards, "fix.token.v1.json"), { schema: 1, id: "fix.token", version: 1, kind: "bundle", items: [{ name: "a fixi18 token", count: 2 }], money: 5 });
	write(new java.io.File(rewards, "fix.ghost.v1.json"), { schema: 1, id: "fix.ghost", version: 1, kind: "bundle", items: [{ name: "a fixi18 missing thing" }] });
	write(new java.io.File(rewards, "fix.brittle.v1.json"), { schema: 1, id: "fix.brittle", version: 1, kind: "activity", on_restore: "forfeit", on_leave: "bank" });
	write(new java.io.File(rewards, "fix.shrine-only.v1.json"), { schema: 1, id: "fix.shrine-only", version: 1, kind: "activity", claim_rooms: ["Fixi18 Nowhere#9"] });
	WorldFiles.configure(folder);
	boot(null);
	check("manifest-valid", plan.errors.isEmpty() && RE.enabled() && s(OM.snapshot()).indexOf('"requires_handler":"rewards.escrow"') >= 0, s(plan.problems()));
	check("definitions", RE.definitions().errors.isEmpty() && RE.definitions().entries.size() == 8, s(RE.definitions().report()));

	// the three-room trial: each room's spoils provisional, banked at the shrine, the last room lost to a death
	var tia = actor("Escrow Tia");
	var r1 = RE.offer(tia, "example.trial", "example.room-one", "room-one");
	check("provisional", r1 > 0 && s(RE.state(tia, r1)) == "provisional" && RE.inRun(tia, "example.trial"), "entry " + r1 + " " + RE.state(tia, r1));
	check("one-key-one-entry", RE.offer(tia, "example.trial", "example.room-one", "room-one") == 0 && RE.count(tia, "provisional") == 1,
		"the same completion was rewarded twice");
	var r2 = RE.offer(tia, "example.trial", "example.room-two", "room-two");
	check("bank", RE.bank(tia, "example.trial") == 2 && s(RE.state(tia, r1)) == "banked" && s(RE.state(tia, r2)) == "banked" && RE.inRun(tia, "example.trial"),
		"banked " + RE.state(tia, r1) + " " + RE.state(tia, r2));
	var r3 = RE.offer(tia, "example.trial", "example.room-three", "room-three");
	RE.died(tia);
	check("death-forfeits", s(RE.state(tia, r3)) == "forfeited" && s(RE.state(tia, r1)) == "banked" && !RE.inRun(tia, "example.trial"),
		"room three " + RE.state(tia, r3) + ", room one " + RE.state(tia, r1));
	// a claim gives everything at once, with a receipt; the same reward cannot be claimed twice
	var before = money(tia);
	var receipt = s(RE.claim(tia, 0));
	check("claim", money(tia) == before + 130 && s(RE.state(tia, r1)) == "claimed" && s(RE.state(tia, r2)) == "claimed" && receipt.indexOf("Claimed rewards") >= 0,
		"money " + before + " -> " + money(tia) + " / " + receipt);
	throws("claim-once", function () { RE.claim(tia, r1); }, "already claimed");
	check("claim-nothing", s(RE.claim(tia, 0)).indexOf("no banked rewards") >= 0, "a second claim found something");

	// leaving and failing do what the activity says; a completion banks the rest
	var uma = actor("Escrow Uma");
	var u1 = RE.offer(uma, "example.trial", "example.room-one", "room-one");
	RE.left(uma, "example.trial");
	check("leave-forfeits", s(RE.state(uma, u1)) == "forfeited" && !RE.inRun(uma, "example.trial"), s(RE.state(uma, u1)));
	var u2 = RE.offer(uma, "fix.brittle", "example.room-one", "room-one");
	RE.left(uma, "fix.brittle");
	check("leave-banks", s(RE.state(uma, u2)) == "banked", "fix.brittle's on_leave is bank: " + RE.state(uma, u2));
	var u3 = RE.offer(uma, "example.trial", "example.room-two", "room-two");
	RE.end(uma, "example.trial", "fail");
	check("fail-forfeits", s(RE.state(uma, u3)) == "forfeited", s(RE.state(uma, u3)));
	var u4 = RE.offer(uma, "example.trial", "example.room-three", "room-three");
	RE.end(uma, "example.trial", "complete");
	check("complete-banks", s(RE.state(uma, u4)) == "banked", s(RE.state(uma, u4)));
	// a new run may earn a room again; within a run, the same room is rewarded once
	var u5 = RE.offer(uma, "example.trial", "example.room-one", "room-one");
	check("new-run", u5 > 0 && RE.offer(uma, "example.trial", "example.room-one", "room-one") == 0, "u5 " + u5);

	// a restore: example.trial keeps its run going; fix.brittle forfeits; a committed key stays committed
	var vic = actor("Escrow Vic");
	var v1 = RE.offer(vic, "example.trial", "example.room-one", "room-one");
	var v2 = RE.offer(vic, "fix.brittle", "example.room-two", "room-two");
	var snap = s(OM.snapshot());
	boot(snap);
	check("restore-keeps", s(RE.state(vic, v1)) == "provisional" && RE.inRun(vic, "example.trial"), s(RE.state(vic, v1)));
	check("restore-forfeits", s(RE.state(vic, v2)) == "forfeited" && !RE.inRun(vic, "fix.brittle"), s(RE.state(vic, v2)));
	check("restore-no-replay", RE.offer(vic, "example.trial", "example.room-one", "room-one") == 0, "a completion replayed after the restore was rewarded again");
	// the restore policy applies once: a second read of the ledger changes nothing
	var once = s(OM.snapshot()); RE.describe(vic);
	check("restore-once", s(OM.snapshot()) == once, "reading the ledger again changed it");

	// a claim that cannot be made whole gives nothing
	var wes = actor("Escrow Wes");
	RE.offer(wes, "fix.brittle", "fix.token", "token"); var w2 = RE.offer(wes, "fix.brittle", "fix.ghost", "ghost");
	RE.end(wes, "fix.brittle", "complete");
	var items = wes.numItems(), cash = money(wes);
	throws("claim-whole-or-nothing", function () { RE.claim(wes, 0); }, "no catalog item 'a fixi18 missing thing'");
	check("nothing-given", wes.numItems() == items && money(wes) == cash && s(RE.state(wes, w2)) == "banked", "items " + wes.numItems() + " money " + money(wes));
	var said = s(RE.claim(wes, 1));
	check("items-claimed", said.indexOf("a fixi18 token, a fixi18 token") >= 0 && named(wes, "a fixi18 token") == 2 && money(wes) == cash + 5, said + " / tokens " + named(wes, "a fixi18 token") + " money " + money(wes));
	// a reward claimed only at a place
	var xia = actor("Escrow Xia");
	var x1 = RE.offer(xia, "fix.shrine-only", "example.room-one", "room-one"); RE.end(xia, "fix.shrine-only", "complete");
	throws("claim-place", function () { RE.claim(xia, x1); }, "claimed at Fixi18 Nowhere#9");

	// a party shares a completion, each with their own entry
	var yan = actor("Escrow Yan"), zed = actor("Escrow Zed"); zed.setFollowing(yan);
	var shared = RE.offerParty(yan, "example.trial", "example.room-one", "room-one");
	check("party", shared.size() == 2 && RE.count(yan, "provisional") == 1 && RE.count(zed, "provisional") == 1, "entries " + shared);

	// inspection and checks
	check("describe", s(RE.describe(vic)).indexOf("Spoils of room one: 50 in coin") >= 0, s(RE.describe(vic)));
	var found = s(java.lang.String.join(" | ", RE.FAMILY_ENTRY.check()));
	check("check", found.indexOf("rewards/fix.ghost.v1.json: /items: no catalog item 'a fixi18 missing thing'") >= 0
		&& found.indexOf("rewards/fix.shrine-only.v1.json: /claim_rooms: no room Fixi18 Nowhere#9") >= 0, found);
	write(new java.io.File(rewards, "fix.empty.v1.json"), { schema: 1, id: "fix.empty", version: 1, kind: "bundle" });
	found = s(java.lang.String.join(" | ", RE.FAMILY_ENTRY.check()));
	check("check-empty", found.indexOf("rewards/fix.empty.v1.json: /: a bundle gives at least an item, money or a resource") >= 0, found);
	check("stats", MS.value("rewards", "example.trial", "duplicate events suppressed") >= 2 && MS.value("rewards", "claim", "refused") >= 1, s(MS.report()));
} finally {
	for (var i = 0; i < actors.length; i++) if (!actors[i].amDestroyed()) actors[i].destroy();
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogItem(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	room.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); RE.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
