// Q7: Prop_QuestGiver REOFFER=ALWAYS|ONCE|<game time>, offers kept with the quest, QUESTS OFFERED and QUESTS ACCEPT.
FIXFILE = "q7-offers";
var R = M.location();
var QR = CM.core.QuestRecords;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq7mira");
var VS = CMClass.getCommon("FakeSession");
VS.initialize(null, "MEMORY");
V.setSession(VS);
VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
function quests(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("Questwins").execute(V, L, 0); return out(); }
var g = CMClass.getMOB("GenMob"); g.setName("a fixq7 ned"); CMLib.catalog().addCatalog(g); g.destroy();
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(["set name fixq7", "set display The Fixq7 Bell", "set duration 2000", "set room any", "load mob catalog a fixq7 ned", "give script LOAD=fixq7giver.script",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq7giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq7track.script", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq7track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ7T on", "~", "</DATA></FILE>", ""].join("\n"), true);
CMLib.quests().addQuest(Q);
Q.startQuest();
// the giver offers to the player's own character (a monster is offered nothing); OPTIONAL=FALSE takes it at once, with no prompt
function drop(mob) { for (var i = mob.numScripts() - 1; i >= 0; i--) { var S = mob.fetchScript(i); if (S != null && s(S.defaultQuestName()).toLowerCase() == "fixq7") mob.delScript(S); } }
var key = "OFFERED:" + s(M.Name()).toUpperCase(), shownKey = "SHOWN:" + s(M.Name()).toUpperCase();
var swamp = CMClass.getAbility("Prop_QuestGiver");
swamp.setMiscText("QUEST=fixq7 REOFFER=ONCE OPTIONAL=FALSE DETAILS=FALSE");
R.addNonUninvokableEffect(swamp);
// a Java Boolean is an object, and so true in JavaScript: compare its text
function mayOffer(P, mob) { return s(call(P, "mayOffer", [Q, mob])) == "true"; }
check("none-yet", s(Q.getStat(key)) == "" && mayOffer(swamp, M), "OFFERED '" + Q.getStat(key) + "'");
swamp.maybeGiveTheQuest(M, null);
check("noted", s(Q.getStat(key)).length > 0 && CM.core.QuestMarkers.accepted(M, Q), "OFFERED '" + Q.getStat(key) + "', on it " + CM.core.QuestMarkers.accepted(M, Q));
drop(M);
check("once", !mayOffer(swamp, M), "ONCE offers again");
swamp.setMiscText("QUEST=fixq7 REOFFER=ALWAYS OPTIONAL=FALSE DETAILS=FALSE");
check("always", mayOffer(swamp, M), "ALWAYS does not offer again");
swamp.setMiscText("QUEST=fixq7 REOFFER=5 OPTIONAL=FALSE DETAILS=FALSE");
check("time-not-yet", !mayOffer(swamp, M), "offered again before 5 ticks");
Q.setQuestVar(key, "" + (CM.core.GameTime.now() - 6 * CMProps.getTickMillis()));
check("time", mayOffer(swamp, M), "not offered again after 5 ticks");
// without REOFFER, a room's giver offers as before (each time), and the offer is still listed
R.delEffect(swamp);
Q.setQuestVar(key, "");
var shrine = CMClass.getAbility("Prop_QuestGiver");
shrine.setMiscText("QUEST=fixq7 OPTIONAL=FALSE DETAILS=FALSE");
R.addNonUninvokableEffect(shrine);
shrine.maybeGiveTheQuest(M, null);
drop(M);
check("default", mayOffer(shrine, M) && s(Q.getStat(key)) == "" && s(Q.getStat(shownKey)).length > 0 && QR.offeredTo(M).contains(Q),
	"may offer " + mayOffer(shrine, M) + ", OFFERED '" + Q.getStat(key) + "', SHOWN '" + Q.getStat(shownKey) + "'");
R.delEffect(shrine);

// as a giver notes it, for another character
QR.offered(Q, V);
// QUESTS OFFERED and QUESTS ACCEPT
out();
var said = quests("QUESTS OFFERED");
check("quests-offered", has(said, "1) The Fixq7 Bell") && has(said, "QUESTS ACCEPT"), said);
said = quests("QUESTS ACCEPT nosuch");
check("accept-unknown", has(said, "No quest offered to you and open to you is called 'nosuch'"), said);
said = quests("QUESTS ACCEPT 1");
check("accept", CM.core.QuestMarkers.accepted(V, Q) && has(said, "You are now on the quest 'The Fixq7 Bell'."), said);
said = quests("QUESTS OFFERED");
check("taken", QR.offeredTo(V).size() == 0 && has(said, "No quest offered to you is open to you now."), said);

if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
