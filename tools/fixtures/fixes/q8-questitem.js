// Q8: Prop_QuestItem: NODROP, NOSELL, NOGIVE, KEEPONDEATH, REMOVEON=, and QUESTS name listing it.
FIXFILE = "q8-questitem";
var R = M.location();
var QI = CM.core.QuestItems;
var PhyStats = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq8mira");
var VS = CMClass.getCommon("FakeSession");
VS.initialize(null, "MEMORY");
V.setSession(VS);
VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var g = CMClass.getMOB("GenMob"); g.setName("a fixq8 priest"); CMLib.catalog().addCatalog(g); g.destroy();
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(["set name fixq8", "set duration 2000", "set room any", "load mob catalog a fixq8 priest", "give script LOAD=fixq8giver.script",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq8giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq8track.script", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq8track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ8T on", "~", "</DATA></FILE>", ""].join("\n"), true);
CMLib.quests().addQuest(Q);
Q.startQuest();
Q.acceptQuest(V);
function item(name, parms) {
	var I = CMClass.getItem("GenItem");
	I.setName(name);
	var P = CMClass.getAbility("Prop_QuestItem");
	P.setMiscText(parms);
	I.addNonUninvokableEffect(P);
	V.addItem(I);
	I.recoverPhyStats();
	return I;
}
var bell = item("a fixq8 bell", "QUEST=fixq8 NODROP NOSELL NOGIVE REMOVEON=WON KEEPONDEATH");
var map = item("a fixq8 map", "QUEST=fixq8 REMOVEON=FAILED");
var key = item("a fixq8 key", "QUEST=fixq8");
var other = newMob("GenMob", R);
other.setName("a fixq8 pawnbroker");

out();
check("nodrop", !R.okMessage(V, CMClass.getMsg(V, bell, null, CMMsg.MSG_DROP, null)) && has(out(), "You cannot drop a fixq8 bell: it is for a quest."), "dropped");
check("nogive", !R.okMessage(V, CMClass.getMsg(V, other, bell, CMMsg.MSG_GIVE, null)) && has(out(), "You cannot give a fixq8 bell away"), "given");
check("nosell", !R.okMessage(V, CMClass.getMsg(V, other, bell, CMMsg.MSG_SELL, null)) && has(out(), "You cannot sell a fixq8 bell"), "sold");
check("free", R.okMessage(V, CMClass.getMsg(V, map, null, CMMsg.MSG_DROP, null)), "the map may be dropped");
check("keepondeath", (bell.phyStats().sensesMask() & PhyStats.SENSE_ITEMDEATHKEEPER) != 0 && (map.phyStats().sensesMask() & PhyStats.SENSE_ITEMDEATHKEEPER) == 0,
	"senses " + bell.phyStats().sensesMask());
check("of", QI.of(V, "fixq8").size() == 3, "bound items " + QI.of(V, "fixq8"));
// QUESTS fixq8 lists them
var L = new java.util.ArrayList(); L.add("QUESTS"); L.add("fixq8");
CMClass.getCommand("Questwins").execute(V, L, 0);
var said = out();
check("quests-name", has(said, "Quest items:") && has(said, "a fixq8 bell") && has(said, "a fixq8 key"), said);

// the win takes the bell (REMOVEON=WON) and the key (any), not the map (FAILED only)
Q.declareWinner(V.Name());
check("removeon-won", bell.amDestroyed() && key.amDestroyed() && !map.amDestroyed() && has(out(), "a fixq8 bell is gone"),
	"bell " + bell.amDestroyed() + ", key " + key.amDestroyed() + ", map " + map.amDestroyed());
// the quest's end: ENDED takes those REMOVEON lets
var lamp = item("a fixq8 lamp", "QUEST=fixq8 REMOVEON=ENDED");
var ring = item("a fixq8 ring", "QUEST=fixq8 REMOVEON=NONE");
Q.stopQuest();
check("removeon-ended", lamp.amDestroyed() && !ring.amDestroyed() && !map.amDestroyed(), "lamp " + lamp.amDestroyed() + ", ring " + ring.amDestroyed());

map.destroy(); ring.destroy(); other.destroy();
CMLib.quests().delQuest(Q);
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
