// I9: Prop_Switch VERBS STATES START DESC_x MSG_x ACTION_x RESET REQUIRES/REQMSG ONCE, ACTIVATE/DEACTIVATE,
// the state in its text; SWITCH_PROG and SWITCHSTATE(); core/Actions (MP left off).
FIXFILE = "i9-switch";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi9Gate");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi9Gate#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var YARD = room(0, "Fixi9 yard"), GATE = room(1, "Fixi9 gatehouse"), ROAD = room(2, "Fixi9 road");
function door() { var e = CMClass.getExit("GenExit"); e.setName("a portcullis"); e.setDoorsNLocks(true, false, true, false, false, false); return e; }
var D1 = door(), D2 = door();
GATE.rawDoors()[Dir.EAST] = ROAD; GATE.setRawExit(Dir.EAST, D1);
ROAD.rawDoors()[Dir.WEST] = GATE; ROAD.setRawExit(Dir.WEST, D2);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi9pip"); V.bringToLife(YARD, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
function thing(name, parms) {
	var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " juts from the wall.");
	CMLib.flags().setGettable(I, false); YARD.addItem(I);
	var P = CMClass.getAbility("Prop_Switch"); P.setMiscText(parms); I.addNonUninvokableEffect(P);
	return { I: I, P: P };
}
var lever = thing("a rusted lever", "STATES=\"down,up\" DESC_down=\"The lever is down.\" DESC_up=\"The lever is up.\" "
	+ "MSG_up=\"<S-NAME> heave(s) <T-NAME> up; somewhere a portcullis grinds.\" "
	+ "ACTION_up=\"door open Fixi9Gate#1 east QUIET; setvar FIXTEST FIXI9A up\" ACTION_down=\"door close Fixi9Gate#1 east QUIET; setvar FIXTEST FIXI9A down\" RESET=3");
scriptOn(YARD, "SWITCH_PROG up\nmpsetvar FIXTEST FIXI9P $0:$n\n~\n");
var fn = scriptOn(YARD, "FUNCTION_PROG RD\nmpsetvar FIXTEST FIXI9S $%SWITCHSTATE(rusted lever)%\n~\n");
["FIXI9A", "FIXI9P", "FIXI9S"].forEach(clearVar);
var r = cmd("pull lever");
check("pull", r.took && s(lever.P.getStat("STATE")) == "up" && D1.isOpen() && D2.isOpen() && gvar("FIXI9A") == "up" && has(r.said, "somewhere a portcullis grinds"),
	"took " + r.took + " state " + lever.P.getStat("STATE") + " gate " + D1.isOpen() + "/" + D2.isOpen() + " '" + r.said + "'");
check("prog", gvar("FIXI9P") == "up:Fixi9pip", "SWITCH_PROG '" + gvar("FIXI9P") + "'");
fn.callFunction(YARD, "RD", "", V);
check("function", gvar("FIXI9S") == "up", "SWITCHSTATE '" + gvar("FIXI9S") + "'");
check("kept", has(lever.P.text(), "STATE=up"), "text " + lever.P.text());
out(); YARD.send(V, CMClass.getMsg(V, lever.I, null, CMMsg.MSG_LOOK, null));
check("look", has(out(), "The lever is up."), "LOOK does not say");
// it goes back after RESET ticks
lever.P.tick(lever.P, TPS); lever.P.tick(lever.P, TPS);
var still = s(lever.P.getStat("STATE"));
lever.P.tick(lever.P, TPS);
check("reset", still == "up" && s(lever.P.getStat("STATE")) == "down" && (!D1.isOpen()) && (!D2.isOpen()) && gvar("FIXI9A") == "down",
	"after 2 " + still + " after 3 " + lever.P.getStat("STATE") + " gate " + D1.isOpen());
// a fresh copy keeps its state from the text
var copy = CMClass.getAbility("Prop_Switch"); copy.setMiscText(s(lever.P.text()) + " STATE=up");
check("restore", s(copy.getStat("STATE")) == "up", "copy " + copy.getStat("STATE"));
// a dial
var dial = thing("a brass dial", "VERBS=turn STATES=1-8 START=1");
r = cmd("turn dial to 5");
var five = s(dial.P.getStat("STATE"));
var r2 = cmd("turn dial to 9");
r = cmd("turn dial");
check("dial", five == "5" && has(r2.said, "no '9'") && s(dial.P.getStat("STATE")) == "6", "five " + five + " now " + dial.P.getStat("STATE") + " '" + r2.said + "'");
// REQUIRES and ONCE
var crank = thing("an iron crank", "REQUIRES=\"-NAMES +Fixi9bo\" REQMSG=\"It will not budge without the crank handle.\"");
r = cmd("pull crank");
check("requires", r.took && s(crank.P.getStat("STATE")) == "down" && has(r.said, "will not budge"), "state " + crank.P.getStat("STATE") + " '" + r.said + "'");
var button = thing("a stone button", "VERBS=press STATES=\"out,in\" ONCE=true REQMSG=\"The button stays in.\"");
cmd("press button");
r = cmd("press button");
check("once", s(button.P.getStat("STATE")) == "in" && has(r.said, "stays in"), "state " + button.P.getStat("STATE") + " '" + r.said + "'");
// ACTIVATE and DEACTIVATE, and lines that are not its
r = cmd("activate lever");
var on = s(lever.P.getStat("STATE"));
cmd("deactivate lever");
var other = cmd("pull nothingness");
check("activate", on == "up" && s(lever.P.getStat("STATE")) == "down" && (!other.took), "on " + on + " off " + lever.P.getStat("STATE") + " other " + other.took);
var AL = s(CM.core.Actions.lines("door open here north; signal WORLD x; MPECHO hi; IF VAR(a b c)")).toUpperCase();
check("actions", has(AL, "MPDOOR OPEN HERE NORTH MPSIGNAL WORLD X MPECHO HI IF VAR"), AL);
check("help", has(CMLib.help().getHelpText("PROP_SWITCH", M, true), "ACTION_") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "SWITCH_PROG"), "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
done();
