// W7: RoomStates: states (TITLE DESC ATMOSPHERE ADD BLOCK ENTERMSG), transitions (ONVAR, TICKS, WHEN AFTER), MPROOMSTATE, ROOMSTATE(), an area's rooms together, the saved state.
FIXFILE = "w7-roomstates";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var Climate = CM.Common.interfaces.Climate;
var TRB = Tickable.TICKID_ROOM_BEHAVIOR;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw7Mine");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixw7Mine#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
}
var SHAFT = room(0, "Fixw7 shaft"), BELOW = room(1, "Fixw7 gallery"), HUT1 = room(2, "Fixw7 hut"), HUT2 = room(3, "Fixw7 shed");
link(SHAFT, Dir.DOWN, BELOW);
var B = CMClass.getBehavior("RoomStates");
B.setParms("STATE=dry TITLE=\"A dusty shaft\" DESC=\"Dust hangs in the air.\";"
	+ " STATE=collapsed TITLE=\"A collapsed shaft\" DESC=\"Rubble chokes the shaft.\" ADD=\"Prop_RoomDark()\" BLOCK=down ENTERMSG=\"Dust and rubble fill the air.\";"
	+ " STATE=cleared TITLE=\"A shored-up shaft\" DESC=\"Beams hold the roof.\";"
	+ " STATE=flooded TITLE=\"A flooded shaft\" ATMOSPHERE=WATER;"
	+ " TO=collapsed FROM=dry ONVAR=\"FIXTEST W7FALL==1\";"
	+ " TO=dry FROM=collapsed TICKS=50;"
	+ " TO=flooded FROM=cleared WHEN=\"-WEATHER +RAIN\" AFTER=2;"
	+ " START=dry");
SHAFT.addBehavior(B);
function tick(n) { for (var i = 0; i < n; i++) B.tick(SHAFT, TRB); }
tick(1);
check("start", s(B.currentState()) == "dry" && s(SHAFT.displayText()) == "A dusty shaft" && s(SHAFT.description()) == "Dust hangs in the air.", B.currentState() + " / " + SHAFT.displayText());
// a variable makes it collapse: darker, the way down blocked, a line on entry
VARS.setVar("FIXTEST", "W7FALL", "1");
tick(1);
var V = CMClass.getMOB("GenMob"); V.setName("Fixw7miner"); V.bringToLife(SHAFT, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
SHAFT.recoverPhyStats();
var dark = CMLib.flags().isInDark(SHAFT);
CMLib.tracking().walk(V, Dir.DOWN, false, false);
var said = s(field(VS, "bout").toString());
check("collapsed", s(B.currentState()) == "collapsed" && s(SHAFT.displayText()) == "A collapsed shaft" && dark && V.location() == SHAFT && has(said, "The way down is blocked."),
	B.currentState() + " dark " + dark + " in " + V.location().displayText() + ": " + said);
// MPROOMSTATE and ROOMSTATE()
clearVar("W7S");
var m = newMob("GenMob", SHAFT); m.setName("Fixw7foreman");
scriptOn(m, "RAND_PROG 100\nmproomstate HERE cleared\nmpsetvar FIXTEST W7S $%ROOMSTATE(HERE)%\n~\n"); tickMob(m); m.destroy();
SHAFT.recoverPhyStats();
check("script", gvar("W7S") == "cleared" && s(SHAFT.displayText()) == "A shored-up shaft" && SHAFT.fetchEffect("Prop_RoomDark") == null && (!CMLib.flags().isInDark(SHAFT)),
	"state " + gvar("W7S") + " title " + SHAFT.displayText());
// WHEN holding AFTER ticks in a row
var C = X.getClimateObj();
C.setCurrentWeatherType(Climate.WEATHER_RAIN);
tick(1);
var afterOne = s(B.currentState());
tick(1);
check("when", afterOne == "cleared" && s(B.currentState()) == "flooded" && SHAFT.getAtmosphere() != BELOW.getAtmosphere(), "after one " + afterOne + " then " + B.currentState());
C.setCurrentWeatherType(Climate.WEATHER_CLEAR);
// the saved state comes back
var state = s(B.getStat("STATE"));
var B2 = CMClass.getBehavior("RoomStates");
B2.setParms(B.getParms());
var SHAFT2 = room(4, "Fixw7 copy"); SHAFT2.addBehavior(B2);
B2.setStat("STATE", state);
check("saved", has(state, "NAME=\"flooded\"") && s(B2.currentState()) == "flooded" && s(SHAFT2.displayText()) == "A flooded shaft", state + " -> " + B2.currentState());
// on an area: its rooms together
var AB = CMClass.getBehavior("RoomStates");
AB.setParms("STATE=day TITLE=\"A quiet yard\"; STATE=fair TITLE=\"A crowded fairground\"; ROOMS=Fixw7Mine#2,Fixw7Mine#3; START=day");
X.addBehavior(AB);
AB.tick(X, Tickable.TICKID_AREA);
clearVar("W7A");
var m2 = newMob("GenMob", SHAFT); m2.setName("Fixw7crier");
scriptOn(m2, "RAND_PROG 100\nmproomstate Fixw7Mine fair\nmpsetvar FIXTEST W7A $%ROOMSTATE(Fixw7Mine)%\n~\n"); tickMob(m2); m2.destroy();
check("area", gvar("W7A") == "fair" && s(HUT1.displayText()) == "A crowded fairground" && s(HUT2.displayText()) == "A crowded fairground", "state " + gvar("W7A") + " " + HUT1.displayText() + "/" + HUT2.displayText());
check("help", has(CMLib.help().getHelpText("BEHAVIOR_ROOMSTATES", M, true), "ONVAR") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPROOMSTATE"), "no help");
V.setSession(null);
clearVar("W7FALL");
[SHAFT, BELOW, HUT1, HUT2, SHAFT2].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var mm = r.fetchInhabitant(i); if (mm != null) mm.destroy(); } });
CMLib.map().delArea(X);
done();
