// ST24: EVERY_PROG on the area's clock, catching up on missed intervals, its
// next time in the engine's saved state.
FIXFILE = "st24-every";
var R = M.location();
var TC = R.getArea().getTimeObj();
clearVar("FIXST24E");
var S = scriptOn(R, "EVERY_PROG 2 HOURS CATCHUP\nmpsetvar FIXTEST FIXST24E $g\n~\n");
S.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
check("not-yet", gvar("FIXST24E") == "", "FIXST24E='" + gvar("FIXST24E") + "'");
check("saved", ("" + S.getStateXML()).indexOf("<EVERY>") >= 0, "state " + S.getStateXML());
TC.tickTock(5);
S.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
check("caught-up", gvar("FIXST24E") == "2", "FIXST24E='" + gvar("FIXST24E") + "'");
clearVar("FIXST24E");
S.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
check("once", gvar("FIXST24E") == "", "FIXST24E='" + gvar("FIXST24E") + "'");
TC.tickTock(-5);
R.delScript(S);
done();
