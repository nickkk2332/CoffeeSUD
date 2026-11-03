// CR11: ResourceOverride by the calendar: SPRING=/SUMMER=/AUTUMN=/WINTER= lists, SEASON=/MONTH=/HOUR= limits (the room's own resource back outside them), MSG= on a change.
FIXFILE = "cr11-seasons";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, TICK = CM.core.interfaces.Tickable.TICKID_ROOM_BEHAVIOR;
var R = scratchRoom(), R2 = scratchRoom(), A = R.getArea(), oldC = A.getTimeObj();
var C = CMClass.getCommon("DefaultTimeClock");
var V = newMob("GenMob", R);
V.setName("Fixcr11gardener");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
function monthOf(season) { for (var m = 0; m <= C.getMonthsInYear(); m++) { C.setMonth(m); if (s(C.getSeasonCode().name()) == season) return m; } return -1; }
function at(season, hour) { C.setMonth(monthOf(season)); if (hour != null) C.setHourOfDay(hour); }
var B = null, B2 = null;
try {
	A.setTimeObj(C);
	var own2 = R2.myResource();
	B = CMClass.getBehavior("ResourceOverride");
	B.setParms("MIN=1 MAX=1 CHANCE=100 SPRING=oak SUMMER=pine,pine AUTUMN=apples WINTER=none MSG=\"The fixcr11 grove turns.\"");
	R.addBehavior(B);
	var got = {};
	["SPRING", "SUMMER", "FALL", "WINTER", "SPRING"].forEach(function (sea, i) { at(sea); said(); B.tick(R, TICK); got[sea + i] = [R.myResource(), said()]; });
	check("seasons", got.SPRING0[0] == RM.RESOURCE_OAK && got.SUMMER1[0] == RM.RESOURCE_PINE && got.FALL2[0] == RM.RESOURCE_APPLES && got.WINTER3[0] == 0 && got.SPRING4[0] == RM.RESOURCE_OAK,
		[got.SPRING0[0], got.SUMMER1[0], got.FALL2[0], got.WINTER3[0]].join());
	check("message", !has(got.SPRING0[1], "fixcr11 grove") && has(got.SUMMER1[1], "The fixcr11 grove turns.") && has(got.WINTER3[1], "The fixcr11 grove turns."), got.SPRING0[1] + " / " + got.SUMMER1[1]);
	// SEASON= and HOUR= limits: the room's own resource outside them
	B2 = CMClass.getBehavior("ResourceOverride");
	B2.setParms("MIN=1 MAX=1 CHANCE=100 pine SEASON=summer HOUR=6-18");
	R2.addBehavior(B2);
	at("SUMMER", 10); B2.tick(R2, TICK); var r1 = R2.myResource();
	at("SUMMER", 22); B2.tick(R2, TICK); var r2 = R2.myResource();
	at("WINTER", 10); B2.tick(R2, TICK); var r3 = R2.myResource();
	at("SUMMER", 7); B2.tick(R2, TICK); var r4 = R2.myResource();
	check("limits", r1 == RM.RESOURCE_PINE && r2 == own2 && r3 == own2 && r4 == RM.RESOURCE_PINE, [r1, r2, r3, r4, own2].join());
	// MONTH=
	var B3 = CMClass.getBehavior("ResourceOverride"); B3.setParms("pine MONTH=" + monthOf("WINTER"));
	C.setMonth(monthOf("WINTER")); var a3 = call(B3, "active", [C]);
	C.setMonth(monthOf("SUMMER")); var b3 = call(B3, "active", [C]);
	check("month", a3 != null && a3.size() == 1 && b3 == null, s(a3) + " / " + s(b3));
	check("help", has(CMLib.help().getHelpText("BEHAVIOR_RESOURCEOVERRIDE", M, true), "WINTER="), "no help");
} finally {
	A.setTimeObj(oldC);
	if (B != null) R.delBehavior(B);
	if (B2 != null) R2.delBehavior(B2);
	R.setResource(-1); R2.setResource(-1);
	V.setPlayerStats(null); V.destroy();
}
done();
