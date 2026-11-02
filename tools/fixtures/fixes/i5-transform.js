// I5: Prop_Transform AFTER/DAYS, START, ONLYIN, RESET, MOB with KEEP=FOLLOW, INTO (a chain), SET with WHEN and REVERT,
// MOB with WHEN=NIGHT and REVERT; LEFT kept in the text.
FIXFILE = "i5-transform";
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
var TOD = CM.Common.interfaces.TimeClock.TimeOfDay;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
function catItem(name) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " is here."); I.recoverPhyStats(); CMLib.catalog().addCatalog(I); I.destroy(); }
function catMob(name) { var m = CMClass.getMOB("GenMob"); m.setName(name); m.setDisplayText(name + " is here."); m.recoverPhyStats(); CMLib.catalog().addCatalog(m); m.destroy(); }
catMob("a fixi5 wyrmling"); catMob("a fixi5 gargoyle"); catItem("a fixi5 young tree");
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi5pip");
function mobsNamed(n) { var l = []; for (var i = 0; i < R.numInhabitants(); i++) if (s(R.fetchInhabitant(i).Name()) == n) l.push(R.fetchInhabitant(i)); return l; }
function itemsNamed(n) { var l = []; for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == n) l.push(R.getItem(i)); return l; }
// an egg that hatches after it is carried a while
var egg = CMClass.getItem("GenItem"); egg.setName("a fixi5 dragon egg"); R.addItem(egg);
var P = CMClass.getAbility("Prop_Transform"); P.setMiscText("AFTER=3 START=GET ONLYIN=CARRIED RESET=true MOB=\"a fixi5 wyrmling\" KEEP=FOLLOW MSG=\"$p cracks open!\"");
egg.addNonUninvokableEffect(P);
P.tick(P, TPS); P.tick(P, TPS); P.tick(P, TPS); P.tick(P, TPS);
var lying = (!egg.amDestroyed()) && P.left() == 3;
V.moveItemTo(egg);
P.tick(P, TPS);
var kept = has(P.text(), "LEFT=2");
R.moveItemTo(egg); P.tick(P, TPS);
var reset = P.left() == 3;
V.moveItemTo(egg);
P.tick(P, TPS); P.tick(P, TPS); P.tick(P, TPS); P.tick(P, TPS);
var wyrm = mobsNamed("a fixi5 wyrmling");
check("hatch", lying && kept && reset && egg.amDestroyed() && wyrm.length == 1 && wyrm[0].amFollowing() == V,
	"lying " + lying + " kept " + kept + " reset " + reset + " gone " + egg.amDestroyed() + " wyrm " + wyrm.length + " follows " + (wyrm.length && wyrm[0].amFollowing()));
// a sapling into a tree, from the catalog
var sap = CMClass.getItem("GenItem"); sap.setName("a fixi5 sapling"); R.addItem(sap);
var PS2 = CMClass.getAbility("Prop_Transform"); PS2.setMiscText("AFTER=1 INTO=\"a fixi5 young tree\""); sap.addNonUninvokableEffect(PS2);
PS2.tick(PS2, TPS);
check("into", sap.amDestroyed() && itemsNamed("a fixi5 young tree").length == 1, "sapling " + sap.amDestroyed());
// by the clock: in place, and into a mob and back
var C = CMLib.time().localClock(R);
var hour = C.getHourOfDay();
var dd = C.getDawnToDusk();
function setTod(t) { for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); if (C.getTODCode() == t) return true; } return false; }
var lamp = CMClass.getItem("GenItem"); lamp.setName("a fixi5 moonstone"); R.addItem(lamp);
var PL = CMClass.getAbility("Prop_Transform"); PL.setMiscText("WHEN=NIGHT REVERT=true SET=\"NAME=\\\"a glowing fixi5 moonstone\\\"\""); lamp.addNonUninvokableEffect(PL);
var statue = CMClass.getItem("GenItem"); statue.setName("a fixi5 stone statue"); R.addItem(statue);
var PG = CMClass.getAbility("Prop_Transform"); PG.setMiscText("WHEN=NIGHT REVERT=true MOB=\"a fixi5 gargoyle\""); statue.addNonUninvokableEffect(PG);
setTod(TOD.DAY);
PL.tick(PL, TPS); PG.tick(PG, TPS);
var day = s(lamp.Name()) == "a fixi5 moonstone" && mobsNamed("a fixi5 gargoyle").length == 0;
setTod(TOD.NIGHT);
PL.tick(PL, TPS); PG.tick(PG, TPS);
var g = mobsNamed("a fixi5 gargoyle");
var night = s(lamp.Name()) == "a glowing fixi5 moonstone" && g.length == 1 && itemsNamed("a fixi5 stone statue").length == 0;
setTod(TOD.DAY);
PL.tick(PL, TPS);
if (g.length == 1) { var back = g[0].fetchEffect("Prop_Transform"); if (back != null) back.tick(back, TPS); }
var dawn = s(lamp.Name()) == "a fixi5 moonstone" && mobsNamed("a fixi5 gargoyle").length == 0 && itemsNamed("a fixi5 stone statue").length == 1;
C.setHourOfDay(hour);
check("clock", day && night && dawn, "day " + day + " night " + night + " back " + dawn + " lamp " + lamp.Name());
check("help", has(CMLib.help().getHelpText("PROP_TRANSFORM", M, true), "REVERT"), "no help");

V.destroy();
for (var i = R.numInhabitants() - 1; i >= 0; i--) R.fetchInhabitant(i).destroy();
["a fixi5 wyrmling", "a fixi5 gargoyle"].forEach(function (n) { var c = CMLib.catalog().getCatalogMob(n); if (c != null) CMLib.catalog().delCatalog(c); });
var ct = CMLib.catalog().getCatalogItem("a fixi5 young tree"); if (ct != null) CMLib.catalog().delCatalog(ct);
done();
