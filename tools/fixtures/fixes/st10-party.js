// ST10: PARTY_PROG heard anywhere; PARTYSIZE(), INPARTY(), PARTYLEADER() and PARTY().
FIXFILE = "st10-party";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var R2 = otherRoom();
["FIXST10J", "FIXST10X", "FIXST10S", "FIXST10E"].forEach(clearVar);
var S = scriptOn(R2, "PARTY_PROG JOIN\nmpsetvar FIXTEST FIXST10J $n:$t:$g\n~\nPARTY_PROG LEAVE -NAME +fixsttennobody\nmpsetvar FIXTEST FIXST10X $n\n~\n");
var F = newMob("GenMob", R);
F.setName("Fixsttenhound");
F["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](M);
SEv.party("JOIN", F, M);
runQueued(S);
check("join", gvar("FIXST10J") == "Fixsttenhound:" + M.Name() + ":join", "FIXST10J='" + gvar("FIXST10J") + "'");
SEv.party("LEAVE", F, M);
runQueued(S);
check("mask", gvar("FIXST10X") == "", "FIXST10X='" + gvar("FIXST10X") + "'");
var g = SEv.group(F);
check("group", g.size() == 2 && g.get(0) == M && g.contains(F), "the group is " + g);
var N = "" + M.Name();
var X = scriptedMob("a fixst10 counter", "RAND_PROG 100\nmpsetvar FIXTEST FIXST10S $%PARTYSIZE(" + N + ")%:$%PARTYLEADER(Fixsttenhound)%:$%PARTY(Fixsttenhound)%\n"
	+ "if partysize(" + N + " > 1) and inparty(" + N + " Fixsttenhound) and partyleader(Fixsttenhound == " + N + ")\n  mpsetvar FIXTEST FIXST10E yes\nendif\n~\n");
tickMob(X);
runQueued(X.scripts().nextElement());
check("functions", gvar("FIXST10S") == "2:" + N + ":" + N + " Fixsttenhound" && gvar("FIXST10E") == "yes", "FIXST10S='" + gvar("FIXST10S") + "', FIXST10E='" + gvar("FIXST10E") + "'");
R2.delScript(S);
F.destroy();
X.destroy();
done();
