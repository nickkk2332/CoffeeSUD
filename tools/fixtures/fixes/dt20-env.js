// DT20: WEATHER SET, TIME SET, TIME SEASON, LOOK AS and HAZARDS, for builders.
FIXFILE = "dt20-env";
var R = M.location();
var A = R.getArea();
var Climate = CM.Common.interfaces.Climate;
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixenvbuilder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function run(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(B, L, 0); return out(); }
var C = A.getClimateObj();
var T = A.getTimeObj();
var oldWeather = C.weatherType(null), oldHour = T.getHourOfDay(), oldSeason = "" + T.getSeasonCode().name();

// WEATHER SET
var said = run("Weather", "WEATHER SET RAIN");
check("weather", C.weatherType(null) == Climate.WEATHER_RAIN && has(said, "is now rain"), "weather " + C.weatherType(null) + ": " + said);
said = run("Weather", "WEATHER SET SNOW NEXT");
check("weather-next", C.weatherType(null) == Climate.WEATHER_RAIN && C.nextWeatherType(null) == Climate.WEATHER_SNOW && has(said, "when it next changes"), said);
check("weather-bad", has(run("Weather", "WEATHER SET GLITTER"), "'GLITTER' is no weather"), "a bad weather");
// TIME SET and TIME SEASON run the clock forward
said = run("Time", "TIME SET DUSK");
check("time-dusk", ("" + T.getTODCode().name()) == "DUSK" && has(said, "(dusk)"), "time " + T.getTODCode() + ": " + said);
said = run("Time", "TIME SET NIGHT");
check("time-night", ("" + T.getTODCode().name()) == "NIGHT", "time " + T.getTODCode() + ": " + said);
said = run("Time", "TIME SEASON WINTER");
check("season", ("" + T.getSeasonCode().name()) == "WINTER" && has(said, "It is now winter"), "season " + T.getSeasonCode() + ": " + said);
// LOOK AS: the room as PREVIEW reads it
var oldTitle = R.displayText();
R.setDisplayText("<VARIES><NIGHT>A fixes night</NIGHT><DAY>A fixes day</DAY></VARIES>");
said = run("Look", "LOOK AS DAY");
check("look-as", has(said, "A fixes day") && !has(said, "A fixes night"), said);
R.setDisplayText(oldTitle);
// HAZARDS
var H = CMClass.getLocale("StdRoom");
H.setRoomID(A.Name() + "#9401");
H.setArea(A);
H.setDisplayText("A fixes ledge");
var ledge = CMClass.getAbility("Prop_NarrowLedge");
H.addNonUninvokableEffect(ledge);
var em = CMClass.getBehavior("Emoter");
em.setParms("min=5 max=9 chance=50;creaks.");
H.addBehavior(em);
said = run("EnvToolsCmd", "HAZARDS");
check("hazards", has(said, "Hazards, spawners and conditions in " + A.Name() + ":") && has(said, A.Name() + "#9401 Prop_NarrowLedge:") && has(said, A.Name() + "#9401 Emoter: parameters: min=5 max=9"), said);
A.delProperRoom(H);

// put the world back for the tests after: the clock forward to the old hour and season, and the weather
while (("" + T.getSeasonCode().name()) != oldSeason) T.tickTock(T.getHoursInDay() * T.getDaysInMonth());
var ahead = (oldHour - T.getHourOfDay() + T.getHoursInDay()) % T.getHoursInDay();
if (ahead > 0) T.tickTock(ahead);
C.setNextWeatherType(oldWeather); C.forceWeatherTick(A);
check("restored", T.getHourOfDay() == oldHour, "the hour is " + T.getHourOfDay() + ", not " + oldHour);
B.setSession(null);
B.destroy();
done();
