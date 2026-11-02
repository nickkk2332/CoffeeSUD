// W20: climates as data: world/climates/NAME.ini by an area's WEATHERPROFILE (MODIFY AREA), its weights, TRANSITION, ALLOW, HOLD and texts.
FIXFILE = "w20-climate";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function writeFile(F, t) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var Climate = CM.Common.interfaces.Climate;
var CP = CM.core.ClimateProfiles;
var R = M.location();
var A = R.getArea();
var C = A.getClimateObj();
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "climates"), "fixw20.ini");
writeFile(F, "# a test climate\nALL=CLEAR 10, FOG 90\nSUMMER=CLEAR:50,FOG:50\nTRANSITION=CLEAR->FOG=40, FOG->FOG=30\nALLOW=CLEAR,FOG\nHOLD=0\n"
	+ "TEXT_FOG=A cold grey fog rolls between the fixw20 stones.|The fixw20 fog thickens.\nSTOP_FOG=The fixw20 fog lifts.\nBOGUS=1\n");
CP.reload();
function modify(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("Modify").execute(M, L, 0); }
modify("MODIFY AREA WEATHERPROFILE fixw20");
var P = CP.get("fixw20");
check("profile", s(A.getBlurbFlag("WEATHERPROFILE")) == "fixw20" && s(CP.nameFor(A)) == "fixw20" && P != null && P.holdHours == 0 && P.allows(Climate.WEATHER_FOG)
	&& (!P.allows(Climate.WEATHER_RAIN)) && P.problems.size() == 1 && has(P.problems.get(0), "BOGUS"), "blurb " + A.getBlurbFlag("WEATHERPROFILE") + " problems " + (P == null ? "none" : P.problems));
// only CLEAR and FOG, with the transition's chance in place of the built-in one
var chart = C.weatherChances(A, Climate.WEATHER_CLEAR);
var others = 0;
for (var g = 0; g < chart.length; g++) if (g != Climate.WEATHER_CLEAR && g != Climate.WEATHER_FOG && chart[g] > 0) others++;
check("chart", others == 0 && chart[Climate.WEATHER_FOG] > 0 && chart[Climate.WEATHER_CLEAR] > 0, "others " + others + " fog " + chart[Climate.WEATHER_FOG]);
var seen = {};
for (var i = 0; i < 30; i++) { C.forceWeatherTick(A); seen[C.weatherType(null)] = true; }
var bad = 0;
for (var k in seen) if (k != Climate.WEATHER_CLEAR && k != Climate.WEATHER_FOG) bad++;
check("allowed", bad == 0, "weathers seen " + JSON.stringify(seen));
// texts in place of lists.ini's
C.setCurrentWeatherType(Climate.WEATHER_FOG);
var desc = s(C.getWeatherDescription(A));
var stop = s(call(C, "weatherStop", [A, java.lang.Integer.valueOf(Climate.WEATHER_FOG)]));
check("texts", (desc == "A cold grey fog rolls between the fixw20 stones." || desc == "The fixw20 fog thickens.") && stop == "The fixw20 fog lifts.", desc + " / " + stop);
// HOLD: the weather lasts that many game hours before it changes on its own (a forced change does not wait)
writeFile(F, "ALL=CLEAR 50, FOG 50\nALLOW=CLEAR,FOG\nHOLD=5\n");
CP.reload();
C.setCurrentWeatherType(Climate.WEATHER_CLEAR); C.setNextWeatherType(Climate.WEATHER_FOG);
C.forceWeatherTick(A);
var forced = C.weatherType(null);
C.setNextWeatherType(Climate.WEATHER_CLEAR);
var ticker = C.getClass().getDeclaredField("weatherTicker"); ticker.setAccessible(true); ticker.setInt(C, 1);
C.weatherTick(A);
check("hold", forced == Climate.WEATHER_FOG && C.weatherType(null) == Climate.WEATHER_FOG, "forced " + forced + " then " + C.weatherType(null));
var fc = s(CM.core.WeatherControl.forecast(A));
check("forecast", has(fc, "Climate: fixw20 (climates/fixw20.ini)"), fc);
// NONE gives the built-in climate back
modify("MODIFY AREA WEATHERPROFILE NONE");
check("none", A.getBlurbFlag("WEATHERPROFILE") == null && CP.of(A) == null, "still " + A.getBlurbFlag("WEATHERPROFILE"));
check("help", has(CMLib.help().getHelpText("WEATHERPROFILE", M, true), "TRANSITION="), "no help");
F["delete"]();
CP.reload();
done();
