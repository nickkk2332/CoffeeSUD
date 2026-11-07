// P04 service records: a Prop_Service on an NPC lists its services (words, mask, hours, station, cost in money or from an
// organization's shared pool); TALK's menu offers only those one may have now and says why not the rest; SERVICES lists them all;
// asking for one (SAYTO it a word) is refused for the same reasons before anyone hears it, and otherwise paid, a pool's with a receipt.
FIXFILE = "p04-services";
var SV = CM.core.Services, OP = CM.core.OrgPools, Gs = CM.core.Guilds, CV = CM.core.Conversations, SM = CM.core.ScriptMenus;
var Files = java.nio.file.Files;
var root = Files.createTempDirectory("sud-p04-").toFile();
var originalWorld = WorldFiles.worldDir();
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function money(m) { return Number(CMLib.beanCounter().getTotalAbsoluteNativeValue(m)); }
var G = Gs.government("GUILD_NPC"), clans = [], made = [];
function makeOrg(name) { var C = CMClass.getCommon("DefaultClan"); C.setName(name); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.create(); clans.push(C); return C; }
var R = scratchRoom();
function person(name) { var m = CMClass.getMOB("StdMOB"); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.baseCharStats().setMyRace(CMClass.getRace("Human"));
	m.recoverCharStats(); m.bringToLife(R, true); made.push(m); return m; }
try {
	Files.write(new java.io.File(root, "orgpools.ini").toPath(), new java.lang.String('SUPPLIES=TITLE="Supplies" MAX=10 MONEY=5\n').getBytes("UTF-8"));
	WorldFiles.configure(root);
	var order = makeOrg("Fixp04 Order");
	var P = person("Fixp04 Pip"), ann = person("Fixp04 Ann");
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); P.setSession(VS); VS.setMob(P);
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	order.addMember(P, G.getAcceptPos());
	CMLib.beanCounter().addMoney(P, 100);
	OP.give(P, order, "SUPPLIES", 3);
	var healer = newMob("GenMob", R); healer.setName("Fixp04 Healer"); made.push(healer);
	var clock = CMLib.time().localClock(healer), H = clock.getHoursInDay(), h = clock.getHourOfDay();
	var shut = (h + 1) % H, opens = (h + 2) % H;
	var A = CMClass.getAbility("Prop_Service");
	A.setMiscText(JSON.stringify({ services: [
		{ id: "heal", title: "Healing", words: ["heal", "mend wounds"], cost: { money: 10 } },
		{ id: "bless", title: "A blessing", words: ["bless"], mask: '-NAMES "+Fixp04 Ann"', refusal: "the healer blesses only the faithful" },
		{ id: "lodging", title: "Lodging", words: ["lodge"], cost: { pool: "SUPPLIES", units: 2, org: "Fixp04 Order" } },
		{ id: "watch", title: "Night watch", words: ["watch"], hours: shut + "-" + opens },
		{ id: "forge", title: "Forging", words: ["forge"], station: "anvil" }] }));
	healer.addNonUninvokableEffect(A);
	check("readable", s(A.accountForYourself()).indexOf("Offers services") >= 0 && SV.of(healer).size() == 5, s(A.accountForYourself()));
	check("problems", s(SV.problem('{"services":[{"id":"x","words":[]}]}')).indexOf("x: a service is asked for by at least one word") >= 0
		&& s(SV.problem('{"services":[{"id":"y","words":["a"],"hours":"late"}]}')).indexOf("hours are from-to") >= 0, s(SV.problem('{"services":[{"id":"x","words":[]}]}')));

	// one record, read the same way everywhere: SERVICES lists all of them with why not
	var listed = s(SV.list(P)).replace(/\s+/g, " ");
	check("listed", listed.indexOf("Fixp04 Healer:") >= 0 && /Healing - say heal; [^;]+; you may/.test(listed)
		&& listed.indexOf("A blessing - say bless; not now: the healer blesses only the faithful") >= 0
		&& listed.indexOf("Lodging - say lodge; 2 from Fixp04 Order's supplies; you may") >= 0
		&& listed.indexOf("Night watch - say watch; open " + shut + "-" + opens + "; not now: closed until " + shut + " o'clock") >= 0
		&& listed.indexOf("Forging - say forge; not now: Fixp04 Healer works only at the anvil") >= 0, listed);
	// TALK's menu: only what one may have now, the rest under "Not now"
	var keys = []; var menu = SV.menu(P, healer); for (var i = 0; i < menu.size(); i++) keys.push(s(menu.get(i)[0]));
	check("menu", keys.join(",") == "SVC_HEAL,SVC_LODGING" && s(menu.get(0)[2]) == 'SAYTO "Fixp04 Healer" heal', keys.join(","));
	var annKeys = []; menu = SV.menu(ann, healer); for (var i = 0; i < menu.size(); i++) annKeys.push(s(menu.get(i)[0]));
	check("menu-mask", annKeys.indexOf("SVC_BLESS") >= 0 && annKeys.indexOf("SVC_LODGING") < 0, annKeys.join(","));
	said();
	CV.offerServices(P, healer);
	var talk = said();
	check("talk", talk.indexOf("Fixp04 Healer can help you with:") >= 0 && talk.indexOf("Healing") >= 0
		&& talk.indexOf("Not now: A blessing: the healer blesses only the faithful; Night watch: closed until") >= 0 && talk.indexOf("Forging: Fixp04 Healer works only at the anvil") >= 0, talk);
	SM.cancel(P);
	said(); CMClass.getCommand("ServicesCmd").execute(P, list("SERVICES"), 0);
	check("services-command", said().indexOf("Fixp04 Healer:") >= 0, "SERVICES listed nothing");

	// asking: refused before anyone hears it, for the same reason
	CMLib.commands().postSay(P, healer, "bless me", false, false);
	var out = said();
	check("refused", out.indexOf("A blessing: not now, the healer blesses only the faithful.") >= 0 && out.indexOf("bless me") < 0, out);
	// asking for one one may have: paid
	var had = money(P);
	CMLib.commands().postSay(P, healer, "please mend wounds", false, false);
	out = said();
	check("paid-money", money(P) == had - 10 && out.indexOf("for Healing.") >= 0 && out.indexOf("mend wounds") >= 0, out + " / money " + money(P) + " was " + had);
	// words said to the room ask no one
	CMLib.commands().postSay(P, null, "heal", false, false);
	check("room-not-asked", money(P) == had - 10, "saying it to the room paid " + (had - 10 - money(P)));
	// from the organization's pool, with a receipt; then too little: refused, nothing spent
	CMLib.commands().postSay(P, healer, "lodge", false, false);
	out = said();
	check("paid-pool", OP.units(order, "SUPPLIES") == 1 && out.indexOf("Fixp04 Order's supplies pays 2 for Lodging (receipt #2).") >= 0, out);
	CMLib.commands().postSay(P, healer, "lodge", false, false);
	out = said();
	check("pool-short", OP.units(order, "SUPPLIES") == 1 && out.indexOf("Lodging: not now, Fixp04 Order's supplies holds too little.") >= 0, out);
	// money short: refused
	CMLib.beanCounter().subtractMoney(P, money(P));
	CMLib.commands().postSay(P, healer, "heal", false, false);
	out = said();
	check("money-short", out.indexOf("Healing: not now, it costs") >= 0 && money(P) == 0, out);

	// a record that cannot be read offers nothing, and says why
	A.setMiscText('{"services":[{"id":"bad"}]}');
	check("unreadable", SV.of(healer).size() == 0 && s(A.accountForYourself()).indexOf("cannot be read") >= 0, s(A.accountForYourself()));
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	clans.forEach(function (c) { c.destroyClan(); });
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown);
	var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
