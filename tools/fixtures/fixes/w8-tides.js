// W8: ISTIDE(), +TIDE and -TIDE, <HIGHTIDE> and <LOWTIDE>, Prop_TidalExit, TidalRoom.
FIXFILE = "w8-tides";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var Tides = CM.core.Tides;
var TRB = Tickable.TICKID_ROOM_BEHAVIOR;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw8Isle");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var TC = CMLib.time().globalClock().copyOf(); X.setTimeObj(TC);
var dtd = TC.getDawnToDusk();
function hour(h) { TC.setHourOfDay((h + TC.getHoursInDay()) % TC.getHoursInDay()); }
function room(cls, i, name) { var r = CMClass.getLocale(cls); r.setRoomID("Fixw8Isle#" + i); r.setDisplayText(name); r.setArea(X); return r; }
function link(a, d, b) {
	var e1 = CMClass.getExit("Open"), e2 = CMClass.getExit("Open");
	a.rawDoors()[d] = b; a.setRawExit(d, e1); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), e2);
	return e1;
}
var SHORE = room("Shore", 0, "Fixw8 shore"), ISLE = room("Plains", 1, "Fixw8 isle");
var causeway = link(SHORE, Dir.EAST, ISLE);
function probe(R) {
	clearVar("W8H"); clearVar("W8L");
	var m = newMob("GenMob", R); m.setName("Fixw8clerk");
	scriptOn(m, "RAND_PROG 100\nif istide(HERE HIGH)\n mpsetvar FIXTEST W8H yes\nendif\nif istide(HERE LOW)\n mpsetvar FIXTEST W8L yes\nendif\n~\n");
	tickMob(m); m.destroy();
	return gvar("W8H") + "/" + gvar("W8L");
}
hour(dtd[2]); var atHigh = probe(SHORE), highNow = Tides.is(SHORE, "HIGH");
hour(dtd[1]); var atLow = probe(SHORE);
hour(dtd[2] - 1); var between = probe(SHORE), spanned = Tides.is(SHORE, "HIGH:1");
check("istide", highNow && atHigh == "yes/" && atLow == "/yes" && between == "/" && spanned, "high " + atHigh + " low " + atLow + " between " + between + " span " + spanned);
// masks and VARIES
var V = CMClass.getMOB("GenMob"); V.setName("Fixw8wader"); V.bringToLife(SHORE, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var MK = CMLib.masking();
hour(dtd[2]);
var mHigh = MK.maskCheck("-TIDE +HIGH", V, true) && (!MK.maskCheck("+TIDE -HIGH", V, true));
var text = "<VARIES><HIGHTIDE>The sea covers the causeway.</HIGHTIDE><LOWTIDE>The causeway is bare.</LOWTIDE></VARIES>";
var vHigh = s(CMLib.commands().parseVaries(V, X, SHORE, text));
hour(dtd[1]);
var mLow = (!MK.maskCheck("-TIDE +HIGH", V, true)) && MK.maskCheck("-TIDE +LOW", V, true);
var vLow = s(CMLib.commands().parseVaries(V, X, SHORE, text));
check("masks-varies", mHigh && mLow && has(vHigh, "The sea covers") && (!has(vHigh, "bare")) && has(vLow, "The causeway is bare."), "masks " + mHigh + "/" + mLow + " varies '" + vHigh + "' / '" + vLow + "'");
// Prop_TidalExit: the causeway is open only at low tide
var TE = CMClass.getAbility("Prop_TidalExit"); TE.setMiscText("OPEN=LOW MSG=\"The fixw8 causeway is under water.\""); causeway.addNonUninvokableEffect(TE);
hour(dtd[2]);
CMLib.tracking().walk(V, Dir.EAST, false, false);
var stayed = V.location() == SHORE && has(field(VS, "bout").toString(), "The fixw8 causeway is under water.");
hour(dtd[1]);
CMLib.tracking().walk(V, Dir.EAST, false, false);
check("tidalexit", stayed && V.location() == ISLE, "stayed " + stayed + " now in " + V.location().displayText());
// TidalRoom: flooded at high tide, light things washed away; dry again at the ebb
var TR = CMClass.getBehavior("TidalRoom");
TR.setParms("WASH=2 DESC=\"The sea fills the fixw8 shore.\" FLOODMSG=\"You wade into the fixw8 tide.\"");
var oldDesc = s(SHORE.description());
SHORE.addBehavior(TR);
var shell = CMClass.getItem("GenItem"); shell.setName("a fixw8 shell"); shell.basePhyStats().setWeight(1); shell.recoverPhyStats(); SHORE.addItem(shell);
hour(dtd[2]); TR.tick(SHORE, TRB);
var flooded = s(TR.currentState()) == "flooded" && s(SHORE.description()) == "The sea fills the fixw8 shore." && SHORE.fetchEffect("Chant_Flood") != null && shell.amDestroyed();
clearVar("W8S");
var m = newMob("GenMob", SHORE); m.setName("Fixw8watcher");
scriptOn(m, "RAND_PROG 100\nmpsetvar FIXTEST W8S $%ROOMSTATE(HERE)%\n~\n"); tickMob(m); m.destroy();
hour(dtd[1]); TR.tick(SHORE, TRB);
check("tidalroom", flooded && gvar("W8S") == "flooded" && s(TR.currentState()) == "ebb" && s(SHORE.description()) == oldDesc && SHORE.fetchEffect("Chant_Flood") == null,
	"flooded " + flooded + " state " + gvar("W8S") + " now " + TR.currentState() + " desc " + SHORE.description());
check("help", has(CMLib.help().getHelpText("PROP_TIDALEXIT", M, true), "OPEN=") && has(CMLib.help().getHelpText("BEHAVIOR_TIDALROOM", M, true), "WASH")
	&& has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ISTIDE("), "no help");
V.setSession(null);
[SHORE, ISLE].forEach(function (r) { for (var i = r.numInhabitants() - 1; i >= 0; i--) { var mm = r.fetchInhabitant(i); if (mm != null) mm.destroy(); } });
CMLib.map().delArea(X);
done();
