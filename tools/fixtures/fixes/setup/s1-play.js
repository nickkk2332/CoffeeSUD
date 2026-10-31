// S1, in play: starts s1quest, brings its herald here, sets a quest variable
// twice with MPQSET, notes s1wait's wait, and gives the player a savable
// script with a ONCE_PROG
FIXFILE = "s1-play";
var Q = CMLib.quests().fetchQuest("s1quest");
if (!Q.running()) Q.startQuest();
check("quest-running", Q.running(), "s1quest did not start");
var herald = Q.getQuestMob(1);
check("herald", herald != null, "the quest has no mob");
if (herald != null) M.location().bringMobHere(herald, false);
var setter = scriptedMob("a wall breaker", "ONCE_PROG\nMPQSET s1quest WALLS ++\nMPQSET s1quest WALLS ++\n~\n");
tickMob(setter);
setter.destroy();
check("walls-set", Q.getStat("WALLS") == "2", "WALLS is " + Q.getStat("WALLS"));
var W = CMLib.quests().fetchQuest("s1wait");
check("wait-quest", (W != null) && !W.running() && (W.waitRemaining() > 0), "s1wait is not waiting: " + (W == null ? "missing" : W.waitRemaining()));
VARS.setVar("FIXTEST", "S1WAIT", "" + W.waitRemaining());
var S = CMClass.getCommon("DefaultScriptingEngine");
S.setSavable(true);
S.setScript("ONCE_PROG\nmpechoat $i FIXONCE the player's script ran.\n~\n");
M.addScript(S);
done();
