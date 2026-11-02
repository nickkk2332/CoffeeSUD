// Q15: SET DROPPABLE FALSE refuses QUESTS DROP; FUNCTION_PROG CAN_DROP refuses with its words; DO_DROP runs
// on the confirmed drop (the character's script for the quest and its accept script).
FIXFILE = "q15-drop";
var R = M.location();
var QR = CM.core.QuestRecords;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq15wren");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var g = CMClass.getMOB("GenMob"); g.setName("a fixq15 postmaster"); CMLib.catalog().addCatalog(g); g.destroy();
function quest(name, extra, giverMore, trackMore) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(["set name " + name, "set duration 2000"].concat(extra || []).concat(["set room any", "load mob catalog a fixq15 postmaster",
		"give script LOAD=" + name + "giver.script",
		"<?xml version=\"1.0\"?>",
		"<FILE><NAME>" + name + "giver.script</NAME><DATA>",
		"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
		"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=" + name + "track.script", "~",
		giverMore || "",
		"</DATA></FILE>",
		"<FILE><NAME>" + name + "track.script</NAME><DATA>", "ONCE_PROG 100\n\tmpsetvar $i FIXQ15 on\n~", trackMore || "", "</DATA></FILE>", ""]).join("\n"), true);
	CMLib.quests().addQuest(Q);
	Q.startQuest();
	Q.acceptQuest(V);
	return Q;
}
function carries(Q) { return CM.core.QuestMarkers.accepted(V, Q); }
function drop(name) { var L = new java.util.ArrayList(); L.add("QUESTS"); L.add("DROP"); L.add(name); CMClass.getCommand("Questwins").execute(V, L, 0); return out(); }

// the main story: SET DROPPABLE FALSE
var A = quest("fixq15main", ["set droppable false"]);
check("set", s(A.getStat("DROPPABLE")) == "FALSE" && carries(A), s(A.getStat("DROPPABLE")));
out();
var said = drop("fixq15main");
check("undroppable", has(said, "is not a quest you can drop") && carries(A), said);

// CAN_DROP, on the accept script: its words refuse
var B = quest("fixq15sealed", [], "FUNCTION_PROG CAN_DROP\n\tRETURN Fixq15 the seal is already broken, so you must finish it.\n~\n");
said = drop("fixq15sealed");
check("can-drop", has(said, "Fixq15 the seal is already broken, so you must finish it.") && carries(B), said);

// with nothing against it, QUESTS DROP goes on to ask (a test's character has no session to answer: it stays)
var C = quest("fixq15parcel", [], "FUNCTION_PROG DO_DROP\n\tmpechoat $n Fixq15 the postmaster takes the parcel back.\n~\n",
	"FUNCTION_PROG DO_DROP\n\tmpechoat $i Fixq15 you hand the parcel over.\n~\n");
check("droppable", s(C.canDropQuest(V)) == "" && s(B.canDropQuest(V)) != "" && s(A.canDropQuest(V)) != "", "C '" + C.canDropQuest(V) + "'");
said = drop("fixq15parcel");
check("asks", carries(C) && !has(said, "not a quest you can drop") && !has(said, "Fixq15 the seal"), said);

// the confirmed drop: DO_DROP on the character's script for the quest and on its accept script
out();
C.dropQuest(V);
said = out();
check("do-drop", has(said, "Fixq15 you hand the parcel over.") && has(said, "Fixq15 the postmaster takes the parcel back."), said);

[A, B, C].forEach(function (Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
