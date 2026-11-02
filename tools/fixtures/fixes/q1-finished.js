// Q1: a quest with no schedule stays known when it stops, FINISHED, with its winners;
// SET RESTART NEVER|ALWAYS|<game time>, SET ENDONWIN TRUE, SET AUTOSTART BOOT, MODIFY QUEST REOPEN.
FIXFILE = "q1-finished";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function run(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(M, L, 0); }
function quest(name, extra) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript("set name " + name + "\nset duration 1000\n" + (extra || "") + "set room any\nstep break\n", true);
	CMLib.quests().addQuest(Q);
	Q.autostartup();
	return Q;
}
function fin(Q) { return s(Q.getStat("FINISHED")) == "true"; }
function tickQ(Q, n) { for (var i = 0; i < n; i++) Q.tick(Q, Tickable.TICKID_QUEST); }

// no schedule, no settings: kept, finished, with its winner; it may start again
var A = quest("fixq1a");
check("idle", !A.running() && !A.waiting() && !fin(A), "running " + A.running() + ", waiting " + A.waiting());
A.startQuest();
A.declareWinner(M.Name());
A.stopQuest();
check("kept", CMLib.quests().fetchQuest("fixq1a") == A && fin(A) && A.wasWinner(M.Name()) && !A.waiting(),
	"listed " + (CMLib.quests().fetchQuest("fixq1a") == A) + ", finished " + fin(A) + ", winner " + A.wasWinner(M.Name()));
check("inspection", has(A.inspection(), "Quest 'fixq1a': finished"), A.inspection());
check("status", s(new CM.core.JSQuests.Helper().status("fixq1a")) == "finished", s(new CM.core.JSQuests.Helper().status("fixq1a")));
A.startQuest();
check("again", A.running() && !fin(A), "running " + A.running() + ", finished " + fin(A));
A.stopQuest();

// RESTART NEVER and ENDONWIN TRUE: the win ends it, and it does not start again until REOPEN
var B = quest("fixq1b", "set restart never\nset endonwin true\n");
check("settings", s(B.getStat("RESTART")) == "NEVER" && s(B.getStat("ENDONWIN")) == "true" && has(B.inspection(), "Settings: RESTART NEVER, ENDONWIN TRUE"),
	"RESTART '" + B.getStat("RESTART") + "', ENDONWIN '" + B.getStat("ENDONWIN") + "'");
B.startQuest();
var wasOn = B.running();
B.declareWinner(M.Name());
check("endonwin", wasOn && !B.running() && fin(B) && B.wasWinner(M.Name()), "was " + wasOn + ", running " + B.running() + ", finished " + fin(B));
B.startQuest();
check("never", !B.running() && fin(B), "running " + B.running());
run("Modify", "MODIFY QUEST fixq1b REOPEN");
check("reopen", !fin(B) && B.startQuest() && B.running(), "finished " + fin(B) + ", running " + B.running());
B.stopQuest();

// the save keeps FINISHED
var WS = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState");
var xml = s(callTyped(null, WS, "questsXML", ["MOB"], [M]));
var at = xml.indexOf("<NAME>fixq1b</NAME>");
var from = xml.lastIndexOf("<QUEST", at), to = xml.indexOf("</QUEST>", at) + 8;
var mine = (at < 0) ? "" : xml.substring(from, to);
check("saved", has(mine, "<FINISHED>true</FINISHED>") && has(mine, "<WINNERS>"), mine);
run("Modify", "MODIFY QUEST fixq1b REOPEN");
var tags = CMLib.xml().parseAllXML("<QUESTS>" + mine + "</QUESTS>");
callTyped(null, WS, "setQuests", ["XMLTag"], [tags.get(0)]);
check("restored", fin(B) && B.wasWinner(M.Name()) && !B.running(), "finished " + fin(B) + ", winner " + B.wasWinner(M.Name()));

// RESTART <game time>: finished, and it starts by itself that long after, not before
var C = quest("fixq1c", "set restart 5\n");
C.startQuest();
C.stopQuest();
check("restart-wait", fin(C) && C.waiting() && C.waitRemaining() == 5 && has(C.inspection(), "finished, starts again"), "finished " + fin(C) + ", wait " + C.waitRemaining());
C.startQuest();
check("restart-early", !C.running(), "started before its time");
tickQ(C, 7);
check("restart-time", C.running() && !fin(C), "running " + C.running() + ", wait " + C.waitRemaining());
C.stopQuest();

// AUTOSTART BOOT: waits a tick, starts, and once over is finished, not started again
var D = quest("fixq1d", "set autostart boot\n");
check("autostart-wait", D.waiting() && D.waitRemaining() == 1 && s(D.getStat("AUTOSTART")) == "BOOT", "wait " + D.waitRemaining());
tickQ(D, 3);
check("autostart", D.running(), "not started, wait " + D.waitRemaining());
D.stopQuest();
D.autostartup();
check("autostart-once", fin(D) && !D.waiting() && !D.running(), "finished " + fin(D) + ", wait " + D.waitRemaining());

// only DESTROY QUEST takes one away
run("Destroy", "DESTROY QUEST fixq1a");
check("destroy", CMLib.quests().fetchQuest("fixq1a") == null, "still listed");
[B, C, D].forEach(function(Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
done();
