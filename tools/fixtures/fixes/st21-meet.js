// ST21: FIRSTGREET_PROG at the first meeting ever, REGREET_PROG after so many
// game days, and MEMORY() reading the record, kept in the game's variables.
FIXFILE = "st21-meet";
var R2 = otherRoom();
["FIXST21F", "FIXST21G", "FIXST21M"].forEach(clearVar);
var N = newMob("GenMob", R2);
N.setName("a fixst21 hermit");
var S = scriptOn(N, "FIRSTGREET_PROG 100\nmpsetvar FIXTEST FIXST21F $n\n~\nREGREET_PROG 0\nmpsetvar FIXTEST FIXST21G $n\n~\nRAND_PROG 100\nmpsetvar FIXTEST FIXST21M $%MEMORY($i Fixsttwentyone COUNT)%:$%MEMORY($i Fixsttwentyone MET)%\n~\n");
var P = newMob("StdMOB", R2);
P.setName("Fixsttwentyone");
P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function arrive() { R2.send(P, CMClass.getMsg(P, R2, null, CMMsg.MSG_ENTER, null)); runQueued(S); }
arrive();
check("first", gvar("FIXST21F") == "Fixsttwentyone" && gvar("FIXST21G") == "", "FIXST21F='" + gvar("FIXST21F") + "', FIXST21G='" + gvar("FIXST21G") + "'");
clearVar("FIXST21F");
arrive();
check("again", gvar("FIXST21G") == "Fixsttwentyone" && gvar("FIXST21F") == "", "FIXST21G='" + gvar("FIXST21G") + "', FIXST21F='" + gvar("FIXST21F") + "'");
tickMob(N);
runQueued(S);
check("memory", gvar("FIXST21M") == "2:true", "FIXST21M='" + gvar("FIXST21M") + "'");
P.setPlayerStats(null);
P.destroy(); N.destroy();
done();
