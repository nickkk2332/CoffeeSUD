// ST9: CLAN_PROG for joining, promotion, demotion, relations and leaving;
// every join and leave runs the government's entry and exit scripts.
FIXFILE = "st9-clan";
var SEv = CM.core.ScriptEvents;
var R = M.location();
["FIXST9J", "FIXST9P", "FIXST9D", "FIXST9L", "FIXST9R", "FIXST9ENTRY", "FIXST9EXIT", "FIXST9X"].forEach(clearVar);
var C = CMClass.getCommon("DefaultClan");
C.setName("FixST9Clan");
C.setGovernmentID(0);
C.setRecall(CMLib.map().getExtendedRoomID(R));
CMLib.clans().addClan(C);
var G = C.getGovernment();
var entryWas = G.getEntryScript(), exitWas = G.getExitScript();
G.setEntryScript("ONCE_PROG\nmptattoo $n FIXSTNINEIN\n~\n");
G.setExitScript("ONCE_PROG\nmptattoo $n FIXSTNINEOUT\n~\n");
var S = scriptOn(R, "CLAN_PROG JOIN FixST9Clan\nmpsetvar FIXTEST FIXST9J $n:$1\n~\nCLAN_PROG PROMOTE\nmpsetvar FIXTEST FIXST9P $G\n~\nCLAN_PROG DEMOTE ALL\nmpsetvar FIXTEST FIXST9D $G\n~\n"
	+ "CLAN_PROG LEAVE\nmpsetvar FIXTEST FIXST9L $n\n~\nCLAN_PROG RELATION\nmpsetvar FIXTEST FIXST9R $G:$3\n~\nCLAN_PROG JOIN nosuchfixclan\nmpsetvar FIXTEST FIXST9X $n\n~\n");
var accept = G.getAcceptPos();
var rank = function(role) { var P = G.getPositions(); for (var i = 0; i < P.length; i++) if (P[i].getRoleID() == role) return P[i].getRank(); return 99; };
var higher = -1;
var P = G.getPositions();
for (var i = 0; i < P.length; i++) if (P[i].getRank() < rank(accept) && (higher < 0 || P[i].getRank() > rank(higher))) higher = P[i].getRoleID();
C.addMember(M, accept);
runQueued(S);
check("join", gvar("FIXST9J") == M.Name() + ":" + C.clanID() && gvar("FIXST9X") == "", "FIXST9J='" + gvar("FIXST9J") + "', FIXST9X='" + gvar("FIXST9X") + "'");
check("entry-script", M.findTattoo("FIXSTNINEIN") != null, "no FIXSTNINEIN tattoo");
if (higher >= 0) {
	C.addMember(M, higher);
	runQueued(S);
	check("promote", gvar("FIXST9P") == "" + C.getRoleName(higher, true, false), "FIXST9P='" + gvar("FIXST9P") + "'");
	C.addMember(M, accept);
	runQueued(S);
	check("demote", gvar("FIXST9D") == "" + C.getRoleName(accept, true, false), "FIXST9D='" + gvar("FIXST9D") + "'");
}
var Clan = CM.Common.interfaces.Clan;
C.setClanRelations("FixST9Other", Clan.REL_WAR, java.lang.System.currentTimeMillis());
runQueued(S);
check("relation", gvar("FIXST9R") == Clan.REL_DESCS[Clan.REL_WAR] + ":FixST9Other", "FIXST9R='" + gvar("FIXST9R") + "'");
C.delMember(M);
runQueued(S);
check("leave", gvar("FIXST9L") == M.Name() && M.findTattoo("FIXSTNINEOUT") != null && M.getClanRole(C.clanID()) == null, "FIXST9L='" + gvar("FIXST9L") + "', exit tattoo " + M.findTattoo("FIXSTNINEOUT"));
M.delTattoo(M.findTattoo("FIXSTNINEIN"));
M.delTattoo(M.findTattoo("FIXSTNINEOUT"));
G.setEntryScript(entryWas);
G.setExitScript(exitWas);
R.delScript(S);
CMLib.clans().removeClan(C);
done();
