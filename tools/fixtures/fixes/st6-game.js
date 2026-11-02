// ST6: GAME_PROG from PAUSE, RESUME and TIMEMODE, and SAVE (which a script
// may refuse); TURN_PROG every N turns; ISTURNBASED(), ISPAUSED(), TURNS(),
// GAMETIME() and SAVESLOT().
FIXFILE = "st6-game";
var SEv = CM.core.ScriptEvents;
var GT = CM.core.GameTime;
var R = M.location();
var R2 = otherRoom();
function list(s) { var L = new java.util.ArrayList(); var w = s.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
["FIXST6P", "FIXST6R", "FIXST6T", "FIXST6S", "FIXST6N", "FIXST6F", "FIXST6E", "FIXST6X"].forEach(clearVar);
var S = scriptOn(R2, "GAME_PROG PAUSE\nmpsetvar FIXTEST FIXST6P $n:$G\n~\nGAME_PROG RESUME\nmpsetvar FIXTEST FIXST6R $G\n~\n"
	+ "GAME_PROG TIMEMODE\nmpsetvar FIXTEST FIXST6T $1\n~\nGAME_PROG NEWCHAR -NAME +fixst6nobody\nmpsetvar FIXTEST FIXST6X $n\n~\n"
	+ "GAME_PROG SAVE\nmpsetvar FIXTEST FIXST6S $g\nif eval('$g' == 'fixst6refuse')\n  return cancel\nendif\n~\n");
var wasTurns = GT.isTurnBased();
CMClass.getCommand("TimeMode").execute(M, list("TIMEMODE REALTIME"), 0);
runQueued(S);
check("timemode", gvar("FIXST6T") == "REALTIME" && !GT.isTurnBased(), "FIXST6T='" + gvar("FIXST6T") + "'");
CMClass.getCommand("Pause").execute(M, list("PAUSE"), 0);
runQueued(S);
check("pause", gvar("FIXST6P") == M.Name() + ":PAUSE" && GT.isPaused(), "FIXST6P='" + gvar("FIXST6P") + "', paused " + GT.isPaused());
// a script asks while paused
var asker = scriptedMob("a fixst6 asker", "RAND_PROG 100\nmpsetvar FIXTEST FIXST6F $%ISTURNBASED()%:$%ISPAUSED()%:$%TURNS()%\nif turns(>= 0) and gametime(> 0)\n  mpsetvar FIXTEST FIXST6E yes\nendif\n~\n");
tickMob(asker);
runQueued(asker.scripts().nextElement());
check("functions", gvar("FIXST6F") == "false:true:" + SEv.turns() && gvar("FIXST6E") == "yes", "FIXST6F='" + gvar("FIXST6F") + "', FIXST6E='" + gvar("FIXST6E") + "'");
CMClass.getCommand("Pause").execute(M, list("RESUME"), 0);
runQueued(S);
check("resume", gvar("FIXST6R") == "RESUME" && !GT.isPaused(), "FIXST6R='" + gvar("FIXST6R") + "'");
CMClass.getCommand("TimeMode").execute(M, list("TIMEMODE TURNS"), 0);
runQueued(S);
check("timemode-back", gvar("FIXST6T") == "TURNS" && GT.isTurnBased(), "FIXST6T='" + gvar("FIXST6T") + "'");
if (!wasTurns) GT.setTurnBased(false);

// SAVE: run at once; a script may refuse it
check("save-ok", SEv.gameCheck(M, "fixst6slot") && gvar("FIXST6S") == "fixst6slot", "FIXST6S='" + gvar("FIXST6S") + "'");
check("save-refused", !SEv.gameCheck(M, "fixst6refuse") && gvar("FIXST6S") == "fixst6refuse", "FIXST6S='" + gvar("FIXST6S") + "'");
// the mask: not for this character
SEv.game("NEWCHAR", M, "");
runQueued(S);
check("mask", gvar("FIXST6X") == "", "FIXST6X='" + gvar("FIXST6X") + "'");

// TURN_PROG 3, in the player's area
var ST = scriptOn(R, "TURN_PROG 3\nmpsetvar FIXTEST FIXST6N $g\n~\n");
var before = SEv.turns();
for (var i = 0; i < 3 && gvar("FIXST6N") == ""; i++) { SEv.turn(M); runQueued(ST); }
check("turn", gvar("FIXST6N") == "" + SEv.turns() && (SEv.turns() % 3) == 0 && SEv.turns() > before, "FIXST6N='" + gvar("FIXST6N") + "', turns " + before + " to " + SEv.turns());
// away from the player's area, nothing
var A2 = CMClass.getAreaType("StdArea");
A2.setName("FixST6 Elsewhere");
CMLib.map().addArea(A2);
var X = CMClass.getLocale("StdRoom");
X.setRoomID("FixST6 Elsewhere#0");
X.setArea(A2);
var SX = scriptOn(X, "TURN_PROG\nmpsetvar FIXTEST FIXST6X elsewhere\n~\n");
SEv.turn(M);
runQueued(SX);
check("turn-area", gvar("FIXST6X") == "", "FIXST6X='" + gvar("FIXST6X") + "'");
CMLib.map().obliterateMapArea(A2);
R.delScript(ST);
R2.delScript(S);
asker.destroy();
done();
