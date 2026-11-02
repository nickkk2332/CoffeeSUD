// ST11: TIME_PROG DAWN and ranges, DAY_PROG ranges; SEASON_PROG, MONTH_PROG,
// YEAR_PROG and WEEKDAY_PROG on the clock's changes; ISWEEKDAY().
FIXFILE = "st11-calendar";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var A = R.getArea();
var TC = A.getTimeObj();
var TOD = CM.Common.interfaces.TimeClock.TimeOfDay;
check("ranges", SEv.rangeMatches("6-18/3", 9) && !SEv.rangeMatches("6-18/3", 10) && SEv.rangeMatches("6-18", 18) && SEv.rangeMatches("7", 7) && !SEv.rangeMatches("7", 8), "rangeMatches");
var dawn = TC.getDawnToDusk()[TOD.DAWN.ordinal()];
check("named-hours", SEv.hourMatches("DAWN", dawn, TC) && !SEv.hourMatches("DAWN", dawn + 1, TC), "dawn is " + dawn);
["FIXST11T", "FIXST11D", "FIXST11M", "FIXST11W", "FIXST11S", "FIXST11Y", "FIXST11I"].forEach(clearVar);
var hours = 0;
for (var i = 0; i < 100 && TC.getHourOfDay() != dawn; i++) { TC.tickTock(1); hours++; }
var ST = scriptOn(R, "TIME_PROG DAWN\nmpsetvar FIXTEST FIXST11T $g\n~\nDAY_PROG 1-200\nmpsetvar FIXTEST FIXST11D $%ISWEEKDAY(" + SEv.weekday(TC) + ")%\n~\n");
ST.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
check("time-dawn", gvar("FIXST11T") == "" + dawn, "FIXST11T='" + gvar("FIXST11T") + "', hour " + TC.getHourOfDay());
check("day-range", gvar("FIXST11D") == ((SEv.weekday(TC).length() > 0) ? "true" : "false"), "FIXST11D='" + gvar("FIXST11D") + "', weekday " + SEv.weekday(TC));
R.delScript(ST);
var SM = scriptOn(R, "MONTH_PROG ANY\nmpsetvar FIXTEST FIXST11M $g:$0\n~\nWEEKDAY_PROG ANY\nmpsetvar FIXTEST FIXST11W $G\n~\n");
var SS = scriptOn(A, "SEASON_PROG ANY\nmpsetvar FIXTEST FIXST11S $G:$0\n~\nYEAR_PROG ANY\nmpsetvar FIXTEST FIXST11Y $g\n~\n");
var month = TC.getMonth();
for (var i = 0; i < 200 && TC.getMonth() == month; i++) { TC.tickTock(TC.getHoursInDay()); hours += TC.getHoursInDay(); }
runQueued(SM);
check("month", gvar("FIXST11M") == TC.getMonth() + ":" + month, "FIXST11M='" + gvar("FIXST11M") + "', month " + month + " to " + TC.getMonth());
check("weekday", TC.getDaysInWeek() == 0 || gvar("FIXST11W") == SEv.weekday(TC), "FIXST11W='" + gvar("FIXST11W") + "', today " + SEv.weekday(TC));
var season = "" + TC.getSeasonCode();
var year = TC.getYear();
for (var i = 0; i < 40 && (("" + TC.getSeasonCode()) == season || TC.getYear() == year); i++) { TC.tickTock(TC.getHoursInDay() * TC.getDaysInMonth()); hours += TC.getHoursInDay() * TC.getDaysInMonth(); runQueued(SS); }
runQueued(SS);
check("season", gvar("FIXST11S").indexOf(TC.getSeasonCode() + ":") == 0 && gvar("FIXST11S").length > ("" + TC.getSeasonCode()).length + 1, "FIXST11S='" + gvar("FIXST11S") + "', now " + TC.getSeasonCode());
check("year", gvar("FIXST11Y") == "" + TC.getYear(), "FIXST11Y='" + gvar("FIXST11Y") + "', year " + TC.getYear());
R.delScript(SM);
A.delScript(SS);
TC.tickTock(-hours);
done();
