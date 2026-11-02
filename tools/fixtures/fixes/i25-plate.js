// I25: Prop_PressurePlate on a fixture (WEIGHT, OFF/MSGOFF when the idol is lifted, sand of equal weight) and on a
// room (COUNT, ON/OFF, DELAY); ROOMWEIGHT() and CONTENTWEIGHT().
FIXFILE = "i25-plate";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi25Temple");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi25Temple#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var SHRINE = room(0, "Fixi25 shrine"), HALL = room(1, "Fixi25 hall"), GATE = room(2, "Fixi25 gate"), BEYOND = room(3, "Fixi25 beyond");
HALL.rawDoors()[Dir.EAST] = GATE; HALL.setRawExit(Dir.EAST, CMClass.getExit("Open"));
GATE.rawDoors()[Dir.WEST] = HALL; GATE.setRawExit(Dir.WEST, CMClass.getExit("Open"));
function door() { var e = CMClass.getExit("GenExit"); e.setName("a stone gate"); e.setDoorsNLocks(true, false, true, false, false, false); return e; }
var G1 = door(), G2 = door();
GATE.rawDoors()[Dir.NORTH] = BEYOND; GATE.setRawExit(Dir.NORTH, G1);
BEYOND.rawDoors()[Dir.SOUTH] = GATE; BEYOND.setRawExit(Dir.SOUTH, G2);
function weighs(I, w) { I.basePhyStats().setWeight(w); I.recoverPhyStats(); return I; }
var V = CMClass.getMOB("GenMob"); V.setName("Fixi25pip"); weighs(V, 80); V.bringToLife(SHRINE, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
// the idol on its pedestal
var ped = CMClass.getItem("GenContainer"); ped.setName("a stone pedestal"); ped.setCapacity(500); ped.setDoorsNLocks(false, true, false, false, false, false);
CMLib.flags().setGettable(ped, false); SHRINE.addItem(ped);
var idol = weighs(CMClass.getItem("GenItem"), 50); idol.setName("a golden fixi25 idol"); SHRINE.addItem(idol); idol.setContainer(ped);
var P = CMClass.getAbility("Prop_PressurePlate"); P.setMiscText("WEIGHT=\">=50\" OFF=\"setvar FIXTEST FIXI25T sprung\" ON=\"setvar FIXTEST FIXI25T set\" MSGOFF=\"The pedestal rises with a click!\"");
ped.addNonUninvokableEffect(P);
clearVar("FIXI25T");
out();
CMLib.commands().postGet(V, ped, idol, false);
var said = out();
check("lift", V.isMine(idol) && gvar("FIXI25T") == "sprung" && has(said, "The pedestal rises with a click!"), "carried " + V.isMine(idol) + " '" + gvar("FIXI25T") + "' '" + said + "'");
CMLib.commands().postPut(V, ped, idol, false);
check("back", gvar("FIXI25T") == "set" && idol.container() == ped, "'" + gvar("FIXI25T") + "'");
// sand of equal weight first: no spring
var sand = weighs(CMClass.getItem("GenItem"), 50); sand.setName("a fixi25 bag of sand"); V.addItem(sand);
CMLib.commands().postPut(V, ped, sand, false);
clearVar("FIXI25T");
CMLib.commands().postGet(V, ped, idol, false);
check("sand", gvar("FIXI25T") == "" && V.isMine(idol) && s(P.getStat("DOWN")) == "true", "'" + gvar("FIXI25T") + "' down " + P.getStat("DOWN"));
// a room plate: two to hold the gate, two ticks after one steps off
var RP = CMClass.getAbility("Prop_PressurePlate");
RP.setMiscText("COUNT=\">=2\" ON=\"door open Fixi25Temple#2 north QUIET\" OFF=\"door close Fixi25Temple#2 north QUIET\" DELAY=2");
GATE.addNonUninvokableEffect(RP);
HALL.bringMobHere(V, false);
var pal = CMClass.getMOB("GenMob"); pal.setName("Fixi25pal"); weighs(pal, 70); pal.bringToLife(HALL, true);
CMLib.tracking().walk(V, Dir.EAST, false, false);
var alone = G1.isOpen();
CMLib.tracking().walk(pal, Dir.EAST, false, false);
var both = G1.isOpen() && G2.isOpen();
CMLib.tracking().walk(pal, Dir.WEST, false, false);
var held = G1.isOpen();
RP.tick(RP, TPS); RP.tick(RP, TPS);
check("count", (!alone) && both && held && (!G1.isOpen()) && (!G2.isOpen()), "alone " + alone + " both " + both + " held " + held + " now " + G1.isOpen());
// the functions
var fn = scriptOn(GATE, "FUNCTION_PROG W\nmpsetvar FIXTEST FIXI25R $%ROOMWEIGHT(Fixi25Temple#0)%\nmpsetvar FIXTEST FIXI25C $%CONTENTWEIGHT(stone pedestal)%\n~\n");
SHRINE.bringMobHere(V, false);
var fn2 = scriptOn(SHRINE, "FUNCTION_PROG W\nmpsetvar FIXTEST FIXI25R $%ROOMWEIGHT(here)%\nmpsetvar FIXTEST FIXI25C $%CONTENTWEIGHT(stone pedestal)%\n~\n");
fn2.callFunction(SHRINE, "W", "", V);
check("functions", gvar("FIXI25C") == "50" && CMath.s_int(gvar("FIXI25R")) >= 130 + 50, "room " + gvar("FIXI25R") + " pedestal " + gvar("FIXI25C"));
check("help", has(CMLib.help().getHelpText("PROP_PRESSUREPLATE", M, true), "DELAY") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ROOMWEIGHT("), "no help");

V.setPlayerStats(null); V.destroy(); pal.destroy();
CMLib.map().obliterateMapArea(X);
done();
