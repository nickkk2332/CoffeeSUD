// P6: world/origins.ini: origins a character may take (MASK, UNLOCK), the ORIGIN creation step after the class (then P7's KIT step), and what an origin gives.
FIXFILE = "p6-origins";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var OR = CM.core.Origins;
var OF = OR.file();
var had = OF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(OF.toPath()), "UTF-8")) : null;
var R0 = M.location();
var R = scratchRoom();
var hero = newMob("GenMob", R);
hero.setName("Fixp6hero");
hero.baseCharStats().setMyRace(CMClass.getRace("Human")); hero.recoverCharStats();
hero.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); hero.setSession(VS); VS.setMob(hero);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var book = CMClass.getItem("GenItem"); book.setName("a fixp6 prayer book"); CMLib.catalog().addCatalog(book);
var F = CMLib.factions().factions().nextElement();
var fid = s(F.factionID());
// the creation steps, as the login runs them
var CC = CMLib.login(), LSI = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function lget(name) { var f = LSI.getDeclaredField(name); f.setAccessible(true); return f.get(lo); }
function step(name) {
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
lset("mob", hero);
try {
	if (OF.isFile()) OF["delete"]();
	check("no file", OR.choices(hero).size() == 0 && step("charcrOriginStart") == null && s(lget("state")) == "CHARCR_KITSTART", s(lget("state")));
	write(OF, ["# test origins",
		"[FIXORPHAN]",
		"NAME=Fixp6 Temple Orphan",
		"DESCRIPTION=Raised by the priests of the temple.",
		"MASK=-RACE +Human +Half-Elf",
		"START=" + CMLib.map().getExtendedRoomID(R0),
		"ITEMS=2 a fixp6 prayer book, GenItem",
		"ABILITIES=Prayer_Bless 25",
		"FACTIONS=" + fid + " 100",
		"TATTOOS=FIXORPHANED",
		"MONEY=50",
		"SCRIPT=ONCE_PROG 100;mpechoat $i Fixp6 welcome home;~",
		"[FIXEXILE]",
		"NAME=Fixp6 Exile",
		"DESCRIPTION=Cast out, \\",
		"  and far from home.",
		"UNLOCK=FIXP6_EXILE",
		"[FIXDWARF]",
		"NAME=Fixp6 Deep Delver",
		"MASK=-RACE +Dwarf",
		""].join("\n"));
	var all = OR.all();
	check("read", all.size() == 3 && s(all.get(1).get("DESCRIPTION")) == "Cast out, and far from home.", all.size() + " " + (all.size() > 1 ? all.get(1).get("DESCRIPTION") : ""));
	var C = OR.choices(hero);
	check("mask and unlock", C.size() == 1 && s(C.get(0).id) == "FIXORPHAN", C.size());
	var acct = CMClass.getCommon("DefaultPlayerAccount"); hero.playerStats().setAccount(acct); acct.addTattoo("FIXP6_EXILE");
	C = OR.choices(hero);
	check("unlocked", C.size() == 2 && s(OR.find(C, "2").id) == "FIXEXILE" && s(OR.find(C, "fixp6 t").id) == "FIXORPHAN"
		&& OR.find(C, "fixdwarf") == null && OR.find(C, "9") == null, C.size());
	// the step
	said();
	var r1 = step("charcrOriginStart"), o1 = said();
	check("menu", s(r1) == "INPUT_REQUIRED" && s(lget("state")) == "CHARCR_ORIGINPICKED" && has(o1, "Fixp6 Temple Orphan") && has(o1, "Raised by the priests")
		&& has(o1, "Fixp6 Exile") && !has(o1, "Deep Delver"), o1.substring(0, 300));
	lset("lastInput", "nonsense");
	step("charcrOriginPicked"); var o2 = said();
	check("not one", s(lget("state")) == "CHARCR_ORIGINSTART" && has(o2, "is not one of the origins"), o2);
	lset("lastInput", "1");
	step("charcrOriginPicked");
	check("chosen", s(lget("state")) == "CHARCR_KITSTART" && hero.findTattoo("ORIGIN_FIXORPHAN") != null && s(OR.chosen(hero).id) == "FIXORPHAN", s(lget("state")));
	// what it gives
	var f0 = hero.fetchFaction(fid); if (f0 == java.lang.Integer.MAX_VALUE) f0 = 0;
	said(); var O = OR.apply(hero); var o3 = said();
	var books = 0; for (var i = 0; i < hero.numItems(); i++) if (has(hero.getItem(i).Name(), "fixp6 prayer book")) books++;
	var bless = hero.fetchAbility("Prayer_Bless");
	check("start", O != null && hero.getStartRoom() == R0, s(hero.getStartRoom()));
	check("items", books == 2 && hero.numItems() == 4, books + " " + hero.numItems()); // and the coins
	check("ability", bless != null && bless.proficiency() == 25, bless == null ? "none" : bless.proficiency());
	check("faction", hero.fetchFaction(fid) == f0 + 100, hero.fetchFaction(fid) + " " + f0);
	check("tattoo and money", hero.findTattoo("FIXORPHANED") != null && CMLib.beanCounter().getTotalAbsoluteNativeValue(hero) == 50, CMLib.beanCounter().getTotalAbsoluteNativeValue(hero));
	check("script", has(o3, "Fixp6 welcome home"), o3);
	check("help", has(CMLib.help().getHelpText("ORIGINS", M, true), "origins.ini"), "no help");
} finally {
	if (had != null) write(OF, had); else if (OF.isFile()) OF["delete"]();
	hero.setSession(null); hero.setPlayerStats(null); hero.destroy();
	CMLib.catalog().delCatalog(book);
}
done();
