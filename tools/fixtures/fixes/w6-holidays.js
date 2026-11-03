// W6: holidays: SET WEEKDAY, EVERY, SEASONSTART, MOON (on the game's calendar), DURATION in game days; ISHOLIDAY, HOLIDAY(), +HOLIDAY, VARIES <HOLIDAY>, SET HOLIDAY, CALENDAR HOLIDAYS.
FIXFILE = "w6-holidays";
var H = CM.core.Holidays;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var DB = CMLib.database();
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw6Riverton");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
var R = CMClass.getLocale("StdRoom"); R.setRoomID("Fixw6Riverton#0"); R.setDisplayText("Fixw6 square"); R.setArea(X);
var Q = null, Q2 = null;
try {
	X.addBlurbFlag("CALENDAR HOURS=6 DAYS=10 MONTHS=\"Ash,Cinder,Flood,Silt\" WEEK=\"Sunday,Moonday,Fireday\" MOONS=\"Selune:8\" DAWNHR=0 DAYHR=1 DUSKHR=4 NIGHTHR=5 START=1/1/1/0");
	CM.core.Calendars.applyAll();
	var C = X.getTimeObj();
	// the recurrences, from the 1st of Ash, year 1 (a Fireday, a new moon)
	var wk = H.next("WEEKDAY Fireday", C, true), wk2 = H.next("WEEKDAY Fireday", C, false);
	var ev = H.next("EVERY 7 DAYS FROM 1/1/3", C, true);
	check("recur", H.daysUntil("WEEKDAY Fireday", C) == 0 && (H.dayNumber(wk2) - H.dayNumber(C)) == 3 && H.daysUntil("WEEKDAY sunday", C) == 1
		&& H.daysUntil("EVERY 7 DAYS FROM 1/1/3", C) == 2 && H.daysUntil("EVERY 7 FROM 1/1/1", C) == 0
		&& H.daysUntil("SEASONSTART SPRING", C) == 10 && H.daysUntil("MOON FULL", C) == 4 && H.daysUntil("MOON Selune FULL", C) == 4
		&& H.daysUntil("MUDDAY 1-1", C) == 10, "fireday " + H.daysUntil("WEEKDAY Fireday", C) + " next " + (H.dayNumber(wk2) - H.dayNumber(C))
		+ " every " + H.daysUntil("EVERY 7 DAYS FROM 1/1/3", C) + " spring " + H.daysUntil("SEASONSTART SPRING", C) + " full " + H.daysUntil("MOON FULL", C) + " mudday " + H.daysUntil("MUDDAY 1-1", C));
	// a quest on the full moon, a game day long
	Q = CMClass.getCommon("DefaultQuest");
	Q.setScript("set name fixw6moonfeast\nset area Fixw6Riverton\nset moon full\nset duration 2 days\nquiet\n", true);
	CMLib.quests().addQuest(Q);
	Q.autostartup();
	var perHour = CMProps.getMillisPerMudHour() / CMProps.getTickMillis();
	check("quest", s(Q.startDate()) == "MOON full" && Q.waitRemaining() == 4 * 6 * perHour && Q.duration() == CMLib.time().parseTickExpression(C, "2 MUDDAYS"),
		"start " + Q.startDate() + " wait " + Q.waitRemaining() + " (" + (4 * 6 * perHour) + ") duration " + Q.duration());
	// on: tests by name
	Q.startQuest();
	clearVar("W6A"); clearVar("W6B"); clearVar("W6C");
	var m = newMob("GenMob", M.location()); m.setName("Fixw6crier");
	scriptOn(m, "RAND_PROG 100\nif isholiday(fixw6moonfeast)\n mpsetvar FIXTEST W6A yes\nendif\nif isholiday(fixw6nofeast)\n mpsetvar FIXTEST W6B yes\nendif\nmpsetvar FIXTEST W6C $%HOLIDAY()%\n~\n");
	tickMob(m);
	var MK = CMLib.masking();
	var text = s(CMLib.commands().parseVaries(m, X, R, "<VARIES><HOLIDAY fixw6moonfeast>Lanterns hang from every eave.</HOLIDAY> The square is quiet.</VARIES>"));
	check("on", Q.running() && H.on("fixw6moonfeast") && gvar("W6A") == "yes" && gvar("W6B") == "" && has(gvar("W6C"), "fixw6moonfeast")
		&& MK.maskCheck("+HOLIDAY fixw6moonfeast", m, true) && !MK.maskCheck("-HOLIDAY fixw6moonfeast", m, true) && MK.maskCheck("+HOLIDAY", m, true)
		&& !MK.maskCheck("+HOLIDAY fixw6nofeast", m, true) && has(MK.maskDesc("+HOLIDAY fixw6moonfeast"), "holiday") && has(text, "Lanterns hang") && has(H.list(C), "fixw6moonfeast: on now"),
		"running " + Q.running() + " vars " + gvar("W6A") + "/" + gvar("W6B") + "/" + gvar("W6C") + " text " + text + " / " + H.list(C));
	// SET HOLIDAY: a quest that runs exactly while the feast does
	Q2 = CMClass.getCommon("DefaultQuest");
	Q2.setScript("set name fixw6lanterns\nset holiday fixw6moonfeast\nquiet\n", true);
	CMLib.quests().addQuest(Q2);
	Q2.autostartup();
	Q2.tick(Q2, Tickable.TICKID_QUEST);
	var follows = Q2.running();
	Q.stopQuest();
	Q2.tick(Q2, Tickable.TICKID_QUEST);
	var stops = !Q2.running();
	text = s(CMLib.commands().parseVaries(m, X, R, "<VARIES><HOLIDAY fixw6moonfeast>Lanterns hang from every eave.</HOLIDAY> The square is quiet.</VARIES>"));
	m.destroy();
	check("follows", follows && stops && !H.on("fixw6moonfeast") && !has(text, "Lanterns") && has(H.list(C), "fixw6moonfeast: ") && has(H.list(C), "(moon full)"),
		"follows " + follows + " stops " + stops + " text " + text + " / " + H.list(C));
	check("units", s(H.gameUnits("2 days+3 HOURS")) == "2 MUDDAYS+3 MUDHOURS" && s(H.gameUnits("1 MUDDAY")) == "1 MUDDAY", s(H.gameUnits("2 days+3 HOURS")));
	check("help", has(CMLib.help().getHelpText("CALENDAR", M, false), "HOLIDAYS") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ISHOLIDAY(")
		&& has(CMLib.help().getHelpText("HOLIDAYS", M, true), "SEASONSTART"), "no help");
} finally {
	[Q2, Q].forEach(function (q) { if (q != null) { if (q.running()) q.stopQuest(); CMLib.quests().delQuest(q); } });
	X.delBlurbFlag("CALENDAR"); CM.core.Calendars.applyAll();
	DB.DBDeletePlayerData(X.Name(), "TIMECLOCK");
	CMLib.map().delArea(X);
}
done();
