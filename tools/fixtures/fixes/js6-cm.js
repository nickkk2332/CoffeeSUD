// JS6: cm, importPackage, include(), debug(), sud and cm.on in every JavaScript scope.
FIXFILE = "js6-cm";
var R = M.location();
var A = R.getArea();
var JE = CM.core.JSEvents;
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
JE.clear();

// the scope: cm, cm.lib, importPackage, sud, include, debug
check("cm", typeof cm == "object" && cm.here() == R && cm.player() == M, "cm.here()=" + cm.here() + ", player " + cm.player());
check("lib", cm.lib.map().getRoom(R.roomID()) == R, "cm.lib.map()");
importPackage(Packages.com.planet_ink.coffee_mud.core.save);
check("importPackage", typeof SaveGame != "undefined" && SaveGame.isBuilderMode(), "SaveGame after importPackage");
check("debug", typeof debug == "function", "typeof debug " + typeof debug);
check("sud", sud.save.isBuilder() && typeof sud.time.isTurnBased() == "boolean" && sud.party.leader() == M && sud.party.isMember(M),
	"isBuilder " + sud.save.isBuilder() + ", leader " + sud.party.leader());

// finding
check("room", cm.room(R.roomID()) == R && sud.find.room(R.roomID()) == R, "cm.room");
check("area", cm.area() == A && cm.area(A.Name()) == A, "cm.area");
var baker = newMob("GenMob", R);
baker.setName("a fixjs6 baker");
check("findMob", cm.findMob("fixjs6 baker") == baker && cm.findMob("fixjs6 baker", R) == baker && sud.find.mob("fixjs6 baker") == baker
	&& sud.find.mob("fixjs6 baker", A) == baker, "findMob");
var loaf = CMClass.getItem("GenItem");
loaf.setName("a fixjs6 loaf");
R.addItem(loaf);
check("findItem", cm.findItem("fixjs6 loaf") == loaf && sud.find.item("fixjs6 loaf", R) == loaf, "findItem");

// dice, vars, stats
var d = cm.dice("3d6");
check("dice", d >= 3 && d <= 18 && cm.dice(2, 1, 1) == 3 && cm.chance(100) && !cm.chance(-1), "dice " + d);
clearVar("FIXJS6A");
cm.var("FIXTEST", "FIXJS6A", "abc");
check("var", gvar("FIXJS6A") == "abc" && s(cm.var("FIXTEST", "FIXJS6A")) == "abc" && sud.vars.get("FIXTEST", "FIXJS6A") == "abc", "FIXJS6A='" + gvar("FIXJS6A") + "'");
sud.vars.set("FIXTEST", "FIXJS6A", "def");
check("vars", gvar("FIXJS6A") == "def" && sud.vars.list("FIXTEST").contains("FIXJS6A"), "list " + sud.vars.list("FIXTEST"));
cm.var(baker, "mood", "cheerful");
check("objvar", s(cm.var(baker, "MOOD")) == "cheerful" && cm.vars(baker).contains("MOOD"), "@ var " + cm.var(baker, "MOOD") + ", " + cm.vars(baker));
check("stat", s(cm.stat(baker, "NAME")) == "a fixjs6 baker" && s(cm.stat(baker, "LEVEL")) == "5", "NAME " + cm.stat(baker, "NAME"));
clearVar("FIXJS6B");
var r = cm.mp("MPSETVAR FIXTEST FIXJS6B $n\nRETURN 7");
check("mp", gvar("FIXJS6B") == s(M.Name()) && s(r) == "7", "FIXJS6B='" + gvar("FIXJS6B") + "', returned '" + r + "'");

// a <SCRIPT> has cm (its host), sud and importPackage
clearVar("FIXJS6S");
var sm = scriptedMob("a fixjs6 scribe", "RAND_PROG 100\n<SCRIPT>\nimportPackage(Packages.com.planet_ink.coffee_mud.core.save);\n"
	+ "setVar('FIXTEST', 'FIXJS6S', cm.host().Name() + '|' + (typeof SaveGame) + '|' + (typeof sud.find.mob) + '|' + (cm.here() == cm.host().location()));\n"
	+ "</SCRIPT>\nmpdisable\n~\n");
tickMob(sm);
runQueued(sm.scripts().nextElement());
check("script", gvar("FIXJS6S") == "a fixjs6 scribe|function|function|true", "FIXJS6S='" + gvar("FIXJS6S") + "'");

// include(): resources/ or world:, compiled once while unchanged, no ..
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var IF = new java.io.File(new java.io.File("resources"), "fixjs6inc.js");
write(IF, "var fixInc = 41 + 1;\n");
include("fixjs6inc.js");
check("include", fixInc == 42, "fixInc " + (typeof fixInc));
var WI = new java.io.File(new java.io.File(WorldFiles.worldDir(), "js"), "fixjs6w.js");
write(WI, "var fixIncW = 'world';\n");
include("world:js/fixjs6w.js");
check("include-world", fixIncW == "world", "fixIncW " + (typeof fixIncW));
var bad = "";
try { include("../fixjs6inc.js"); } catch (e) { bad = s(e.message); }
check("include-path", bad.indexOf("no such file") >= 0, "'..': " + bad);
IF["delete"](); WI["delete"]();

// the clock and the weather
var c = cm.clock();
check("clock", c.hour() >= 0 && c.hour() < c.hoursInDay() && ["dawn", "day", "dusk", "night"].indexOf(s(c.daypart())) >= 0
	&& ["spring", "summer", "fall", "winter"].indexOf(s(c.season())) >= 0 && c.absHours() > 0 && s(c.moon()).length > 0,
	"hour " + c.hour() + ", " + c.daypart() + ", " + c.season() + ", moon " + c.moon());
clearVar("FIXJS6D");
cm.on("day", function(area, was, now) { cm.var("FIXTEST", "FIXJS6D", area.Name() + ":" + was + ">" + now); }, "fixjs6day");
JE.tick();
var h0 = c.absHours(), day0 = c.day();
sud.time.passTime(c.hoursInDay());
JE.tick();
check("passTime", cm.clock().absHours() == h0 + c.hoursInDay() && cm.clock().hour() == c.hour(), "absHours " + h0 + " > " + cm.clock().absHours());
check("on-day", gvar("FIXJS6D") == s(A.Name()) + ":" + day0 + ">" + cm.clock().day(), "FIXJS6D='" + gvar("FIXJS6D") + "'");
var w = cm.weather();
var to = (s(w.get()) == "rain") ? "clear" : "rain";
var from = s(w.get());
clearVar("FIXJS6W");
cm.on("weather", function(area, was, now) { cm.var("FIXTEST", "FIXJS6W", was + ">" + now); }, "fixjs6weather");
JE.tick();
check("weather-set", w.set(to) && s(w.get()) == to && !w.set("snowballs"), "weather " + w.get() + ", wanted " + to);
JE.tick();
check("on-weather", gvar("FIXJS6W") == from + ">" + to, "FIXJS6W='" + gvar("FIXJS6W") + "'");
check("forecast", w.setNext("clear") && s(w.next()) == "clear" && s(w.forecast()).length > 0 && s(w.describe()).length > 0, "next " + w.next());

// the game's course: listeners, keys, refusing a save
clearVar("FIXJS6P");
var onPause = function(who, detail) { cm.var("FIXTEST", "FIXJS6P", gvar("FIXJS6P") + "p"); };
cm.on("pause", onPause);
cm.on("pause", onPause);
SE.game("PAUSE", M, "");
check("on-pause", gvar("FIXJS6P") == "p" && cm.listening("pause").size() == 1, "FIXJS6P='" + gvar("FIXJS6P") + "', " + cm.listening("pause"));
cm.on("save", function(who, slot) { return false; }, "fixjs6nosave");
check("on-save", !SE.gameCheck(M, "fixjs6"), "a save listener returning false refuses");
check("off", cm.off("fixjs6nosave") == 1 && SE.gameCheck(M, "fixjs6"), "after cm.off the save goes on");
var badEvent = "";
try { cm.on("lunch", onPause); } catch (e) { badEvent = s(e.message); }
check("on-unknown", badEvent.indexOf("no event 'lunch'") >= 0, badEvent);

// sud.time.waitUntil: the test each tick until true, then the rest once
var n = 0, then = 0;
sud.time.waitUntil(function() { n++; return n >= 2; }, function() { then++; });
JE.tick(); JE.tick(); JE.tick();
check("waitUntil", n == 2 && then == 1 && cm.listening("tick").isEmpty(), "n " + n + ", then " + then + ", " + cm.listening("tick"));

// the law
check("no-law", cm.law() == null, "a lawless area");
var arrest = CMClass.getBehavior("Arrest");
A.addBehavior(arrest);
var L = cm.law();
check("law", L != null && L.crimes().size() > 0, "law " + L);
if (L != null) {
	// the law is for those with a session, as the player's characters
	var suspect = newMob("GenMob", R);
	suspect.setName("Fixjs6suspect");
	var SS = CMClass.getCommon("FakeSession");
	SS.initialize(null, "MEMORY");
	suspect.setSession(SS);
	SS.setMob(suspect);
	check("crimes", L.crimes().contains("ASSAULT"), "crimes " + L.crimes());
	check("accuse", L.accuse(suspect, "assault") && L.warrants(suspect).size() == 1 && !L.accuse(suspect, "fixjs6 jaywalking"), "warrants " + L.warrants(suspect));
	check("pardon", L.pardon(suspect, "ALL") == 1 && L.warrants(suspect).size() == 0, "pardon");
	suspect.destroy();
	CMLib.beanCounter().setMoney(baker, 50);
	check("fine", L.fine(baker, 20) && !L.fine(baker, 1000) && Math.round(CMLib.beanCounter().getTotalAbsoluteNativeValue(baker)) == 30,
		"money " + CMLib.beanCounter().getTotalAbsoluteNativeValue(baker));
}
A.delBehavior(arrest);

// a shop and a bank
check("no-shop", cm.shop(baker) == null && cm.bank(baker) == null, "a baker keeps neither");
var K = newMob("GenShopkeeper", R);
K.setName("Fixjs6merchant");
var cloak = CMClass.getItem("GenItem");
cloak.setName("a fixjs6 cloak");
cloak.setBaseValue(100);
cloak.recoverPhyStats();
K.getShop().addStoreInventory(cloak, 1, -1);
var shop = cm.shop(K);
check("shop", shop != null && shop.stock().size() == 1 && shop.find("cloak") != null && shop.price("cloak") > 0, "stock " + (shop && shop.stock()));
var bank = cm.bank(newMob("GenBanker", R));
check("bank", bank != null && bank.balance(M) == 0 && bank.items(null).isEmpty(), "bank " + bank);

JE.clear();
baker.destroy(); sm.destroy(); K.destroy(); loaf.destroy();
done();
