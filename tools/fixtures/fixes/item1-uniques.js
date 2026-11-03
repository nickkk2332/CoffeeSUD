// Item 1: unique and rare catalog items: a container rule (on opening, on a timer), every copy counted (made,
// from a save, carried in), unique at a time, once ever or for a while, several rules sharing the cap.
FIXFILE = "item1-uniques";
var U = CM.core.Uniques;
var CS = CM.Libraries.interfaces.CatalogLibrary.CataSpawn;
var GT = CM.core.GameTime;
var R = M.location();
function s(x) { return "" + x; }
function entry(cls, name) {
	var P = CMClass.getItem(cls);
	P.setName(name);
	P.recoverPhyStats();
	CMLib.catalog().addCatalog(P);
	P.destroy(); // the original stays a counted copy otherwise
	return CMLib.catalog().getCatalogItemData(name);
}
function box(name) {
	var C = CMClass.getItem("GenContainer");
	C.setName(name);
	C.setCapacity(500);
	C.setDoorsNLocks(true, false, true, false, false, false);
	R.addItem(C);
	return C;
}
function open(C) {
	var msg = CMClass.getMsg(M, C, null, CMMsg.MSG_OPEN, "<S-NAME> open(s) <T-NAME>.");
	if (R.okMessage(M, msg)) R.send(M, msg);
	return C.isOpen();
}
function shut(C) { C.setDoorsNLocks(true, false, true, false, false, false); }
function inside(C, name) {
	var n = 0;
	for (var i = 0; i < R.numItems(); i++) {
		var I = R.getItem(i);
		if (I.container() == C && s(I.Name()) == name && !I.amDestroyed()) n++;
	}
	return n;
}
function oldLost(name, days) {
	// as if the lost copy was lost DAYS game days ago
	var ms = CMProps.getMillisPerMudHour() * CMLib.time().globalClock().getHoursInDay() * days;
	var x = s(U.toXML()).replace(new RegExp('(NAME="' + name.toUpperCase() + '"[^/]*LOST=")([0-9]+)"'), function (m, a, b) { return a + (Number(b) - ms) + '"'; });
	U.fromXML(CMLib.xml().parseAllXML(x).get(0));
}
var CMProps = CM.core.CMProps;

// the rules as written
var why = new java.lang.StringBuilder();
var r1 = U.parse("CONTAINER 5% TIMER -NAMES \"+an iron chest\"", why), r2 = U.parse("drop 100% -NAMES +chieftain", null), r3 = U.parse("CONT 50%", null);
var bad = U.parse("FOO 5%", why), bad2 = U.parse("ROOM often", null);
check("parse", r1 != null && r1.spawn == CS.CONTAINER && Math.abs(r1.rate - 0.05) < 1e-9 && s(r1.when) == "TIMER" && s(r1.maskStr) == "-NAMES \"+an iron chest\""
	&& r2.spawn == CS.DROP && r2.rate == 1.0 && s(r2.maskStr) == "-NAMES +chieftain" && r3.spawn == CS.CONTAINER && s(r3.when) == "OPEN" && r3.mask == null
	&& bad == null && bad2 == null && s(why).indexOf("FOO") >= 0 && r1.onTimer() && !r1.onOpen() && r3.onOpen() && !r3.onTimer(),
	"r1 " + (r1 && r1.text()) + " r2 " + (r2 && r2.text()) + " r3 " + (r3 && r3.text()) + " why " + why);

// the catalog data keeps it, and old data reads as before
var D0 = CMLib.catalog().sampleCataData("");
D0.setSpawn(CS.CONTAINER); D0.setRate(0.25); D0.setCap(2); D0.setMaskStr("-NAMES \"+a chest\""); D0.setWhen("B"); D0.setUnique("W"); D0.setUniqueDays(3);
var rl = new java.util.ArrayList(); rl.add("DROP 100% -NAMES +chieftain"); rl.add("ROOM 1% -AREAS +\"<Midgaard>\""); D0.setRules(rl);
var xml = s(D0.data("a thing")), D1 = CMLib.catalog().sampleCataData(xml);
var old = CMLib.catalog().sampleCataData("<CATALOGDATA CATAGORY=\"\"><RATE>10%</RATE><LMASK></LMASK><SPAWN>ROOM</SPAWN><CAP>3</CAP></CATALOGDATA>");
check("data", D1.getSpawn() == CS.CONTAINER && s(D1.getWhen()) == "BOTH" && s(D1.getUnique()) == "WHILE" && D1.getUniqueDays() == 3 && D1.getCap() == 2
	&& D1.getRules().size() == 2 && s(D1.getRules().get(1)) == "ROOM 1% -AREAS +\"<Midgaard>\"" && U.rules(D1).size() == 3
	&& s(old.getUnique()) == "TIME" && s(old.getWhen()) == "OPEN" && old.getRules().size() == 0 && old.getSpawn() == CS.ROOM && xml.indexOf("<UNIQUE>WHILE</UNIQUE>") > 0
	&& s(old.data(null)).indexOf("UNIQUE") < 0, xml + " -> " + D1.getRules() + " / " + old.data(null));

// a container rule on opening, 100%, cap 1, once ever
var SD = entry("GenWeapon", "a fixi1 sunblade");
SD.setSpawn(CS.CONTAINER); SD.setRate(1.0); SD.setCap(1); SD.setWhen("OPEN"); SD.setMaskStr("-NAMES \"+a fixi1 iron chest\""); SD.setUnique("ONCE");
var c1 = box("a fixi1 iron chest"), c2 = box("a fixi1 iron chest"), wood = box("a fixi1 wooden box");
var o1 = open(c1), in1 = inside(c1, "a fixi1 sunblade");
check("open", o1 && in1 == 1 && U.live(SD, null) == 1 && U.ever("a fixi1 sunblade") == 1 && U.wasRolled(c1), "opened " + o1 + " inside " + in1 + " live " + U.live(SD, null) + " ever " + U.ever("a fixi1 sunblade"));
open(c2); open(wood);
check("second", inside(c2, "a fixi1 sunblade") == 0 && inside(wood, "a fixi1 sunblade") == 0 && U.live(SD, null) == 1 && U.wasRolled(c2) && U.wasRolled(wood),
	"second chest " + inside(c2, "a fixi1 sunblade") + " box " + inside(wood, "a fixi1 sunblade") + " live " + U.live(SD, null));

// a container rolled stays rolled until its contents reset
var PD = entry("GenItem", "a fixi1 pebble");
PD.setSpawn(CS.CONTAINER); PD.setRate(1.0); PD.setCap(5); PD.setMaskStr("-NAMES \"+a fixi1 urn\"");
var urn = box("a fixi1 urn");
open(urn); shut(urn); open(urn);
var p1 = inside(urn, "a fixi1 pebble");
U.refilled(urn); shut(urn); open(urn);
check("rolled", p1 == 1 && inside(urn, "a fixi1 pebble") == 2 && inside(urn, "a fixi1 sunblade") == 0, "pebbles " + p1 + " then " + inside(urn, "a fixi1 pebble"));

// a save and a restore: the copy from the save is counted, the chest stays rolled, and no second comes
var WS = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState");
var blade = null;
for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == "a fixi1 sunblade") blade = R.getItem(i);
var list = new java.util.ArrayList(); list.add(c1); list.add(blade);
var saved = s(callTyped(null, WS, "itemsXML", ["List"], [list])), kept = s(U.toXML());
blade.destroy(); c1.destroy();
var back = field(callTyped(null, WS, "unpackItems", ["XMLTag"], [CMLib.xml().parseAllXML("<THINGS>" + saved + "</THINGS>").get(0)]), "items");
for (var i = 0; i < back.size(); i++) R.addItem(back.get(i));
U.fromXML(CMLib.xml().parseAllXML(kept).get(0));
var c1b = back.get(0), bladeb = back.get(1);
var c3 = box("a fixi1 iron chest");
open(c3); shut(c1b); open(c1b);
check("restore", saved.indexOf("<ROLLED>true</ROLLED>") > 0 && kept.indexOf("A FIXI1 SUNBLADE") > 0 && U.wasRolled(c1b) && bladeb.container() == c1b
	&& CMLib.flags().isCataloged(bladeb) && U.live(SD, null) == 1 && U.ever("a fixi1 sunblade") == 1 && inside(c3, "a fixi1 sunblade") == 0 && inside(c1b, "a fixi1 sunblade") == 1,
	"live " + U.live(SD, null) + " ever " + U.ever("a fixi1 sunblade") + " third " + inside(c3, "a fixi1 sunblade") + " kept " + kept + " saved " + saved.substring(0, 200));

// destroyed, a once-ever item does not come back
bladeb.destroy();
var c4 = box("a fixi1 iron chest");
open(c4);
check("once", inside(c4, "a fixi1 sunblade") == 0 && U.live(SD, null) == 0 && U.ever("a fixi1 sunblade") == 1 && CM.core.ScriptLib.catalogFull(CMLib.catalog().getCatalogItem("a fixi1 sunblade")),
	"in the fourth chest " + inside(c4, "a fixi1 sunblade") + " ever " + U.ever("a fixi1 sunblade"));

// once for a while: not before its time, then again
SD.setUnique("WHILE"); SD.setUniqueDays(2);
var c5 = box("a fixi1 iron chest");
open(c5);
var early = inside(c5, "a fixi1 sunblade"), lostNow = U.lost("a fixi1 sunblade"), until = U.daysUntil("a fixi1 sunblade", SD);
oldLost("a fixi1 sunblade", 3);
var c6 = box("a fixi1 iron chest");
open(c6);
check("while", early == 0 && lostNow == 1 && until == 2 && inside(c6, "a fixi1 sunblade") == 1 && U.ever("a fixi1 sunblade") == 1 && U.lost("a fixi1 sunblade") == 0 && U.live(SD, null) == 1,
	"early " + early + " lost " + lostNow + " until " + until + " later " + inside(c6, "a fixi1 sunblade") + " ever " + U.ever("a fixi1 sunblade") + " lost " + U.lost("a fixi1 sunblade"));

// at a time: one lost comes again at once
var GD = entry("GenItem", "a fixi1 garnet");
GD.setSpawn(CS.CONTAINER); GD.setRate(1.0); GD.setCap(1); GD.setMaskStr("-NAMES \"+a fixi1 casket\"");
var k1 = box("a fixi1 casket"), k2 = box("a fixi1 casket");
open(k1);
var g1 = inside(k1, "a fixi1 garnet");
for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == "a fixi1 garnet") R.getItem(i).destroy();
open(k2);
check("time", g1 == 1 && inside(k2, "a fixi1 garnet") == 1 && U.ever("a fixi1 garnet") == 0, "first " + g1 + " second " + inside(k2, "a fixi1 garnet"));

// several rules sharing the cap: a drop from the chieftain, or found in an urn
var FD = entry("GenItem", "a fixi1 fang");
FD.setCap(2);
var fr = new java.util.ArrayList(); fr.add("DROP 100% -NAMES \"+a fixi1 chieftain\""); fr.add("CONTAINER 100% OPEN -NAMES \"+a fixi1 jar\""); FD.setRules(fr);
var chief = CMClass.getMOB("GenMob"); chief.setName("a fixi1 chieftain"); chief.recoverPhyStats();
var other = CMClass.getMOB("GenMob"); other.setName("a fixi1 villager"); other.recoverPhyStats();
var d1 = CMLib.catalog().getDropItem(chief, false), dOther = CMLib.catalog().getDropItem(other, false);
var jar = box("a fixi1 jar");
open(jar);
var d2 = CMLib.catalog().getDropItem(chief, false);
check("rules", d1 != null && s(d1.Name()) == "a fixi1 fang" && dOther == null && inside(jar, "a fixi1 fang") == 1 && d2 == null && U.live(FD, null) == 2,
	"drop " + (d1 && d1.Name()) + " other " + dOther + " jar " + inside(jar, "a fixi1 fang") + " again " + d2 + " live " + U.live(FD, null));
if (d1 != null) d1.destroy();

// on a timer, into a random matching container in the world
var TD = entry("GenItem", "a fixi1 ruby");
TD.setSpawn(CS.CONTAINER); TD.setRate(1.0); TD.setCap(1); TD.setWhen("TIMER"); TD.setMaskStr("-NAMES \"+a fixi1 coffer\"");
var cof = box("a fixi1 coffer");
U.timer("a fixi1 ruby", TD);
var t1 = inside(cof, "a fixi1 ruby");
U.timer("a fixi1 ruby", TD);
var cof2 = box("a fixi1 coffer");
open(cof2);
check("timer", t1 == 1 && inside(cof, "a fixi1 ruby") == 1 && inside(cof2, "a fixi1 ruby") == 0 && U.live(TD, null) == 1, "first " + t1 + " then " + inside(cof, "a fixi1 ruby") + " opened " + inside(cof2, "a fixi1 ruby"));

// a mob coming back to life brings no copy past the cap
var CD = entry("GenItem", "a fixi1 crown");
CD.setCap(1);
var crown1 = CM.core.ScriptLib.catalogCopy(CMLib.catalog().getCatalogItem("a fixi1 crown"), false);
R.addItem(crown1);
var king = CMClass.getMOB("GenMob"); king.setName("a fixi1 king"); king.recoverPhyStats();
var crown2 = CMLib.catalog().getCatalogItem("a fixi1 crown").copyOf(); CMLib.catalog().changeCatalogUsage(crown2, true);
king.addItem(crown2);
king.bringToLife(R, true);
var kept2 = king.findItem("a fixi1 crown");
crown1.destroy();
var queen = CMClass.getMOB("GenMob"); queen.setName("a fixi1 queen"); queen.recoverPhyStats();
var crown3 = CMLib.catalog().getCatalogItem("a fixi1 crown").copyOf(); CMLib.catalog().changeCatalogUsage(crown3, true);
queen.addItem(crown3);
queen.bringToLife(R, true);
check("revive", kept2 == null && crown2.amDestroyed() && queen.findItem("a fixi1 crown") != null && U.live(CD, null) == 1 && CM.core.ScriptLib.catalogCount(CMLib.catalog().getCatalogItem("a fixi1 crown")) == 1,
	"king kept " + kept2 + " destroyed " + crown2.amDestroyed() + " queen " + queen.findItem("a fixi1 crown") + " live " + U.live(CD, null));

// a copy of a copy is counted as the copy
var GA = entry("GenItem", "a fixi1 amulet");
var a1 = CMLib.catalog().getCatalogItem("a fixi1 amulet").copyOf(); CMLib.catalog().changeCatalogUsage(a1, true);
var a2 = a1.copyOf();
check("copy", GA.isReference(a2) && GA.isReference(a1), "copy counted " + GA.isReference(a2) + " original " + GA.isReference(a1));

// the description CATALOG EDIT shows
var desc = s(U.describe("a fixi1 sunblade", SD));
check("describe", desc.indexOf("Once for a while") >= 0 && desc.indexOf("CONTAINER") >= 0 && s(U.describe("a fixi1 garnet", GD)).indexOf("At most 1 at a time") >= 0, desc);

// clean up
[c1b, c2, c3, c4, c5, c6, wood, urn, k1, k2, jar, cof, cof2].forEach(function (C) { C.destroy(); });
king.destroy(); queen.destroy(); chief.destroy(); other.destroy(); a1.destroy(); a2.destroy();
for (var i = R.numItems() - 1; i >= 0; i--) if (s(R.getItem(i).Name()).indexOf("fixi1") >= 0) R.getItem(i).destroy();
["a fixi1 sunblade", "a fixi1 pebble", "a fixi1 garnet", "a fixi1 fang", "a fixi1 ruby", "a fixi1 crown", "a fixi1 amulet"].forEach(function (n) {
	CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem(n)); U.forget(n);
});
done();
