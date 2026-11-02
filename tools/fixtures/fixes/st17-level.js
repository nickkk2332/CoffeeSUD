// ST17: LEVEL_PROG GAIN (as before), LOSS and ANY, with the new level in $g,
// once though heard from the room and the world.
FIXFILE = "st17-level";
var R = M.location();
var R2 = otherRoom();
["FIXST17G", "FIXST17L", "FIXST17C", "FIXST17A"].forEach(clearVar);
var S = scriptOn(R2, "LEVEL_PROG 100\nmpsetvar FIXTEST FIXST17G $g:$1\n~\n");
var SL = scriptOn(otherRoom().getArea(), "LEVEL_PROG LOSS\nmpsetvar FIXTEST FIXST17L $g\n~\n");
var SC = scriptOn(R, "LEVEL_PROG ANY\nmpsetvar FIXTEST FIXST17C ++\n~\n");
var lv = M.basePhyStats().level();
var up = CMClass.getMsg(M, null, null, CMMsg.MSG_LEVEL, null);
up.setValue(lv + 1);
R.send(M, up);
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LEVEL, up);
runQueued(S); runQueued(SL); runQueued(SC);
check("gain", gvar("FIXST17G") == (lv + 1) + ":100" && gvar("FIXST17L") == "", "FIXST17G='" + gvar("FIXST17G") + "', FIXST17L='" + gvar("FIXST17L") + "'");
check("once", gvar("FIXST17C") == "1", "FIXST17C='" + gvar("FIXST17C") + "'");
clearVar("FIXST17G");
var down = CMClass.getMsg(M, null, null, CMMsg.MSG_LEVEL, null);
down.setValue(lv - 1);
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LEVEL, down);
runQueued(S); runQueued(SL); runQueued(SC);
check("loss", gvar("FIXST17L") == "" + (lv - 1) && gvar("FIXST17G") == "", "FIXST17L='" + gvar("FIXST17L") + "', FIXST17G='" + gvar("FIXST17G") + "'");
check("any", gvar("FIXST17C") == "2", "FIXST17C='" + gvar("FIXST17C") + "'");
R2.delScript(S); otherRoom().getArea().delScript(SL); R.delScript(SC);
done();
