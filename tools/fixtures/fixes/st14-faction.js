// ST14: FACTION_PROG on a change of standing: any change, up or down, or
// entering a named range; @GLOBAL anywhere.
FIXFILE = "st14-faction";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var R2 = otherRoom();
var F = null;
for (var e = CMLib.factions().factions(); e.hasMoreElements();) { F = e.nextElement(); break; }
check("faction", F != null, "no faction in the world");
if (F != null) {
	["FIXST14A", "FIXST14U", "FIXST14N", "FIXST14G", "FIXST14X"].forEach(clearVar);
	var id = "" + F.factionID();
	// from the faction's least to its most: the ranges there, as the faction finds them
	var lowV = F.minimum(), highV = F.maximum();
	var lowName = "" + F.fetchRange(lowV).name(), highName = "" + F.fetchRange(highV).name();
	var S = scriptOn(R, "FACTION_PROG " + id + " ANY\nmpsetvar FIXTEST FIXST14A $G:$0:$1\n~\n");
	var S2 = scriptOn(CMLib.map().getRoom(CMLib.map().getExtendedRoomID(R)).getArea(), "FACTION_PROG " + id + " UP -NAME +fixstnobody\nmpsetvar FIXTEST FIXST14X $n\n~\nFACTION_PROG " + id + " '" + highName + "'\nmpsetvar FIXTEST FIXST14N $G\n~\n");
	var SG = scriptOn(R2, "FACTION_PROG @GLOBAL " + id + " DOWN\nmpsetvar FIXTEST FIXST14G $n:$0\n~\nFACTION_PROG " + id + " ANY\nmpsetvar FIXTEST FIXST14U local\n~\n");
	var was = M.fetchFaction(id);
	M.addFaction(id, lowV);
	M.adjustFaction(id, highV - lowV);
	runQueued(S); runQueued(S2); runQueued(SG);
	check("any", gvar("FIXST14A") == highName + ":" + (highV - lowV) + ":" + lowName, "FIXST14A='" + gvar("FIXST14A") + "'");
	check("range", gvar("FIXST14N") == highName, "FIXST14N='" + gvar("FIXST14N") + "'");
	check("mask", gvar("FIXST14X") == "", "FIXST14X='" + gvar("FIXST14X") + "'");
	check("not-global", gvar("FIXST14U") == "" && gvar("FIXST14G") == "", "FIXST14U='" + gvar("FIXST14U") + "', FIXST14G='" + gvar("FIXST14G") + "'");
	M.adjustFaction(id, -10);
	runQueued(SG);
	check("global-down", gvar("FIXST14G") == M.Name() + ":-10" && gvar("FIXST14U") == "", "FIXST14G='" + gvar("FIXST14G") + "', FIXST14U='" + gvar("FIXST14U") + "'");
	if (was == java.lang.Integer.MAX_VALUE) M.removeFaction(id); else M.addFaction(id, was);
	R.delScript(S); R.getArea().delScript(S2); R2.delScript(SG);
}
done();
