// Q2: Prop_QuestMarker: AVAILABLE, ACTIVE and READY marks in room listings, by viewer;
// QUESTMARKERS=AUTO on a quest's first quest mob.
FIXFILE = "q2-markers";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function blurb(thing, viewer) { return s(CMLib.flags().getDispositionBlurbs(thing, viewer)); }
function viewer(name) {
	var V = newMob("GenMob", R);
	V.setName(name);
	var S = CMClass.getCommon("FakeSession");
	S.initialize(null, "MEMORY");
	V.setSession(S);
	S.setMob(V);
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); // one of the player's characters
	return V;
}
var giver = CMClass.getMOB("GenMob");
giver.setName("a fixq2 giver");
CMLib.catalog().addCatalog(giver);
giver.destroy();
var script = [
	"set name fixq2", "set duration 1000", "set room any", "load mob catalog a fixq2 giver", "give script LOAD=fixq2giver.script",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq2giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq2track.script", "~",
	"FUNCTION_PROG CAN_COMPLETE", "\tif HAS($n 'a fixq2 bell')", "\t\tRETURN TRUE", "\tendif", "\tRETURN", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq2track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ2T on", "~", "</DATA></FILE>",
	""].join("\n");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(script, true);
CMLib.quests().addQuest(Q);
Q.startQuest();
var G = Q.getQuestMob(1);
check("quest", Q.running() && G != null && s(G.Name()) == "a fixq2 giver", "running " + Q.running() + ", mob " + G);
if (G != null) {
	var V = viewer("Fixq2mira");
	check("plain", !has(blurb(G, V), "(!)") && !has(blurb(G, V), "(?)"), "unmarked giver shows '" + blurb(G, V) + "'");
	var P = CMClass.getAbility("Prop_QuestMarker");
	P.setMiscText("QUEST=* READYFUNC=CAN_COMPLETE");
	G.addNonUninvokableEffect(P);
	check("available", has(blurb(G, V), "^Y(!)^?") && Q.canAcceptQuest(V), "shows '" + blurb(G, V) + "'");
	Q.acceptQuest(V);
	check("active", CM.core.QuestMarkers.accepted(V, Q) && has(blurb(G, V), "^w(?)^?"), "accepted " + CM.core.QuestMarkers.accepted(V, Q) + ", shows '" + blurb(G, V) + "'");
	var bell = CMClass.getItem("GenItem");
	bell.setName("a fixq2 bell");
	V.addItem(bell);
	check("ready", has(blurb(G, V), "^Y(?)^?"), "shows '" + blurb(G, V) + "'");
	// per viewer: another character sees it as theirs to take
	var V2 = viewer("Fixq2tobin");
	check("per-viewer", has(blurb(G, V2), "(!)") && !has(blurb(G, V2), "(?)"), "Tobin sees '" + blurb(G, V2) + "'");
	// a mob that is no one's character sees nothing
	var npc = newMob("GenMob", R);
	npc.setName("a fixq2 bystander");
	check("npc", blurb(G, npc).indexOf("(!)") < 0 && blurb(G, npc).indexOf("(?)") < 0, "a monster sees '" + blurb(G, npc) + "'");
	// marks of its own, on an item, for one quest
	var sign = CMClass.getItem("GenItem");
	sign.setName("a fixq2 notice");
	R.addItem(sign);
	var P2 = CMClass.getAbility("Prop_QuestMarker");
	P2.setMiscText("QUEST=fixq2 AVAILABLE=\"[job]\" ACTIVE=\"[taken]\"");
	sign.addNonUninvokableEffect(P2);
	check("own-marks", has(blurb(sign, V2), "[job]") && has(blurb(sign, V), "[taken]"), "Tobin '" + blurb(sign, V2) + "', Mira '" + blurb(sign, V) + "'");
	// QUESTMARKERS=AUTO: the first quest mob, without the property
	G.delEffect(P);
	var old = CMProps.instance().getProperty("QUESTMARKERS");
	check("auto-off", !has(blurb(G, V2), "(!)"), "shows '" + blurb(G, V2) + "'");
	CMProps.instance().setProperty("QUESTMARKERS", "AUTO");
	check("auto", has(blurb(G, V2), "^Y(!)^?") && has(blurb(G, V), "^w(?)^?"), "Tobin '" + blurb(G, V2) + "', Mira '" + blurb(G, V) + "'");
	if (old == null) CMProps.instance().remove("QUESTMARKERS"); else CMProps.instance().setProperty("QUESTMARKERS", old);
	// the quest over: no marks
	Q.stopQuest();
	check("stopped", !has(blurb(sign, V2), "[job]"), "shows '" + blurb(sign, V2) + "'");
	V.setPlayerStats(null); V2.setPlayerStats(null);
	sign.destroy(); npc.destroy(); V.destroy(); V2.destroy();
}
if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
done();
