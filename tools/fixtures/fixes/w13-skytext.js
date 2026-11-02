// W13: Prop_SkyText on an area and a room: time-of-day lines, weather lines and ends, NONE; the sun rises in the east.
FIXFILE = "w13-skytext";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var Climate = CM.Common.interfaces.Climate;
var TOD = CM.Common.interfaces.TimeClock.TimeOfDay;
var Sky = CM.core.SkyText;
var R = M.location();
var A = R.getArea();
var C = A.getClimateObj();
var AP = CMClass.getAbility("Prop_SkyText");
AP.setMiscText("DAWN=\"The red sun drags itself over the ash dunes.\" WEATHER_SNOW=\"Grey ash begins to fall.|Ash drifts down.\" STOP_SNOW=\"The ash stops falling.\" MOON=\"A red moon hangs low.\"");
A.addNonUninvokableEffect(AP);
var R2 = CMClass.getLocale("Plains");
R2.setRoomID(A.Name() + "#9413");
R2.setArea(A);
R2.setDisplayText("A fixw13 ruin");
var RP = CMClass.getAbility("Prop_SkyText");
RP.setMiscText("DUSK=NONE INNIGHT=\"The glowcaps dim as night falls far above.\" WEATHER_SNOW=\"Snow sifts into the fixw13 ruin.\"");
R2.addNonUninvokableEffect(RP);
check("tod", s(Sky.tod(R2, true, TOD.DAWN)) == "The red sun drags itself over the ash dunes." && s(Sky.tod(R2, true, TOD.DUSK)) == ""
	&& s(Sky.tod(R2, false, TOD.NIGHT)) == "The glowcaps dim as night falls far above." && Sky.tod(R2, true, TOD.NIGHT) == null,
	"dawn " + Sky.tod(R2, true, TOD.DAWN) + " dusk '" + Sky.tod(R2, true, TOD.DUSK) + "'");
var oldWeather = C.weatherType(null);
C.setCurrentWeatherType(Climate.WEATHER_SNOW);
var areaDesc = s(C.getWeatherDescription(A));
var roomDesc = s(C.weatherDescription(R2));
var stop = s(call(C, "weatherStop", [A, java.lang.Integer.valueOf(Climate.WEATHER_SNOW)]));
check("weather", (areaDesc == "Grey ash begins to fall." || areaDesc == "Ash drifts down.") && roomDesc == "Snow sifts into the fixw13 ruin." && stop == "The ash stops falling.",
	areaDesc + " / " + roomDesc + " / " + stop);
// a change of weather, as the room with its own lines tells it
var mode = C.getClass().getDeclaredField("lastSayMode"); mode.setAccessible(true); mode.setInt(C, 1);
var told = s(call(C, "roomSay", [R2, java.lang.Integer.valueOf(Climate.WEATHER_CLEAR), "the area's line"]));
var plain = s(call(C, "roomSay", [R, java.lang.Integer.valueOf(Climate.WEATHER_CLEAR), "the area's line"]));
check("change", told == "Snow sifts into the fixw13 ruin." && plain == "the area's line", told + " / " + plain);
check("moon", s(Sky.line(R2, null, "MOON")) == "A red moon hangs low.", "no moon line");
var CMP = CM.core.CMProps;
check("east", has(CMP.getListFileChoiceFromIndexedList(CMP.ListFile.TOD_CHANGE_OUTSIDE, TOD.DUSK.ordinal()), "west")
	&& (!has(new CM.core.CMFile(CM.core.Resources.makeFileResourceName("lists.ini"), null).text(), "rise in the west")), "the sun still rises in the west");
check("help", has(CMLib.help().getHelpText("PROP_SKYTEXT", M, true), "WEATHER_[STATE]"), "no help");
C.setCurrentWeatherType(oldWeather);
A.delEffect(AP);
A.delProperRoom(R2);
done();
