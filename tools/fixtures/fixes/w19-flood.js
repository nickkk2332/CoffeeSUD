// W19: Floodable: RISE (a mask), ONVAR, RATE and DRAIN, the levels (swimming, water), things floating off, FLOODLEVEL().
FIXFILE = "w19-flood";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var Climate = CM.Common.interfaces.Climate;
var TRB = Tickable.TICKID_ROOM_BEHAVIOR;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw19Drains");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw19Drains#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
}
var DRAIN = room(0, "Fixw19 drain"), OUTFALL = room(1, "Fixw19 outfall"), PUMP = room(2, "Fixw19 pump room");
link(DRAIN, Dir.EAST, OUTFALL);
var air = DRAIN.getAtmosphere();
var F = CMClass.getBehavior("Floodable");
F.setParms("RISE=\"-WEATHER +RAIN\" RATE=2 DRAIN=1 FLOWTO=east FLOAT=2 DESC2=\"Water swirls waist-deep through the fixw19 drain.\"");
DRAIN.addBehavior(F);
function tick(n) { for (var i = 0; i < n; i++) F.tick(DRAIN, TRB); }
function level() { clearVar("W19L"); var m = newMob("GenMob", DRAIN); m.setName("Fixw19clerk"); scriptOn(m, "RAND_PROG 100\nmpsetvar FIXTEST W19L $%FLOODLEVEL(HERE)%\n~\n"); tickMob(m); m.destroy(); return gvar("W19L"); }
var C = X.getClimateObj();
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
tick(3);
var dry = level();
C.setCurrentWeatherType(Climate.WEATHER_RAIN);
tick(2);
var ankle = level();
var leaf = CMClass.getItem("GenItem"); leaf.setName("a fixw19 leaf"); leaf.basePhyStats().setWeight(1); leaf.recoverPhyStats(); DRAIN.addItem(leaf);
tick(2);
var waist = level();
check("rise", dry == "0" && ankle == "1" && waist == "2" && DRAIN.fetchEffect("Chant_Flood") != null && s(DRAIN.description()) == "Water swirls waist-deep through the fixw19 drain."
	&& OUTFALL.isContent(leaf), "levels " + dry + " " + ankle + " " + waist + " desc " + DRAIN.description() + " leaf in outfall " + OUTFALL.isContent(leaf));
tick(2);
check("full", level() == "3" && DRAIN.getAtmosphere() != air, "level " + level() + " air " + DRAIN.getAtmosphere());
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
tick(1); var down1 = level();
tick(2);
check("drain", down1 == "2" && level() == "0" && DRAIN.getAtmosphere() == air && DRAIN.fetchEffect("Chant_Flood") == null, "down " + down1 + " then " + level());
// a sluice: a script variable
var F2 = CMClass.getBehavior("Floodable");
F2.setParms("ONVAR=\"FIXTEST W19S==open\" RATE=1");
PUMP.addBehavior(F2);
F2.tick(PUMP, TRB);
var closed = s(F2.getStat("FLOODLEVEL"));
VARS.setVar("FIXTEST", "W19S", "open");
F2.tick(PUMP, TRB);
check("onvar", closed == "0" && s(F2.getStat("FLOODLEVEL")) == "1", "closed " + closed + " open " + F2.getStat("FLOODLEVEL"));
clearVar("W19S");
check("help", has(CMLib.help().getHelpText("BEHAVIOR_FLOODABLE", M, true), "FLOWTO") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "FLOODLEVEL("), "no help");
[DRAIN, OUTFALL, PUMP].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var mm = r.fetchInhabitant(i); if (mm != null) mm.destroy(); } });
CMLib.map().delArea(X);
done();
