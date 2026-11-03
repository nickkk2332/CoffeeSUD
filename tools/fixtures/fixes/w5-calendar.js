// W5: an area's CALENDAR (in its data, so in its .cmare): its own clock, shared by its children, START for a new game, the saved date after; MODIFY AREA CALENDAR.
FIXFILE = "w5-calendar";
var Cal = CM.core.Calendars;
var PS = CM.Common.interfaces.PhyStats;
var TOD = CM.Common.interfaces.TimeClock.TimeOfDay;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var DB = CMLib.database();
var G = CMLib.time().globalClock();
function area(name) {
	var A = CMClass.getAreaType("StdArea"); A.setName(name);
	A.basePhyStats().setDisposition(A.basePhyStats().disposition() | PS.IS_UNSAVABLE); A.recoverPhyStats();
	CMLib.map().addArea(A);
	return A;
}
function modify(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("Modify").execute(M, L, 0); }
var X = area("Fixw5Elvenrealm"), Y = area("Fixw5Glade"), Z = area("Fixw5Underhill");
X.addChild(Y); Y.addParent(X); X.addChild(Z); Z.addParent(X);
DB.DBDeletePlayerData(X.Name(), "TIMECLOCK"); DB.DBDeletePlayerData(Z.Name(), "TIMECLOCK");
var HA = M.location().getArea();
try {
	// reading one
	var P = Cal.parse("HOURS=10 DAYS=20 MONTHS=\"Leafwake,Bloomtide\" START=812/2/3 BOGUS=1");
	check("parse", P.hours.intValue() == 10 && P.days.intValue() == 20 && P.months.length == 2 && s(P.months[1]) == "Bloomtide"
		&& P.start[0] == 812 && P.start[1] == 2 && P.start[2] == 3 && P.start[3] == -1 && P.problems.size() == 1 && has(P.problems.get(0), "BOGUS"), "problems " + P.problems);
	// an elven realm with 10-hour days; a new game opens there at dawn on 3 Bloomtide 812
	X.addBlurbFlag("CALENDAR HOURS=10 DAYS=20 MONTHS=\"Leafwake,Bloomtide,Frostfall\" DAWNHR=0 DAYHR=1 DUSKHR=8 NIGHTHR=9 START=812/2/3");
	Cal.applyAll();
	var C = X.getTimeObj();
	check("own", C != G && Y.getTimeObj() == C && C.getHoursInDay() == 10 && C.getDaysInMonth() == 20 && s(C.getMonthNames()[1]) == "Bloomtide"
		&& C.getDawnToDusk()[TOD.NIGHT.ordinal()] == 9 && s(java.util.Arrays.toString(C.getWeekNames())) == s(java.util.Arrays.toString(G.getWeekNames())),
		"clock " + (C == G ? "global" : "own") + " child " + (Y.getTimeObj() == C) + " hours " + C.getHoursInDay() + " months " + java.util.Arrays.toString(C.getMonthNames()));
	check("start", C.getYear() == 812 && s(C.getMonthNames()[C.getMonth()]) == "Bloomtide" && C.getDayOfMonth() == 3 && C.getHourOfDay() == 0 && C.getTODCode() == TOD.DAWN,
		C.getYear() + "/" + C.getMonth() + "/" + C.getDayOfMonth() + "/" + C.getHourOfDay() + " " + C.getMonthNames()[C.getMonth()]);
	// a child with a calendar of its own keeps it, and takes the rest from its parent's
	Z.addBlurbFlag("CALENDAR HOURS=5 DAWNHR=0 DAYHR=1 DUSKHR=3 NIGHTHR=4");
	Cal.applyAll();
	var CZ = Z.getTimeObj();
	check("child", CZ != C && CZ != G && CZ.getHoursInDay() == 5 && CZ.getDaysInMonth() == 20 && Y.getTimeObj() == C && s(CZ.getMonthNames()[0]) == "Leafwake",
		"hours " + CZ.getHoursInDay() + " days " + CZ.getDaysInMonth() + " months " + java.util.Arrays.toString(CZ.getMonthNames()));
	// the date is save state: saved, it is read again in place of START, and only the date is saved
	C.tickTock(25);
	var when = C.getYear() + "/" + (C.getMonth() + 1) + "/" + C.getDayOfMonth() + "/" + C.getHourOfDay();
	C.save();
	var rec = DB.DBReadPlayerData(X.Name(), "TIMECLOCK");
	var xml = (rec == null || rec.size() == 0) ? "" : s(rec.get(0).xml());
	X.delBlurbFlag("CALENDAR"); Cal.applyAll();
	var back = X.getTimeObj() == G && Y.getTimeObj() == G;
	X.addBlurbFlag("CALENDAR HOURS=10 DAYS=20 MONTHS=\"Leafwake,Bloomtide,Frostfall\" DAWNHR=0 DAYHR=1 DUSKHR=8 NIGHTHR=9 START=812/2/3");
	Cal.applyAll();
	var C2 = X.getTimeObj();
	var again = C2.getYear() + "/" + (C2.getMonth() + 1) + "/" + C2.getDayOfMonth() + "/" + C2.getHourOfDay();
	check("saved", back && C2 != C && again == when && when != "812/2/3/0" && has(xml, "<YEAR>") && !has(xml, "<HOURS>") && C2.getHoursInDay() == 10,
		"back " + back + " saved " + when + " read " + again + " record " + xml);
	// MODIFY AREA CALENDAR, and NONE
	var hadOwn = HA.getTimeObj() != G;
	DB.DBDeletePlayerData(HA.Name(), "TIMECLOCK");
	modify("MODIFY AREA CALENDAR HOURS=12 DAWNHR=1 DAYHR=2 DUSKHR=10 NIGHTHR=11 START=5/1/2/3");
	var HC = HA.getTimeObj();
	var made = HC != G && HC.getHoursInDay() == 12 && HC.getYear() == 5 && HC.getMonth() == 0 && HC.getDayOfMonth() == 2 && HC.getHourOfDay() == 3 && has(HA.getBlurbFlag("CALENDAR"), "HOURS=12");
	modify("MODIFY AREA CALENDAR HOURS=x");
	var kept = has(HA.getBlurbFlag("CALENDAR"), "HOURS=12");
	modify("MODIFY AREA CALENDAR NONE");
	check("modify", !hadOwn && made && kept && HA.getTimeObj() == G && HA.getBlurbFlag("CALENDAR") == null, "had own " + hadOwn + " made " + made + " kept " + kept + " now " + (HA.getTimeObj() == G ? "global" : "own"));
	// a date as STARTDATE gives it
	var T = CMClass.getCommon("DefaultTimeClock"); T.setHoursInDay(6); T.setDaysInMonth(24); T.setDawnToDusk(0, 1, 4, 5);
	Cal.setDate(T, Cal.date("100/3/4"));
	check("date", T.getYear() == 100 && T.getMonth() == 2 && T.getDayOfMonth() == 4 && T.getHourOfDay() == 0 && Cal.date("1/2") == null, T.getYear() + "/" + T.getMonth() + "/" + T.getDayOfMonth() + "/" + T.getHourOfDay());
	check("help", has(CMLib.help().getHelpText("CALENDAR", M, true), "START=") && has(CMLib.help().getHelpText("CALENDAR", M, true), "STARTDATE"), "no help");
} finally {
	HA.delBlurbFlag("CALENDAR");
	[X, Z].forEach(function (A) { A.delBlurbFlag("CALENDAR"); DB.DBDeletePlayerData(A.Name(), "TIMECLOCK"); });
	Cal.applyAll();
	DB.DBDeletePlayerData(HA.Name(), "TIMECLOCK");
	X.removeChild(Y); X.removeChild(Z);
	[Y, Z, X].forEach(function (A) { CMLib.map().delArea(A); });
}
done();
