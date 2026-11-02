// Q16: an objective's place (AT= a room ID, an area, or QUESTOBJ:n), shown by QUESTS as "(area, room)";
// QUESTS WHERE n gives the first way toward it; QUESTTRACK=OFF|AREA|PATH says how much.
FIXFILE = "q16-where";
var R = M.location();
var QJ = CM.core.QuestJournal;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq16tam");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
// a room next to this one, by an open exit
var R2 = null, dir = -1;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
	var E = R.getExitInDir(d);
	if ((R.getRoomInDir(d) != null) && (E != null) && E.isOpen() && R.getRoomInDir(d) != R) { R2 = R.getRoomInDir(d); dir = d; break; }
}
check("setup", R2 != null, "no room next to " + R.roomID());
var g = CMClass.getMOB("GenMob"); g.setName("a fixq16 sexton"); CMLib.catalog().addCatalog(g); g.destroy();
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(["set name fixq16", "set duration 2000", "set room any", "load mob catalog a fixq16 sexton", "give script LOAD=fixq16giver.script",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq16giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq16track.script", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq16track.script</NAME><DATA>", "ONCE_PROG 100\n\tmpsetvar $i FIXQ16 on\n~", "</DATA></FILE>", ""].join("\n"), true);
CMLib.quests().addQuest(Q);
Q.startQuest();
Q.acceptQuest(V);
function run(text) { var m = scriptedMob("a fixq16 clerk", "RAND_PROG 100\n" + text + "\n~\n"); tickMob(m); m.destroy(); }
function where(n) { var L = new java.util.ArrayList(); L.add("QUESTS"); L.add("WHERE"); if (n) L.add(n); CMClass.getCommand("Questwins").execute(V, L, 0); return out(); }
var title = s(CM.core.CMStrings.removeColors(R2.displayText(null))).trim();
var areaName = s(R2.getArea().name());

// AT= a room ID: shown as (area, room); WHERE gives the way
run("mpqobjective Fixq16tam fixq16 ADD bell 1 Return the bell AT=\"" + s(R2.roomID()) + "\"");
var lines = s(QJ.objectiveLines(V, "fixq16"));
check("place", has(lines, "Return the bell (" + areaName + ", " + title + ")"), lines);
out();
var said = where("1");
check("where", has(said, "Return the bell: " + title + " lies to the " + s(CMLib.directions().getDirectionName(dir)).toLowerCase() + "."), said);
// there already
R2.bringMobHere(V, false);
said = where("1");
check("there", has(said, "Return the bell: you are there."), said);
R.bringMobHere(V, false);

// AREA: the area only; OFF: nothing
var old = CMProps.instance().getProperty("QUESTTRACK");
CMProps.instance().setProperty("QUESTTRACK", "AREA");
said = where("1");
check("track-area", has(said, "Return the bell: in " + areaName + "."), said);
CMProps.instance().setProperty("QUESTTRACK", "OFF");
said = where("1");
lines = s(QJ.objectiveLines(V, "fixq16"));
check("track-off", has(said, "do not say where") && !has(lines, "(" + areaName), said + " / " + lines);
if (old == null) CMProps.instance().remove("QUESTTRACK"); else CMProps.instance().setProperty("QUESTTRACK", old);

// AT= the quest's mob, and AT id later; a done objective shows no place
run("mpqobjective Fixq16tam fixq16 ADD sexton 1 Speak to the sexton AT=QUESTOBJ:1");
var sexton = Q.getQuestMob(1);
lines = s(QJ.objectiveLines(V, "fixq16"));
check("questobj", sexton != null && has(lines, "Speak to the sexton (" + s(sexton.location().getArea().name())), lines);
run("mpqobjective Fixq16tam fixq16 DONE bell");
lines = s(QJ.objectiveLines(V, "fixq16"));
check("done-no-place", has(lines, "Return the bell (done)") && !has(lines, "Return the bell (done) ("), lines);
run("mpqobjective Fixq16tam fixq16 AT sexton");
check("at-cleared", !has(QJ.objectiveLines(V, "fixq16"), "Speak to the sexton ("), s(QJ.objectiveLines(V, "fixq16")));
said = where("1");
check("where-none", has(said, "says where it is"), said);

if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
