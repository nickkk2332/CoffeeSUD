// Q3: the quest journal: MPQOBJECTIVE, MPQJOURNAL, QOBJECTIVE(), a step's SET OBJECTIVE and SET JOURNAL,
// QUESTS grouped by category with objectives and time left, QUESTS LOG, and the notices.
FIXFILE = "q3-journal";
var R = M.location();
var QJ = CM.core.QuestJournal;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
["FIXQ3A", "FIXQ3B"].forEach(clearVar);
var V = newMob("GenMob", R);
V.setName("Fixq3mira");
var VS = CMClass.getCommon("FakeSession");
VS.initialize(null, "MEMORY");
V.setSession(VS);
VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
// what the character was told since last asked, as one line without colours (the session wraps long lines)
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }

var giver = CMClass.getMOB("GenMob");
giver.setName("a fixq3 giver");
CMLib.catalog().addCatalog(giver);
giver.destroy();
var script = [
	"set name fixq3", "set display The Fixes Bell", "set category main", "set duration 2000", "set room any",
	"set objective drowned 5 Lay 5 drowned men to rest", "set journal The bell rang under the water.",
	"load mob catalog a fixq3 giver", "give script LOAD=fixq3giver.script",
	"step", "set duration 2000",
	"set objective priest 1 Bring the bell to the priest", "set journal Ned said to take it to the chapel.",
	"step break",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq3giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq3track.script", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq3track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ3T on", "~", "</DATA></FILE>",
	""].join("\n");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(script, true);
CMLib.quests().addQuest(Q);
Q.startQuest();
check("running", Q.running() && s(Q.getStat("STEPNUM")) == "1", "running " + Q.running());

// accepting gives the step's objective and entry
Q.acceptQuest(V);
var said = out();
check("accept-step", QJ.value(V, "fixq3", "drowned") == "0" && has(QJ.entries(V, "fixq3", 10), "The bell rang under the water."),
	"drowned '" + QJ.value(V, "fixq3", "drowned") + "', entries " + QJ.entries(V, "fixq3", 10));
check("notice", has(said, "Quest updated: The Fixes Bell: Lay 5 drowned men to rest 0/5"), said);

// the scripts' way in
var priest = scriptedMob("a fixq3 priest", "RAND_PROG 100\n"
	+ "mpqobjective Fixq3mira fixq3 bump drowned 2\n"
	+ "mpqobjective Fixq3mira fixq3 add bell 1 Find the bell\n"
	+ "mpqjournal Fixq3mira fixq3 Two of the drowned are at rest.\n"
	+ "if qobjective(Fixq3mira fixq3 drowned == 2)\n\tmpsetvar FIXTEST FIXQ3A two\nendif\n"
	+ "mpsetvar FIXTEST FIXQ3B $%QOBJECTIVE(Fixq3mira fixq3 drowned)%\n~\n");
tickMob(priest);
check("mpqobjective", QJ.value(V, "fixq3", "drowned") == "2" && QJ.value(V, "fixq3", "bell") == "0", "drowned '" + QJ.value(V, "fixq3", "drowned") + "', bell '" + QJ.value(V, "fixq3", "bell") + "'");
check("qobjective", gvar("FIXQ3A") == "two" && gvar("FIXQ3B") == "2", "FIXQ3A '" + gvar("FIXQ3A") + "', FIXQ3B '" + gvar("FIXQ3B") + "'");
check("mpqjournal", has(QJ.entries(V, "fixq3", 10), "Two of the drowned are at rest."), s(QJ.entries(V, "fixq3", 10)));
said = out();
check("notice-bump", has(said, "Quest updated: The Fixes Bell: Lay 5 drowned men to rest 2/5"), said);

// reaching the count is done; FAIL, HIDE and SHOW
QJ.objective(V, "fixq3", "BUMP", "drowned 3");
check("done", QJ.value(V, "fixq3", "drowned") == "DONE", QJ.value(V, "fixq3", "drowned"));
QJ.objective(V, "fixq3", "FAIL", "bell");
check("failed", QJ.value(V, "fixq3", "bell") == "FAILED", QJ.value(V, "fixq3", "bell"));
var lines = s(QJ.objectiveLines(V, "fixq3"));
check("lines", has(lines, "[x] Lay 5 drowned men to rest 5/5 (done)") && has(lines, "[-] Find the bell (failed)"), lines);
QJ.objective(V, "fixq3", "HIDE", "bell");
check("hide", !has(QJ.objectiveLines(V, "fixq3"), "Find the bell"), s(QJ.objectiveLines(V, "fixq3")));
QJ.objective(V, "fixq3", "SHOW", "bell");
check("unknown", QJ.objective(V, "fixq3", "BUMP", "nosuch") != null && QJ.value(V, "fixq3", "nosuch") == "", "an objective it has not");

// the next step's defaults, for those on the quest
Q.stepQuest();
check("step-defaults", s(Q.getStat("STEPNUM")) == "2" && QJ.value(V, "fixq3", "priest") == "0" && has(QJ.entries(V, "fixq3", 10), "Ned said to take it to the chapel."),
	"step " + Q.getStat("STEPNUM") + ", priest '" + QJ.value(V, "fixq3", "priest") + "'");

// OBJECTIVEn from the quest's script, and QUESTS: grouped, with objectives and time left
var SE = null;
for (var e = V.scripts(); e.hasMoreElements();) { var x = e.nextElement(); if (s(x.defaultQuestName()).toLowerCase() == "fixq3") SE = x; }
// as the tracking script's MPSETVAR $i OBJECTIVE1 ... would
if (SE != null) { SE.setVar(V.Name(), "OBJECTIVE1", "Speak to Old Ned"); SE.setVar(V.Name(), "OBJECTIVE1DONE", "yes"); }
var QW = CMClass.getCommand("Questwins");
var list = s(QW.getQuestsDoingList(V.Name(), "you are"));
check("quests", has(list, "Main") && has(list, "1) The Fixes Bell (time left: ") && has(list, "[x] Lay 5 drowned men to rest 5/5 (done)")
	&& has(list, "[ ] Bring the bell to the priest") && has(list, "[x] Speak to Old Ned"), list);
check("timeleft", has(QJ.timeLeft(2000), "game"), s(QJ.timeLeft(2000)));
var log = s(QJ.entries(V, null, 20));
check("log", has(log, "[The Fixes Bell] The bell rang under the water.") && has(log, "[The Fixes Bell] Ned said"), log);

// kept in the character's hidden effect, so it is saved with them
var P = V.fetchEffect("Prop_QuestLog");
check("kept", P != null && has(P.text(), "drowned") && s(P.displayText()) == "", P == null ? "no Prop_QuestLog" : s(P.text()));

// QUESTNOTICES=OFF keeps the notices back
var old = CMProps.instance().getProperty("QUESTNOTICES");
CMProps.instance().setProperty("QUESTNOTICES", "OFF");
out();
QJ.objective(V, "fixq3", "SET", "priest 1");
check("notices-off", QJ.value(V, "fixq3", "priest") == "DONE" && !has(out(), "Quest updated"), "priest '" + QJ.value(V, "fixq3", "priest") + "'");
if (old == null) CMProps.instance().remove("QUESTNOTICES"); else CMProps.instance().setProperty("QUESTNOTICES", old);
QJ.objective(V, "fixq3", "CLEAR", "");
check("clear", QJ.objectiveLines(V, "fixq3").size() == 0, s(QJ.objectiveLines(V, "fixq3")));

if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
priest.destroy();
CMLib.players().delPlayer(V);
V.setPlayerStats(null);
V.destroy();
done();
