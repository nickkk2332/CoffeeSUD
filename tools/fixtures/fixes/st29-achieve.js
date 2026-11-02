// ST29: ACHIEVE_PROG anywhere when an achievement is earned.
FIXFILE = "st29-achieve";
var SEv = CM.core.ScriptEvents;
var R2 = otherRoom();
["FIXST29A", "FIXST29X"].forEach(clearVar);
var S = scriptOn(R2, "ACHIEVE_PROG FIXWOLFBANE\nmpsetvar FIXTEST FIXST29A $n:$G\n~\nACHIEVE_PROG * -NAME +fixstnobody\nmpsetvar FIXTEST FIXST29X $n\n~\n");
SEv.achieved(M, "FIXWOLFBANE");
runQueued(S);
check("achieve", gvar("FIXST29A") == M.Name() + ":FIXWOLFBANE" && gvar("FIXST29X") == "", "FIXST29A='" + gvar("FIXST29A") + "', FIXST29X='" + gvar("FIXST29X") + "'");
R2.delScript(S);
done();
