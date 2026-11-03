// CR14: Prop_Yield (HARVESTS, DROP, REGROW, EMPTYMSG) on a room or area; gathering records on game time, kept in the save (YIELDS).
FIXFILE = "cr14-yield";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, Y = CM.core.Yields, I = java.lang.Integer;
var X = scratchRoom(), X2 = scratchRoom(), X3 = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr14forager");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
var F = CMClass.getAbility("Foraging");
function harvest(R) { return call(F, "adjustYieldBasedOnRoomSpam", [I.valueOf(10), R]); }
function left(R) { return s(call(F, "checkIfAnyYield", [R])) == "true"; }
var P = null, PA = null, PR = null;
try {
	[X, X2, X3].forEach(function (R) { R.setResource(RM.RESOURCE_BLUEBERRIES); });
	// no Prop_Yield: as before, 20% less each time
	var plain = [harvest(X2), harvest(X2), harvest(X2)];
	check("plain", plain.join() == "10,8,6" && left(X2) && Y.harvests(X2) == 3 && call(F, "roomYieldLeft", [X2]) == 40, plain.join());
	// HARVESTS=3 DROP=0: three full harvests, then bare, with EMPTYMSG
	P = CMClass.getAbility("Prop_Yield");
	P.setMiscText("HARVESTS=3 DROP=0% REGROW=2 DAYS EMPTYMSG=\"The fixcr14 bushes are bare.\"");
	X.addNonUninvokableEffect(P);
	var got = [harvest(X), harvest(X), harvest(X)];
	var l3 = left(X);
	said();
	var l4 = left(X), msg = said(), h4 = harvest(X);
	check("bare", got.join() == "10,10,10" && !l3 && !l4 && has(msg, "The fixcr14 bushes are bare.") && h4 == 0 && call(F, "roomYieldLeft", [X]) == 0, got.join() + " / " + msg);
	// REGROW in game time: days of the room's calendar; a bare number is hours
	var C = X.getArea().getTimeObj(), hr = CM.core.CMProps.getMillisPerMudHour();
	var r1 = Y.parse("REGROW=2 DAYS", X).regrow, r2 = Y.parse("REGROW=5", X).regrow, r3 = Y.parse("HARVESTS=2 DROP=25%", X);
	check("regrow", r1 == 2 * C.getHoursInDay() * hr && r2 == 5 * hr && r3.harvests == 2 && r3.drop == 0.25 && r3.regrow < 0, r1 + " / " + r2 + " / " + r3.drop);
	// the save keeps it, with the time left; a record whose time is up has regrown (a room with an ID: the player's)
	var RR = M.location(); RR.setResource(RM.RESOURCE_BLUEBERRIES);
	PR = CMClass.getAbility("Prop_Yield"); PR.setMiscText("HARVESTS=3 DROP=0% REGROW=2 DAYS"); RR.addNonUninvokableEffect(PR);
	harvest(RR); harvest(RR); harvest(RR);
	var xml = s(Y.toXML());
	var rid = s(CMLib.map().getExtendedRoomID(RR));
	check("saved", has(xml, "<YIELDS>") && has(xml, "N=\"3\"") && rid.length > 0 && has(xml, rid), "[" + xml.substring(0, 160) + "] " + rid);
	Y.fromXML(null);
	var cleared = Y.harvests(RR);
	Y.fromXML(CMLib.xml().parseAllXML(xml).get(0));
	var back = Y.harvests(RR), bareAgain = !left(RR);
	var gone = xml.replace(/LEFT="\d+"/g, "LEFT=\"-1\"");
	Y.fromXML(CMLib.xml().parseAllXML(gone).get(0));
	check("restored", cleared == 0 && back == 3 && bareAgain && Y.harvests(RR) == 0 && left(RR), cleared + " " + back + " " + bareAgain + " " + Y.harvests(RR));
	// on the area: its rooms
	PA = CMClass.getAbility("Prop_Yield"); PA.setMiscText("HARVESTS=1"); X3.getArea().addNonUninvokableEffect(PA);
	var a1 = harvest(X3), a2 = harvest(X3);
	check("area", a1 == 10 && a2 == 0 && !left(X3), a1 + " " + a2);
	check("help", has(CMLib.help().getHelpText("PROP_YIELD", M, true), "HARVESTS"), "no help");
} finally {
	if (P != null) X.delEffect(P);
	if (PR != null) { M.location().delEffect(PR); M.location().setResource(-1); }
	if (PA != null) X3.getArea().delEffect(PA);
	Y.fromXML(null);
	V.setPlayerStats(null); V.destroy();
}
done();
