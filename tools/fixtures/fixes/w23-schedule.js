// W23: SET SCHEDULE "rules" (HOUR, WEEKDAY, EVERY, SEASON, MONTH, MOON, HOLIDAY, WEATHER, all of them holding) and SET DURATION UNTIL DUSK|HOUR h|END.
FIXFILE = "w23-schedule";
var SC = CM.core.Schedules, H = CM.core.Holidays;
var PS = CM.Common.interfaces.PhyStats;
var Climate = CM.Common.interfaces.Climate;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var DB = CMLib.database();
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw23Market");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
var qs = [];
function quest(script) { var q = CMClass.getCommon("DefaultQuest"); q.setScript(script, true); CMLib.quests().addQuest(q); q.autostartup(); qs.push(q); return q; }
function tick() { qs.forEach(function (q) { q.tick(q, Tickable.TICKID_QUEST); }); }
try {
	X.addBlurbFlag("CALENDAR HOURS=6 DAYS=10 MONTHS=\"Ash,Cinder,Flood,Silt\" WEEK=\"Sunday,Moonday,Fireday\" DAWNHR=1 DAYHR=2 DUSKHR=4 NIGHTHR=5 START=1/1/1/0");
	CM.core.Calendars.applyAll();
	var C = X.getTimeObj();
	X.getClimateObj().setCurrentWeatherType(Climate.WEATHER_CLEAR);
	// the rules, on the 1st of Ash, year 1: a Fireday, winter, a new moon
	function at(h, rule) { C.setHourOfDay(h); return SC.open(rule, C, X); }
	var r = [at(0, "WEEKDAY Fireday"), !at(0, "WEEKDAY Sunday"), !at(0, "HOUR 1-3"), at(2, "HOUR 1-3"), at(0, "HOUR 5-1"), at(2, "EVERY 2 DAYS AT HOUR 2"),
		!at(1, "EVERY 2 DAYS AT HOUR 2"), at(1, "EVERY 4 DAYS AT DAWN"), !at(0, "EVERY 4 DAYS AT DAWN"), !at(2, "EVERY 3 DAYS"), at(0, "SEASON WINTER"), at(0, "MONTH Ash"),
		!at(0, "MONTH 2"), at(0, "MOON NEW"), at(0, "WEATHER CLEAR"), !at(0, "HOLIDAY fixw23none"), at(2, "WEEKDAY Fireday HOUR 1-3 SEASON winter"), !at(2, "WEEKDAY Fireday AND HOUR 3-4")];
	var bad = [];
	r.forEach(function (ok, i) { if (!ok) bad.push(i); });
	check("rules", bad.length == 0 && SC.problems("HOUR x").size() == 1 && SC.problems("").size() == 1, "failed rules " + bad + " / " + SC.problems("HOUR x"));
	// a fair each Fireday, ended at dusk; a stall open only hours 2 to 3
	C.setHourOfDay(0);
	var fair = quest("set name fixw23fair\nset area Fixw23Market\nset schedule \"WEEKDAY Fireday\"\nset duration until dusk\nquiet\n");
	var stall = quest("set name fixw23stall\nset area Fixw23Market\nset schedule \"HOUR 2-3\"\nset duration until end\nquiet\n");
	tick();
	var f0 = fair.running(), s0 = stall.running();
	C.setHourOfDay(2); tick();
	var f2 = fair.running(), s2 = stall.running();
	C.setHourOfDay(4); tick();
	var f4 = fair.running(), s4 = stall.running();
	C.setHourOfDay(5); tick();
	var f5 = fair.running();
	check("window", f0 && !s0 && f2 && s2 && !f4 && !s4 && !f5 && s(fair.getStat("DURATION")) == "0", "fair " + f0 + "/" + f2 + "/" + f4 + "/" + f5 + " stall " + s0 + "/" + s2 + "/" + s4);
	// the next day (a Sunday) it stays shut; the next Fireday it opens again
	C.setHourOfDay(0); H.nextDay(C); tick();
	var sunday = fair.running();
	H.nextDay(C); tick(); H.nextDay(C); tick();
	check("again", !sunday && fair.running() && s(H.weekday(C)) == "Fireday", "sunday " + sunday + " now " + fair.running() + " " + H.weekday(C));
	check("help", has(CMLib.help().getHelpText("HOLIDAYS", M, true), "SET SCHEDULE") && has(CMLib.help().getHelpText("HOLIDAYS", M, true), "UNTIL DUSK"), "no help");
} finally {
	qs.forEach(function (q) { if (q.running()) q.stopQuest(); CMLib.quests().delQuest(q); });
	X.delBlurbFlag("CALENDAR"); CM.core.Calendars.applyAll();
	DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
	CMLib.map().delArea(X);
}
done();
