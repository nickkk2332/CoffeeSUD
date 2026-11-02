// Q5: SET REPEATABLE NO|YES|<game time> and SET MAXWINS, before CAN_ACCEPT; QUESTWINS(), QUESTWINAGE(),
// the QUESTWINS mask, and the wins in QUESTS WON.
FIXFILE = "q5-repeat";
var R = M.location();
var QR = CM.core.QuestRecords;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
["FIXQ5A", "FIXQ5B"].forEach(clearVar);
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
var V = character("Fixq5mira");
var V2 = character("Fixq5tobin");
var g = CMClass.getMOB("GenMob"); g.setName("a fixq5 ranger"); CMLib.catalog().addCatalog(g); g.destroy();
function quest(name, extra) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(["set name " + name, "set duration 2000", extra, "set room any", "load mob catalog a fixq5 ranger", "give script LOAD=fixq5giver.script",
		"<?xml version=\"1.0\"?>",
		"<FILE><NAME>fixq5giver.script</NAME><DATA>",
		"FUNCTION_PROG CAN_ACCEPT", "\tRETURN TRUE", "~",
		"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq5track.script", "~",
		"</DATA></FILE>",
		"<FILE><NAME>fixq5track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ5T on", "~", "</DATA></FILE>", ""].join("\n"), true);
	CMLib.quests().addQuest(Q);
	Q.startQuest();
	return Q;
}
// a turn-in: the win, and the character's quest script taken away, as MPENDQUEST would
function turnIn(Q, mob) {
	Q.declareWinner(mob.Name());
	for (var i = mob.numScripts() - 1; i >= 0; i--) { var S = mob.fetchScript(i); if (S != null && s(S.defaultQuestName()).toLowerCase() == s(Q.name()).toLowerCase()) mob.delScript(S); }
}
// as if the game had gone on: the last win moved back by so many ticks
function later(Q, mob, ticks) { var rec = QR.get(Q.name(), mob.Name()); rec.lastWin = rec.lastWin - ticks * CMProps.getTickMillis(); }

// a bounty: once a game day (here 10 ticks), at most twice
var B = quest("fixq5bounty", "set repeatable 10\nset maxwins 2");
check("settings", s(B.getStat("REPEATABLE")) == "10" && s(B.getStat("MAXWINS")) == "2" && has(B.inspection(), "REPEATABLE 10, MAXWINS 2"), B.inspection());
check("first", B.canAcceptQuest(V), "Mira cannot take it the first time");
B.acceptQuest(V);
turnIn(B, V);
check("wins-1", QR.wins("fixq5bounty", V.Name()) == 1, "wins " + QR.wins("fixq5bounty", V.Name()));
check("cooldown", !B.canAcceptQuest(V) && B.canAcceptQuest(V2), "Mira " + B.canAcceptQuest(V) + ", Tobin " + B.canAcceptQuest(V2));
later(B, V, 11);
check("after-cooldown", B.canAcceptQuest(V), "not again after the wait");
B.acceptQuest(V);
turnIn(B, V);
later(B, V, 11);
check("maxwins", QR.wins("fixq5bounty", V.Name()) == 2 && !B.canAcceptQuest(V), "wins " + QR.wins("fixq5bounty", V.Name()) + ", may " + B.canAcceptQuest(V));

// REPEATABLE NO: once
var O = quest("fixq5once", "set repeatable no");
O.acceptQuest(V2);
turnIn(O, V2);
later(O, V2, 1000);
check("never-again", !O.canAcceptQuest(V2) && O.canAcceptQuest(V), "Tobin " + O.canAcceptQuest(V2) + ", Mira " + O.canAcceptQuest(V));

// the functions
var clerk = scriptedMob("a fixq5 clerk", "RAND_PROG 100\n"
	+ "if questwins(Fixq5mira fixq5bounty >= 2)\n\tmpsetvar FIXTEST FIXQ5A two\nendif\n"
	+ "mpsetvar FIXTEST FIXQ5B $%QUESTWINS(Fixq5mira fixq5bounty)%:$%QUESTWINAGE(Fixq5mira fixq5bounty)%:$%QUESTWINAGE(Fixq5tobin fixq5bounty)%\n~\n");
tickMob(clerk);
check("functions", gvar("FIXQ5A") == "two" && gvar("FIXQ5B") == "2:0:-1", "FIXQ5A '" + gvar("FIXQ5A") + "', FIXQ5B '" + gvar("FIXQ5B") + "'");

// the mask
var MK = CMLib.masking();
check("mask", MK.maskCheck("-QUESTWINS +fixq5bounty:>=2", V, true) && !MK.maskCheck("-QUESTWINS +fixq5bounty:>=2", V2, true)
	&& !MK.maskCheck("+QUESTWINS -fixq5bounty:>=2", V, true) && MK.maskCheck("-QUESTWINS +fixq5bounty:<1", V2, true), "QUESTWINS masks");

// QUESTS WON
var QW = CMClass.getCommand("Questwins");
check("quests-won", has(call(QW, "getQuestsWonList", [V.Name(), "you are"]), "fixq5bounty (2 wins)"), s(call(QW, "getQuestsWonList", [V.Name(), "you are"])));

[B, O].forEach(function(Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
clerk.destroy();
[V, V2].forEach(function(m) { CMLib.players().delPlayer(m); m.setPlayerStats(null); m.destroy(); });
done();
