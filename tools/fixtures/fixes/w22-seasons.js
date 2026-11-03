// W22: a calendar's SEASONS (named, counting as a built-in one), SEASONOFFSET (an area's own), DAYLIGHT by season, MOONS; ISSEASON, ISMOON, +SEASON, VARIES and TIME with them.
FIXFILE = "w22-seasons";
var Cal = CM.core.Calendars;
var PS = CM.Common.interfaces.PhyStats;
var TC = CM.Common.interfaces.TimeClock;
var Climate = CM.Common.interfaces.Climate;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var G = CMLib.time().globalClock();
var DB = CMLib.database();
function area(name) {
	var A = CMClass.getAreaType("StdArea"); A.setName(name);
	A.basePhyStats().setDisposition(A.basePhyStats().disposition() | PS.IS_UNSAVABLE); A.recoverPhyStats();
	CMLib.map().addArea(A);
	return A;
}
function room(A, id, name) { var r = CMClass.getLocale("Plains"); r.setRoomID(id); r.setDisplayText(name); r.setArea(A); return r; }
var X = area("Fixw22Desert"), Y = area("Fixw22South");
X.addChild(Y); Y.addParent(X);
DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
var RX = room(X, "Fixw22Desert#0", "Fixw22 dunes"), RY = room(Y, "Fixw22South#0", "Fixw22 marsh");
var V = CMClass.getMOB("GenMob"); V.setName("Fixw22pip"); V.bringToLife(RX, true);
try {
	var bad = Cal.parse("SEASONS=\"x:HOT:1,ok:SUMMER:1\" DAYLIGHT=\"WINTER:a-b\" MOONS=\"Luna:0\"");
	check("problems", bad.problems.size() == 3 && bad.seasons.size() == 1, "problems " + bad.problems);
	X.addBlurbFlag("CALENDAR HOURS=6 DAYS=10 MONTHS=\"Ash,Cinder,Flood,Silt\" DAWNHR=0 DAYHR=1 DUSKHR=4 NIGHTHR=5"
		+ " SEASONS=\"the Burn:SUMMER:1-2,the Rains:SPRING:Flood-Silt\" DAYLIGHT=\"the Burn:0-5,the Rains:2-4\" MOONS=\"Selune:24,Ember:9:3\" START=1/1/8/0");
	Y.addBlurbFlag("CALENDAR SEASONOFFSET=2");
	Cal.applyAll();
	var C = X.getTimeObj();
	// the Burn, in Ash and Cinder, counts as summer; the southern land, two months on, is in the Rains, on the same clock
	var burn = Cal.season(X), south = Cal.season(Y);
	check("seasons", C != G && Y.getTimeObj() == C && s(burn.name) == "the Burn" && burn.counts == TC.Season.SUMMER && C.getSeasonCode() == TC.Season.SUMMER
		&& s(south.name) == "the Rains" && south.counts == TC.Season.SPRING, "clock " + (C == G ? "global" : "own") + " shared " + (Y.getTimeObj() == C) + " " + burn.name + "/" + burn.counts + " south " + south.name);
	// the day's length by season: the Rains' dawn at 2 and night at 4
	C.setHourOfDay(1); var burnAt1 = C.getTODCode();
	C.setMonth(2);
	var rains = Cal.season(X);
	C.setHourOfDay(1); var rainsAt1 = C.getTODCode();
	C.setHourOfDay(3); var rainsAt3 = C.getTODCode();
	check("daylight", burnAt1 == TC.TimeOfDay.DAY && s(rains.name) == "the Rains" && C.getSeasonCode() == TC.Season.SPRING && rainsAt1 == TC.TimeOfDay.NIGHT && rainsAt3 == TC.TimeOfDay.DAY,
		"burn@1 " + burnAt1 + " rains@1 " + rainsAt1 + " rains@3 " + rainsAt3);
	C.setMonth(0); C.setDayOfMonth(8); C.setHourOfDay(0);
	// moons: on the 8th of Ash, year 1, Ember (9 days, 3 on) is full and Selune (24 days) a waning crescent; the first is the moon
	var ember = Cal.moon(C, "Ember"), selune = Cal.moon(C, "sel");
	check("moons", Cal.phase(C, ember, null) == TC.MoonPhase.FULL && Cal.phase(C, selune, null) == TC.MoonPhase.WANECRESCENT && C.getMoonPhase(null) == TC.MoonPhase.WANECRESCENT,
		"ember " + Cal.phase(C, ember, null) + " selune " + Cal.phase(C, selune, null) + " moon " + C.getMoonPhase(null));
	// scripts
	["W22A", "W22B", "W22C", "W22D", "W22E"].forEach(function (v) { clearVar(v); });
	var m = newMob("GenMob", RX); m.setName("Fixw22priest");
	scriptOn(m, "RAND_PROG 100\nif ismoon(Ember full)\n mpsetvar FIXTEST W22A yes\nendif\nif ismoon(Selune full)\n mpsetvar FIXTEST W22B yes\nendif\n"
		+ "if isseason(the Burn)\n mpsetvar FIXTEST W22C yes\nendif\nif isseason(summer)\n mpsetvar FIXTEST W22D yes\nendif\nif isseason(the Rains)\n mpsetvar FIXTEST W22E yes\nendif\n~\n");
	tickMob(m); m.destroy();
	check("scripts", gvar("W22A") == "yes" && gvar("W22B") == "" && gvar("W22C") == "yes" && gvar("W22D") == "yes" && gvar("W22E") == "",
		"ember " + gvar("W22A") + " selune " + gvar("W22B") + " burn " + gvar("W22C") + " summer " + gvar("W22D") + " rains " + gvar("W22E"));
	// masks: a season by its own name, the area's offset on
	var MK = CMLib.masking();
	var inBurn = MK.maskCheck("-SEASON \"+the Burn\"", V, true), notRains = MK.maskCheck("-SEASON \"+the Rains\"", V, true), notBurn = MK.maskCheck("+SEASON \"-the Burn\"", V, true);
	V.location().delInhabitant(V); RY.bringMobHere(V, false);
	var southRains = MK.maskCheck("-SEASON \"+the Rains\"", V, true);
	V.location().delInhabitant(V); RX.bringMobHere(V, false);
	check("masks", inBurn && !notRains && !notBurn && southRains && has(s(MK.maskDesc("-SEASON \"+the Burn\"")).toUpperCase(), "THE BURN"),
		"burn " + inBurn + " rains " + notRains + " +burn " + notBurn + " south " + southRains + " / " + MK.maskDesc("-SEASON \"+the Burn\""));
	// VARIES: a block in the season's own name, and <SUMMER> by what it counts as
	var text = s(CMLib.commands().parseVaries(V, X, RX, "<VARIES><the Burn>Heat shimmers.</the Burn><the Rains>Rain drums.</the Rains><SUMMER> It is hot.</SUMMER></VARIES>"));
	check("varies", has(text, "Heat shimmers.") && has(text, "It is hot.") && !has(text, "Rain drums."), text);
	// TIME: the season by name, and each moon at night
	X.getClimateObj().setCurrentWeatherType(Climate.WEATHER_CLEAR);
	C.setHourOfDay(5);
	var time = s(C.timeDescription(V, RX));
	check("time", has(time, "It is the Burn.") && has(time, "Ember is full.") && has(time, "Selune is a waning crescent."), time);
	check("describe", has(Cal.describe(X), "the season the Burn (as summer)") && has(Cal.describe(X), "Ember is full."), Cal.describe(X));
	check("help", has(CMLib.help().getHelpText("CALENDAR", M, true), "SEASONS=") && has(CMLib.help().getHelpText("CALENDAR", M, true), "MOONS=")
		&& has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ISMOON([MOON]"), "no help");
} finally {
	V.destroy();
	[X, Y].forEach(function (A) { A.delBlurbFlag("CALENDAR"); });
	Cal.applyAll();
	DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
	X.removeChild(Y);
	[Y, X].forEach(function (A) { CMLib.map().delArea(A); });
}
done();
