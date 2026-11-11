// P08 shared pools: world/orgpools.ini names kinds of pool; a member gives units to their organization's pool, paid in money or a
// material, up to its MAX; a service spends from it, refused and spending nothing when it holds too little; every gift and spend
// has a receipt number in the organization's ledger, which keeps the last 20 entries and each giver's and user's totals; POOLS shows
// them; the pools and the ledger are saved with the organization.
FIXFILE = "p08-pools";
var OP = CM.core.OrgPools, Gs = CM.core.Guilds, RM = CM.Items.interfaces.RawMaterial, MD = CM.core.ModuleDefinitions;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p08-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function err(f) { try { f(); return ""; } catch (e) { return s(e); } }
function money(m) { return Number(CMLib.beanCounter().getTotalAbsoluteNativeValue(m)); }
var G = Gs.government("GUILD_NPC"), clans = [], made = [];
function makeOrg(name) { var C = CMClass.getCommon("DefaultClan"); C.setName(name); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.create(); clans.push(C); return C; }
var R = scratchRoom();
function person(name) { var m = CMClass.getMOB("StdMOB"); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.baseCharStats().setMyRace(CMClass.getRace("Human"));
	m.recoverCharStats(); m.bringToLife(R, true); made.push(m); return m; }
try {
	Files.write(new java.io.File(root, "orgpools.ini").toPath(), new java.lang.String(
		'SUPPLIES=TITLE="Supplies" MAX=10 MONEY=5\nORE=TITLE="Ore stock" MAX=50 MATERIAL=IRON\nEMPTY=TITLE="Nothing"\nODD=TITLE="Odd" MATERIAL=NOSUCHSTUFF\n').getBytes("UTF-8"));
	WorldFiles.configure(root);
	var probs = s(OP.problems()), refs = s(CM.core.CrossRefs.errors());
	check("problems", probs.indexOf("EMPTY: no one can give to it") >= 0 && refs.indexOf("orgpools.ini: ODD/MATERIAL: no registered material NOSUCHSTUFF") >= 0
		&& refs.indexOf("orgpools.ini: ORE") < 0, probs + " / " + refs);
	var order = makeOrg("Fixp08 Order");
	var P = person("Fixp08 Pip"), Q = person("Fixp08 Quill");
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); P.setSession(VS); VS.setMob(P);
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
	order.addMember(P, G.getAcceptPos());
	CMLib.beanCounter().addMoney(P, 200);
	var had = money(P);

	// giving money: 5 a unit
	var gave = s(OP.give(P, order, "supplies", 3));
	check("gave-money", gave.indexOf("You give 3 unit(s) to Fixp08 Order's Supplies (receipt #1); it holds 3.") >= 0 && OP.units(order, "SUPPLIES") == 3 && money(P) == had - 15,
		gave + " / units " + OP.units(order, "SUPPLIES") + " / money " + money(P));
	// past its MAX: refused, nothing taken; not a member: refused
	var over = err(function () { OP.give(P, order, "SUPPLIES", 8); });
	check("max", over.indexOf("Supplies holds at most 10; it has 3") >= 0 && OP.units(order, "SUPPLIES") == 3 && money(P) == had - 15, over);
	var outsider = err(function () { OP.give(Q, order, "SUPPLIES", 1); });
	check("members-only", outsider.indexOf("only members give to Fixp08 Order's Supplies") >= 0, outsider);

	// giving a material: weight units of a plain pile, what is left kept
	var ore = CMLib.materials().makeItemResource(RM.RESOURCE_IRON); ore.basePhyStats().setWeight(5); ore.recoverPhyStats(); P.addItem(ore);
	gave = s(OP.give(P, order, "ore", 3));
	check("gave-material", gave.indexOf("(receipt #2); it holds 3") >= 0 && ore.basePhyStats().weight() == 2 && OP.units(order, "ORE") == 3, gave + " / weight " + ore.basePhyStats().weight());
	var short = err(function () { OP.give(P, order, "ORE", 5); });
	check("material-short", short.indexOf("need 5 weight unit(s) of plain IRON") >= 0 && ore.basePhyStats().weight() == 2 && OP.units(order, "ORE") == 3, short);

	// spending: a receipt; too little held: refused, nothing spent, no receipt
	var receipt = Number(OP.spend(order, "SUPPLIES", 2, "Fixp08 Pip", "a night's lodging"));
	check("spent", receipt == 3 && OP.units(order, "SUPPLIES") == 1, "receipt " + receipt + " units " + OP.units(order, "SUPPLIES"));
	var little = err(function () { OP.spend(order, "SUPPLIES", 5, "Fixp08 Pip", "a feast"); });
	var ledger = MD.parse(order.getStat("POOLLEDGER"));
	check("too-little", little.indexOf("Fixp08 Order's Supplies holds 1, not 5") >= 0 && OP.units(order, "SUPPLIES") == 1 && Number(ledger.get("serial")) == 3, little);

	// POOLS: the pools, their givers and users, the last entries
	var shown = s(OP.show(P));
	check("shown", shown.indexOf("Supplies: 1 of 10; given by Fixp08 Pip 3; used for Fixp08 Pip 2") >= 0 && shown.indexOf("Ore stock: 3 of 50; given by Fixp08 Pip 3") >= 0
		&& shown.indexOf("#3 Fixp08 Pip spent 2 supplies (a night's lodging)") >= 0 && shown.indexOf("Nothing") < 0, shown);
	var cmd = CMClass.getCommand("ServicesCmd");
	said(); cmd.execute(P, list("POOLS", "GIVE", "1", "SUPPLIES"), 0);
	var out = said();
	check("pools-give", out.indexOf("You give 1 unit(s) to Fixp08 Order's Supplies (receipt #4); it holds 2.") >= 0, out);
	cmd.execute(P, list("POOLS", "GIVE", "1", "NOPE"), 0);
	out = said();
	check("pools-unknown", out.indexOf("Pools: no shared pool NOPE in orgpools.ini") >= 0, out);
	cmd.execute(Q, list("POOLS"), 0);

	// the ledger keeps the last 20 entries; the totals keep everything
	for (var i = 0; i < 11; i++) { OP.give(P, order, "SUPPLIES", 1); OP.spend(order, "SUPPLIES", 1, "Fixp08 Quill", "a meal"); }
	ledger = MD.parse(order.getStat("POOLLEDGER"));
	var entries = MD.array(ledger.get("entries"), "entries");
	check("last-20", entries.length == 20 && Number(ledger.get("serial")) == 26 && Number(entries[0].get("receipt")) == 7
		&& Number(ledger.get("givers").get("SUPPLIES").get("Fixp08 Pip")) == 15 && Number(ledger.get("users").get("SUPPLIES").get("Fixp08 Quill")) == 11,
		"entries " + entries.length + " serial " + ledger.get("serial") + " first " + entries[0].get("receipt"));

	// saved with the organization
	var copy = CMClass.getCommon("DefaultClan"); copy.setDataXML(order.getDataXML());
	check("saved", s(copy.getStat("POOLS")) == s(order.getStat("POOLS")) && s(copy.getStat("POOLLEDGER")) == s(order.getStat("POOLLEDGER")) && OP.units(copy, "SUPPLIES") == 2,
		s(copy.getStat("POOLS")));
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	clans.forEach(function (c) { c.destroyClan(); });
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
