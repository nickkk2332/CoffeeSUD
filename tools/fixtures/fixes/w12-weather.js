// W12: MPWEATHER (NEXT, FOR, QUIET); WEATHER SET ... FOR, WEATHER FORECAST and WEATHER NEXT; cm.weather()'s hold and chances.
FIXFILE = "w12-weather";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var Climate = CM.Common.interfaces.Climate;
var GameTime = CM.core.GameTime;
var R = M.location();
var A = R.getArea();
var C = A.getClimateObj();
var B = newMob("GenMob", R);
B.setName("Fixw12builder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var t = all.substring(mark); mark = all.length; return t; }
function run(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("Weather").execute(B, L, 0); return out(); }
function runScript(body) { var m = scriptedMob("Fixw12clerk", "RAND_PROG 100\n" + body + "\n~\n"); tickMob(m); m.destroy(); }
// held for a while: each change comes back to it
runScript("mpweather HERE THUNDERSTORM FOR 2 HOURS");
var now = C.weatherType(null), held = C.heldWeather();
C.forceWeatherTick(A); C.forceWeatherTick(A);
check("hold", now == Climate.WEATHER_THUNDERSTORM && held == Climate.WEATHER_THUNDERSTORM && C.weatherType(null) == Climate.WEATHER_THUNDERSTORM
	&& C.nextWeatherType(null) == Climate.WEATHER_THUNDERSTORM && C.heldUntil() > GameTime.now(), "now " + now + " held " + held + " after " + C.weatherType(null));
var fc = run("WEATHER FORECAST");
check("forecast", has(fc, "thunderstorm, then thunderstorm") && has(fc, "Held at thunderstorm") && has(fc, "After that: ") && has(fc, "%"), fc);
// a hold that has run out lets the weather go
C.holdWeather(Climate.WEATHER_THUNDERSTORM, GameTime.now() - 1);
C.forceWeatherTick(A);
check("expired", C.heldWeather() == -1, "still held " + C.heldWeather());
// NEXT only; QUIET now; WEATHER NEXT steps
runScript("mpweather HERE CLEAR QUIET");
var quiet = C.weatherType(null);
runScript("mpweather HERE RAIN NEXT");
var stayed = C.weatherType(null), coming = C.nextWeatherType(null);
var said = run("WEATHER NEXT");
check("next", quiet == Climate.WEATHER_CLEAR && stayed == Climate.WEATHER_CLEAR && coming == Climate.WEATHER_RAIN && C.weatherType(null) == Climate.WEATHER_RAIN && has(said, "is now rain"),
	"quiet " + quiet + " stayed " + stayed + " coming " + coming + " now " + C.weatherType(null) + ": " + said);
// WEATHER SET ... FOR, for builders
said = run("WEATHER SET SNOW FOR 3 HOURS");
check("set-for", C.weatherType(null) == Climate.WEATHER_SNOW && C.heldWeather() == Climate.WEATHER_SNOW && has(said, "held for 3 game hours"), said);
// JavaScript
var JW = cm.weather(A);
var heldJs = JW.hold("fog", 1);
var odds = JW.chances();
check("js", heldJs && C.weatherType(null) == Climate.WEATHER_FOG && C.heldWeather() == Climate.WEATHER_FOG && odds.size() > 0 && (!JW.hold("glitter", 1)), "odds " + odds);
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MPWEATHER [AREA/HERE]") && has(CMLib.help().getHelpText("HAZARDS", M, true), "WEATHER FORECAST"), "no help");
C.holdWeather(-1, 0);
B.setSession(null);
B.destroy();
done();
