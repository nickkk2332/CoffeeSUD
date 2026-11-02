// Q11: a character's own quest deadline: MPQTIMER who quest <game time>|OFF, kept in their journal (and so the save),
// shown by QUESTS; when it comes, QUESTTIMEOUT_PROG on their scripts, or with none the quest fails, EXPIRED.
FIXFILE = "q11-deadline";
var R = M.location();
var QR = CM.core.QuestRecords;
var QJ = CM.core.QuestJournal;
var QT = CM.core.QuestTimers;
var GT = CM.core.GameTime;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq11ava");
var VS = CMClass.getCommon("FakeSession");
VS.initialize(null, "MEMORY");
V.setSession(VS);
VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var g = CMClass.getMOB("GenMob"); g.setName("a fixq11 courier"); CMLib.catalog().addCatalog(g); g.destroy();
function quest(name, track) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(["set name " + name, "set duration 2000", "set room any", "load mob catalog a fixq11 courier", "give script LOAD=" + name + "giver.script",
		"<?xml version=\"1.0\"?>",
		"<FILE><NAME>" + name + "giver.script</NAME><DATA>",
		"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
		"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=" + name + "track.script", "~",
		"</DATA></FILE>",
		"<FILE><NAME>" + name + "track.script</NAME><DATA>", track || "ONCE_PROG 100\n\tmpsetvar $i FIXQ11 on\n~", "</DATA></FILE>", ""].join("\n"), true);
	CMLib.quests().addQuest(Q);
	Q.startQuest();
	Q.acceptQuest(V);
	return Q;
}
function carries(Q) { return CM.core.QuestMarkers.accepted(V, Q); }
function run(text) { var m = scriptedMob("a fixq11 clerk", "RAND_PROG 100\n" + text + "\n~\n"); tickMob(m); m.destroy(); }
function due(name) { return QJ.timer(V, name); }
var A = quest("fixq11a");
var B = quest("fixq11b", "QUESTTIMEOUT_PROG\n\tmpechoat $n fixq11b ran late for $0\n\tmpqtimer $n fixq11b 2 mudhours\n~");
var C = quest("fixq11c");
scriptOn(V, "QUESTTIMEOUT_PROG fixq11c\nmpechoat $n fixq11c heard $0\n~\n"); // named, on a script that is no quest's
check("accepted", carries(A) && carries(B) && carries(C), "not all accepted");

// MPQTIMER: kept in the journal, on the game clock
var hourTicks = CMProps.getMillisPerMudHour() / CMProps.getTickMillis();
run("mpqtimer Fixq11ava fixq11a 6 mudhours");
var left = QT.ticksLeft(V, "fixq11a");
check("set", due("fixq11a") > GT.now() && left > 5 * hourTicks && left <= 6 * hourTicks, "due " + due("fixq11a") + ", " + left + " ticks left of " + (6 * hourTicks));
check("saved", has(V.fetchEffect("Prop_QuestLog").text(), "\"t\":{\"fixq11a\":"), s(V.fetchEffect("Prop_QuestLog").text()));
run("mpqtimer Fixq11ava fixq11b 1 mudhour");
run("mpqtimer Fixq11ava fixq11c 1 mudhour");

// QUESTS: their own deadline, sooner than the quest's duration
out();
var said = s(CMClass.getCommand("Questwins").getQuestsDoingList(V.Name(), "you are")); // QUESTS (a test's character is a monster to it)
var own = s(QJ.timeLeft(QT.ticksLeft(V, "fixq11a")));
check("quests-list", has(said, "fixq11a (time left: " + own + ")") && own != s(QJ.timeLeft(A.ticksRemaining())), said);
var L = new java.util.ArrayList(); L.add("QUESTS"); L.add("fixq11a");
CMClass.getCommand("Questwins").execute(V, L, 0);
said = out();
check("quests-detail", has(said, "Time Remaining: " + own), said);

// OFF
run("mpqtimer Fixq11ava fixq11a OFF");
check("off", due("fixq11a") == -1 && QT.ticksLeft(V, "fixq11a") == -1, "due " + due("fixq11a"));

// it comes with no trigger for it: the quest fails, EXPIRED, and the deadline goes
run("mpqtimer Fixq11ava fixq11a 3 mudhours");
QJ.setTimer(V, "fixq11a", GT.now() - 1);
QT.tick();
check("expired", s(QR.status("fixq11a", V, V.Name())) == "EXPIRED" && !carries(A) && due("fixq11a") == -1,
	"status '" + QR.status("fixq11a", V, V.Name()) + "', carries " + carries(A) + ", due " + due("fixq11a"));
check("others-wait", due("fixq11b") > GT.now() && due("fixq11c") > GT.now(), "b " + due("fixq11b") + ", c " + due("fixq11c"));

// a bare QUESTTIMEOUT_PROG on the quest's own script: it runs, and sets a new deadline
out();
QJ.setTimer(V, "fixq11b", GT.now() - 1);
QT.tick();
said = out();
check("timeout-bare", has(said, "fixq11b ran late for fixq11b") && s(QR.status("fixq11b", V, V.Name())) == "ACTIVE", said + " / '" + QR.status("fixq11b", V, V.Name()) + "'");
left = QT.ticksLeft(V, "fixq11b");
check("timeout-again", left > hourTicks && left <= 2 * hourTicks, left + " ticks left");

// QUESTTIMEOUT_PROG quest on another script
QJ.setTimer(V, "fixq11c", GT.now() - 1);
QT.tick();
said = out();
check("timeout-named", has(said, "fixq11c heard fixq11c") && s(QR.status("fixq11c", V, V.Name())) == "ACTIVE" && !has(said, "fixq11b ran late"), said);

// a win takes the deadline away
run("mpqtimer Fixq11ava fixq11c 4 mudhours");
run("mpquestwin Fixq11ava fixq11c");
check("won-clears", C.wasWinner(V.Name()) && due("fixq11c") == -1, "winner " + C.wasWinner(V.Name()) + ", due " + due("fixq11c"));

[A, B, C].forEach(function (Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
