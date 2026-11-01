// ST4: weather, time of day and date changes reach an area's scripts as
// messages (WEATHERCHANGE, TODCHANGE, DATECHANGE), and fire WEATHER_PROG and
// MOON_PROG; a weather put back as it was (a restore) fires nothing.
FIXFILE = "st4-weather";
var R = M.location();
var A = R.getArea();
var Climate = CM.Common.interfaces.Climate;
["FIXST4W", "FIXST4A", "FIXST4E", "FIXST4T", "FIXST4D", "FIXST4M", "FIXST4X"].forEach(clearVar);
var SR = scriptOn(R, "WEATHER_PROG RAIN CLEAR\nmpsetvar FIXTEST FIXST4W $G:$0:$1\n~\nWEATHER_PROG SNOW\nmpsetvar FIXTEST FIXST4X $G\n~\n");
var SA = scriptOn(A, "WEATHER_PROG ANY\nmpsetvar FIXTEST FIXST4A $G\n~\n");
var I = CMClass.getItem("StdItem");
I.setName("a fixst4 weathervane");
R.addItem(I);
var SI = scriptOn(I, "EXECMSG_PROG WEATHERCHANGE ALL\nmpsetvar FIXTEST FIXST4E yes\n~\nEXECMSG_PROG TODCHANGE ALL\nmpsetvar FIXTEST FIXST4T yes\n~\nEXECMSG_PROG DATECHANGE ALL\nmpsetvar FIXTEST FIXST4D yes\n~\n");
var W = newMob("GenMob", R);
W.setName("a fixst4 werewolf");
var SW = scriptOn(W, "MOON_PROG ANY\nmpsetvar FIXTEST FIXST4M $G:$0\n~\n");
function runAll() { runQueued(SR); runQueued(SA); runQueued(SI); runQueued(SW); }

// a change of weather, as WEATHER SET makes it
var C = A.getClimateObj();
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
C.setNextWeatherType(Climate.WEATHER_RAIN);
C.forceWeatherTick(A);
runAll();
var fc = "" + Climate.WEATHER_DESCS[C.nextWeatherType(null)];
check("weather-now", C.weatherType(null) == Climate.WEATHER_RAIN, "the weather is " + Climate.WEATHER_DESCS[C.weatherType(null)]);
check("weather-prog", gvar("FIXST4W") == "RAIN:CLEAR:" + fc, "FIXST4W='" + gvar("FIXST4W") + "', forecast " + fc);
check("weather-area", gvar("FIXST4A") == "RAIN", "FIXST4A='" + gvar("FIXST4A") + "'");
check("weather-msg", gvar("FIXST4E") == "yes", "FIXST4E='" + gvar("FIXST4E") + "'");
check("weather-other", gvar("FIXST4X") == "", "FIXST4X='" + gvar("FIXST4X") + "'");
// put back as a restore does: nothing
["FIXST4W", "FIXST4A", "FIXST4E"].forEach(clearVar);
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
runAll();
check("restore-silent", gvar("FIXST4A") == "" && gvar("FIXST4E") == "", "FIXST4A='" + gvar("FIXST4A") + "', FIXST4E='" + gvar("FIXST4E") + "'");

// the clock: an hour at a time to the next time of day, then a day at a time to the next moon
var TC = A.getTimeObj();
var hours = 0;
var tod = "" + TC.getTODCode();
for (var i = 0; i < 100 && ("" + TC.getTODCode()) == tod; i++) { TC.tickTock(1); hours++; }
runAll();
check("tod-msg", gvar("FIXST4T") == "yes" && ("" + TC.getTODCode()) != tod, "FIXST4T='" + gvar("FIXST4T") + "', " + tod + " to " + TC.getTODCode());
var moon = "" + TC.getMoonPhase(null);
for (var i = 0; i < 100 && ("" + TC.getMoonPhase(null)) == moon; i++) { TC.tickTock(TC.getHoursInDay()); hours += TC.getHoursInDay(); }
runAll();
check("date-msg", gvar("FIXST4D") == "yes", "FIXST4D='" + gvar("FIXST4D") + "'");
check("moon-prog", gvar("FIXST4M") == TC.getMoonPhase(null) + ":" + moon, "FIXST4M='" + gvar("FIXST4M") + "', the moon " + moon + " to " + TC.getMoonPhase(null));

R.delScript(SR);
A.delScript(SA);
I.destroy();
W.destroy();
TC.tickTock(-hours);
done();
