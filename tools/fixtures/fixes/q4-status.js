// Q4: each character's quest status and outcome: MPQUESTWIN ... OUTCOME, MPQUESTFAIL and QUESTFAIL_PROG,
// QUESTSTATUS() and QUESTOUTCOME(), the QUESTFAIL and QUESTOUTCOME masks, KEYPLAYER FAIL, QUESTS FAILED, the save.
FIXFILE = "q4-status";
var R = M.location();
var QR = CM.core.QuestRecords;
var SEv = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
["FIXQ4A", "FIXQ4B", "FIXQ4C", "FIXQ4D"].forEach(clearVar);
function character(name) {
	var V = newMob("GenMob", R);
	V.setName(name);
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	CMLib.players().addPlayer(V);
	return V;
}
var V = character("Fixq4mira");
var V2 = character("Fixq4tobin");
function catalogMob(name) { var m = CMClass.getMOB("GenMob"); m.setName(name); CMLib.catalog().addCatalog(m); m.destroy(); }
catalogMob("a fixq4 giver");
catalogMob("a fixq4 escort");
function questScript(name, extra) {
	return ["set name " + name, "set duration 2000", "set room any", "load mob catalog a fixq4 giver", "give script LOAD=fixq4giver.script",
		extra || "",
		"<?xml version=\"1.0\"?>",
		"<FILE><NAME>fixq4giver.script</NAME><DATA>",
		"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
		"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq4track.script", "~",
		"</DATA></FILE>",
		"<FILE><NAME>fixq4track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ4T on", "~", "</DATA></FILE>",
		""].join("\n");
}
function quest(name, extra) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(questScript(name, extra), true);
	CMLib.quests().addQuest(Q);
	Q.startQuest();
	return Q;
}
function carries(mob, Q) { return CM.core.QuestMarkers.accepted(mob, Q); }

// accepted: ACTIVE; a win with an outcome
var A = quest("fixq4a");
check("none", s(QR.status("fixq4a", V, V.Name())) == "", "status '" + QR.status("fixq4a", V, V.Name()) + "' before");
A.acceptQuest(V);
check("active", s(QR.status("fixq4a", V, V.Name())) == "ACTIVE" && carries(V, A), "status '" + QR.status("fixq4a", V, V.Name()) + "'");
var judge = scriptedMob("a fixq4 judge", "RAND_PROG 100\n"
	+ "mpquestwin Fixq4mira fixq4a OUTCOME spared\n"
	+ "if queststatus(Fixq4mira fixq4a == WON)\n\tmpsetvar FIXTEST FIXQ4A won\nendif\n"
	+ "mpsetvar FIXTEST FIXQ4B $%QUESTOUTCOME(Fixq4mira fixq4a)%\n~\n");
tickMob(judge);
check("won", A.wasWinner(V.Name()) && s(QR.status("fixq4a", V, V.Name())) == "WON" && s(QR.outcome("fixq4a", V.Name())) == "spared",
	"winner " + A.wasWinner(V.Name()) + ", status '" + QR.status("fixq4a", V, V.Name()) + "', outcome '" + QR.outcome("fixq4a", V.Name()) + "'");
check("functions", gvar("FIXQ4A") == "won" && gvar("FIXQ4B") == "spared", "FIXQ4A '" + gvar("FIXQ4A") + "', FIXQ4B '" + gvar("FIXQ4B") + "'");
var QW = CMClass.getCommand("Questwins");
check("quests-won", has(call(QW, "getQuestsWonList", [V.Name(), "you are"]), "fixq4a (spared)"), s(call(QW, "getQuestsWonList", [V.Name(), "you are"])));

// MPQUESTFAIL: the record, QUESTFAIL_PROG and QUEST_PROG FAIL, and the character's quest script gone
var B = quest("fixq4b");
B.acceptQuest(V);
var R2 = otherRoom();
var SR = scriptOn(R2, "QUESTFAIL_PROG fixq4b\nmpsetvar FIXTEST FIXQ4C $n:$0\n~\nQUESTFAIL_PROG nosuchfixq4\nmpsetvar FIXTEST FIXQ4D $n\n~\n");
check("registered", SEv.registered(SEv.QUESTFAIL_PROG).contains(R2), s(SEv.registered(SEv.QUESTFAIL_PROG)));
var widow = scriptedMob("a fixq4 widow", "RAND_PROG 100\nmpquestfail Fixq4mira fixq4b the escort died\n~\n");
tickMob(widow);
runQueued(SR);
check("failed", s(QR.status("fixq4b", V, V.Name())) == "FAILED" && s(QR.outcome("fixq4b", V.Name())) == "the escort died" && !carries(V, B) && B.running(),
	"status '" + QR.status("fixq4b", V, V.Name()) + "', outcome '" + QR.outcome("fixq4b", V.Name()) + "', carries " + carries(V, B));
check("questfail-prog", gvar("FIXQ4C") == "Fixq4mira:the escort died" && gvar("FIXQ4D") == "", "FIXQ4C '" + gvar("FIXQ4C") + "', FIXQ4D '" + gvar("FIXQ4D") + "'");
check("quests-failed", has(call(QW, "getQuestsFailedList", [V.Name(), "you are"]), "fixq4b (the escort died)"), s(call(QW, "getQuestsFailedList", [V.Name(), "you are"])));

// masks
var MK = CMLib.masking();
check("mask-questfail", MK.maskCheck("-QUESTFAIL +fixq4b", V, true) && !MK.maskCheck("-QUESTFAIL +fixq4b", V2, true)
	&& !MK.maskCheck("+QUESTFAIL -fixq4b", V, true) && MK.maskCheck("+QUESTFAIL -fixq4b", V2, true), "QUESTFAIL masks");
check("mask-questoutcome", MK.maskCheck("-QUESTOUTCOME +fixq4a:spared", V, true) && !MK.maskCheck("-QUESTOUTCOME +fixq4a:slain", V, true)
	&& !MK.maskCheck("+QUESTOUTCOME -fixq4a:spared", V, true) && MK.maskCheck("+QUESTOUTCOME -fixq4a:spared", V2, true), "QUESTOUTCOME masks");
check("mask-text", has(MK.maskDesc("-QUESTFAIL +fixq4b"), "failed"), s(MK.maskDesc("-QUESTFAIL +fixq4b")));

// KEYPLAYER FAIL: the escort's death fails it for those on it; the quest goes on
var C = quest("fixq4c", "load mob catalog a fixq4 escort\ngive stat keyplayer fail");
C.acceptQuest(V2);
var escort = C.getQuestMob(2);
var QB = (escort == null) ? null : escort.fetchEffect("QuestBound");
check("keyplayer", QB != null && s(QB.getStat("KEY")) == "FAIL", "escort " + escort + ", QuestBound " + QB);
if (escort != null) {
	var death = CMClass.getMsg(escort, null, null, CMMsg.MSG_DEATH, null);
	escort.location().send(escort, death);
}
check("keyplayer-fail", s(QR.status("fixq4c", V2, V2.Name())) == "FAILED" && s(QR.outcome("fixq4c", V2.Name())) == "KEYPLAYER" && C.running(),
	"status '" + QR.status("fixq4c", V2, V2.Name()) + "', running " + C.running());

// the save keeps them
var xml = s(QR.toXML());
check("saved", has(xml, "Q=\"fixq4a\"") && has(xml, "W=\"Fixq4mira\"") && has(xml, "S=\"WON\"") && has(xml, "O=\"spared\"") && has(xml, "N=\"1\""), xml);
QR.newGame();
check("newgame", s(QR.status("fixq4a", null, V.Name())) == "", "status after a new game '" + QR.status("fixq4a", null, V.Name()) + "'");
QR.fromXML(CMLib.xml().parseAllXML(xml).get(0));
check("restored", s(QR.status("fixq4a", null, V.Name())) == "WON" && s(QR.outcome("fixq4b", V.Name())) == "the escort died" && QR.get("fixq4a", V.Name()).wins == 1,
	"fixq4a '" + QR.status("fixq4a", null, V.Name()) + "', fixq4b '" + QR.outcome("fixq4b", V.Name()) + "'");

[A, B, C].forEach(function(Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
judge.destroy(); widow.destroy();
[V, V2].forEach(function(m) { CMLib.players().delPlayer(m); m.setPlayerStats(null); m.destroy(); });
done();
