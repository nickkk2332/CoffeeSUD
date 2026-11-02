// S1, after the restore: the quest's variables, offers and wait came back
FIXFILE = "s1-check";
var Q = CMLib.quests().fetchQuest("s1quest");
check("quest-running", Q.running(), "s1quest is not running");
check("walls-kept", Q.getStat("WALLS") == "2", "WALLS is '" + Q.getStat("WALLS") + "'");
var offered = "" + Q.getStat("OFFERED:" + M.Name().toUpperCase()); // Q7: when, on the game clock
check("offer-kept", offered.length > 0 && CMath.isLong(offered), "OFFERED var is '" + offered + "'");
check("chat-kept", ("" + Q.getStat("CHAT:" + M.Name().toUpperCase())).indexOf("greet") >= 0, "no CHAT var");
var W = CMLib.quests().fetchQuest("s1wait");
var saved = CMath.s_int(gvar("S1WAIT"));
var now = W.waitRemaining();
check("wait-kept", (saved > 0) && (now > 0) && (saved - now >= 0) && (saved - now <= 40), "saved " + saved + ", now " + now);
done();
