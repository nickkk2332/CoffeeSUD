// ST5: QUEST_PROG fires anywhere for a quest's start, step, win, fail and
// end; $g is the quest, $0 the step or outcome, $n the player or "the quest".
FIXFILE = "st5-quest";
var SEv = CM.core.ScriptEvents;
var R2 = otherRoom();
var A = M.location().getArea();
function list(s) { var L = new java.util.ArrayList(); var w = s.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
["FIXST5S", "FIXST5P", "FIXST5W", "FIXST5F", "FIXST5E", "FIXST5D", "FIXST5X"].forEach(clearVar);
// hosts that have heard nothing, away from the player
var SR = scriptOn(R2, "QUEST_PROG START st5quest\nmpsetvar FIXTEST FIXST5S $g:$n\n~\nQUEST_PROG START nosuchfixquest\nmpsetvar FIXTEST FIXST5X $g\n~\n");
var H = newMob("GenMob", R2);
H.setName("a fixst5 crier");
var B = CMClass.getBehavior("Scriptable");
B.setParms("QUEST_PROG STEP st5quest 2\nmpsetvar FIXTEST FIXST5P $0\n~\nQUEST_PROG WIN *\nmpsetvar FIXTEST FIXST5W $n\n~\n");
H.addBehavior(B);
var SA = scriptOn(A, "QUEST_PROG FAIL st5quest\nmpsetvar FIXTEST FIXST5F $0\n~\nQUEST_PROG END st5quest\nmpsetvar FIXTEST FIXST5E $0:$n\n~\n");
function runAll() { runQueued(SR); runQueued(SA); for (var i = 0; i < 4; i++) B.dequeResponses(null); }
check("registered", SEv.registered(SEv.QUEST_PROG).contains(R2) && SEv.registered(SEv.QUEST_PROG).contains(H) && SEv.registered(SEv.QUEST_PROG).contains(A), "" + SEv.registered(SEv.QUEST_PROG));

CMClass.getCommand("Create").execute(M, list("CREATE QUEST LOAD=_fixes/st5.quest"), 0);
var Q = CMLib.quests().fetchQuest("st5quest");
check("made", Q != null, "no quest st5quest");
if (Q != null) {
	if (Q.running()) Q.stopQuest();
	clearVar("FIXST5E"); clearVar("FIXST5F");
	Q.startQuest();
	runAll();
	check("start", Q.running() && gvar("FIXST5S") == "st5quest:the quest" && gvar("FIXST5X") == "", "running " + Q.running() + ", FIXST5S='" + gvar("FIXST5S") + "', FIXST5X='" + gvar("FIXST5X") + "'");
	Q.stepQuest();
	runAll();
	check("step", gvar("FIXST5P") == "2", "FIXST5P='" + gvar("FIXST5P") + "', step " + Q.getStat("STEPNUM"));
	Q.declareWinner(M.Name());
	runAll();
	check("win", gvar("FIXST5W") == M.Name(), "FIXST5W='" + gvar("FIXST5W") + "'");
	Q.stepQuest(); // past the last step: the quest ends
	runAll();
	check("end-won", !Q.running() && gvar("FIXST5E") == "WON:" + M.Name() && gvar("FIXST5F") == "", "FIXST5E='" + gvar("FIXST5E") + "', FIXST5F='" + gvar("FIXST5F") + "'");
	// again, and it runs out with no winner
	Q.declareWinner("-" + M.Name());
	["FIXST5E", "FIXST5F", "FIXST5S"].forEach(clearVar);
	Q.startQuest();
	var f = null;
	for (var c = Q.getClass(); c != null && f == null; c = c.getSuperclass()) { try { f = c.getDeclaredField("ticksRemaining"); } catch (e) { } }
	f.setAccessible(true);
	f.setInt(Q, 0);
	Q.tick(Q, Tickable.TICKID_QUEST);
	runAll();
	check("expired", !Q.running() && gvar("FIXST5F") == "EXPIRED" && gvar("FIXST5E") == "EXPIRED:the quest", "running " + Q.running() + ", FIXST5F='" + gvar("FIXST5F") + "', FIXST5E='" + gvar("FIXST5E") + "'");
	// dropped by a player, as QUESTS DROP tells it
	clearVar("FIXST5E");
	SEv.quest(Q, "END", M, "DROPPED");
	runAll();
	check("dropped", gvar("FIXST5E") == "DROPPED:" + M.Name(), "FIXST5E='" + gvar("FIXST5E") + "'");
	CMLib.quests().delQuest(Q);
}
R2.delScript(SR);
A.delScript(SA);
H.destroy();
done();
