// C5: MPALARM NAME=, MPALARMCANCEL, ALARM(); named alarms cancelled when the caster cannot act, and saved;
// MPTELEGRAPH (a warning each tick, LOOK, INTERRUPT=, AVOID=, the command on each one hit); GenAbility WINDUP.
FIXFILE = "c5-telegraph";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
function npc(name, hp) { var m = newMob("GenMob", R); m.setName(name); m.baseState().setHitPoints(hp); m.baseState().setMana(1000); m.recoverMaxState(); m.resetToMaxState(); return m; }
var V = npc("Fixc5pip", 1000), tam = npc("Fixc5tam", 1000), bo = npc("Fixc5bo", 1000);
[V, tam, bo].forEach(function (m) { m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); });
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var dragon = npc("Fixc5dragon", 1000);
function fn(S, f) { return S.callFunction(dragon, f, "", V); }
function sleep(m, on) {
	var d = m.basePhyStats().disposition();
	m.basePhyStats().setDisposition(on ? (d | CM.Common.interfaces.PhyStats.IS_SLEEPING) : (d & ~CM.Common.interfaces.PhyStats.IS_SLEEPING));
	m.recoverPhyStats();
}

// named alarms
["FIXC5A", "FIXC5T"].forEach(clearVar);
var TXT = "FUNCTION_PROG SETUP\nmpalarm NAME=roar 3 mpsetvar FIXTEST FIXC5A roared\n~\n"
	+ "FUNCTION_PROG ASK\nmpsetvar FIXTEST FIXC5T $%ALARM($i roar)%\n~\n"
	+ "FUNCTION_PROG STOP\nmpalarmcancel roar\n~\n";
var S = scriptOn(dragon, TXT);
fn(S, "SETUP");
fn(S, "ASK");
check("named", S.alarmTicksLeft("roar") == 3 && gvar("FIXC5T") == "3", "ticks " + S.alarmTicksLeft("roar") + ", ALARM() '" + gvar("FIXC5T") + "'");
fn(S, "SETUP");
check("replaced", S.pendingResponses().size() == 1 && has(S.pendingResponses().get(0), "MPALARM ROAR"), "" + S.pendingResponses());
var state = s(S.getStateXML());
S.dequeResponses(null); S.dequeResponses(null);
fn(S, "STOP");
S.dequeResponses(null); S.dequeResponses(null);
check("cancel", S.alarmTicksLeft("roar") == -1 && gvar("FIXC5A") == "", "ticks " + S.alarmTicksLeft("roar") + " ran '" + gvar("FIXC5A") + "'");
// saved, and back
dragon.delScript(S); // a mob takes no second copy of a script it has
var S2 = CMClass.getCommon("DefaultScriptingEngine");
S2.setScript(TXT);
S2.setStateXML(state); // as a saved game puts it back: applied when the script loads
dragon.addScript(S2);
fn(S2, "ASK"); // the first call loads the script, and the state with it
clearVar("FIXC5T");
fn(S2, "ASK");
check("saved", state.indexOf("ALARM_NAMED_ROAR") >= 0 && S2.alarmTicksLeft("roar") == 3 && gvar("FIXC5T") == "3", "restored ticks " + S2.alarmTicksLeft("roar") + " ALARM() '" + gvar("FIXC5T") + "' from " + state);
for (var i = 0; i < 3; i++) S2.dequeResponses(null);
check("fires", gvar("FIXC5A") == "roared", "the alarm did not go off");
dragon.delScript(S2);
dragon.addScript(S);
// one knocked out loses its named alarm
clearVar("FIXC5A");
fn(S, "SETUP");
sleep(dragon, true);
S.dequeResponses(null);
sleep(dragon, false);
for (var j = 0; j < 3; j++) S.dequeResponses(null);
check("knocked-out", S.alarmTicksLeft("roar") == -1 && gvar("FIXC5A") == "", "still queued " + S.alarmTicksLeft("roar") + " ran '" + gvar("FIXC5A") + "'");
dragon.delScript(S);

// MPTELEGRAPH
["FIXC5H_Fixc5pip", "FIXC5H_Fixc5tam", "FIXC5H_Fixc5bo"].forEach(clearVar);
var T = scriptOn(dragon, "FUNCTION_PROG BREATH\nmptelegraph 2 \"<S-NAME> rears back, flames gathering in its throat!\" mpsetvar FIXTEST FIXC5H_$n hit INTERRUPT=STUN,DAMAGE>20% AVOID=HIDE,COMMAND:brace\n~\n");
out();
fn(T, "BREATH");
var P = dragon.fetchEffect("Prop_Telegraph");
check("warned", P != null && has(out(), "rears back, flames gathering"), "no warning");
R.send(V, CMClass.getMsg(V, dragon, null, CMMsg.MSG_LOOK, null));
check("look", has(out(), "is gathering power"), "LOOK does not show it");
tam.basePhyStats().setDisposition(tam.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_HIDDEN); tam.recoverPhyStats();
CMLib.commands().handleUnknownCommand(bo, new java.util.ArrayList(java.util.Arrays.asList(["brace"])));
check("brace", field(P, "avoided").contains(CM.core.InstanceIds.reference(bo)), "bracing did not count");
tickMob(dragon);
check("each-tick", has(out(), "rears back") && dragon.fetchEffect("Prop_Telegraph") != null && gvar("FIXC5H_Fixc5pip") == "", "no warning on the tick, or too soon");
tickMob(dragon);
check("hits", gvar("FIXC5H_Fixc5pip") == "hit" && gvar("FIXC5H_Fixc5tam") == "" && gvar("FIXC5H_Fixc5bo") == "" && dragon.fetchEffect("Prop_Telegraph") == null,
	"pip '" + gvar("FIXC5H_Fixc5pip") + "' tam '" + gvar("FIXC5H_Fixc5tam") + "' bo '" + gvar("FIXC5H_Fixc5bo") + "'");
tam.basePhyStats().setDisposition(tam.basePhyStats().disposition() & ~CM.Common.interfaces.PhyStats.IS_HIDDEN); tam.recoverPhyStats();
// INTERRUPT=DAMAGE>20%
clearVar("FIXC5H_Fixc5pip");
fn(T, "BREATH");
out();
CMLib.combat().postDamage(V, dragon, null, 250, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("damage", dragon.fetchEffect("Prop_Telegraph") == null && T.alarmTicksLeft("TELEGRAPH") == -1 && has(out(), "interrupted"), "not interrupted by a fifth of its hit points");
dragon.makePeace(true); V.makePeace(true); dragon.resetToMaxState();
// stunned (asleep, held...): always interrupted
fn(T, "BREATH");
sleep(dragon, true);
tickMob(dragon); tickMob(dragon);
sleep(dragon, false);
check("stun", dragon.fetchEffect("Prop_Telegraph") == null && gvar("FIXC5H_Fixc5pip") == "", "hit while knocked out");
dragon.delScript(T);
dragon.makePeace(true);

// GenAbility WINDUP
var GA = CMClass.getAbility("GenAbility");
GA.setStat("CLASS", "Fixc5Bite");
GA.setStat("NAME", "Fixc5 bite");
GA.setStat("CASTMSG", "<S-NAME> bites <T-NAME>!");
GA.setStat("WINDUP", "2");
GA.setStat("WINDUPMSG", "<S-NAME> bares its fangs at <T-NAME>!");
GA.setProficiency(100);
check("stats", s(GA.getStat("WINDUP")) == "2" && has(GA.getStat("ALLXML"), "<WINDUPMSG>"), "WINDUP " + GA.getStat("WINDUP"));
dragon.addAbility(GA);
var A = dragon.fetchAbility("Fixc5Bite");
out();
var used = A.invoke(dragon, new java.util.ArrayList(java.util.Arrays.asList(["Fixc5pip"])), V, false, 0);
var w = out();
check("windup", used && dragon.fetchEffect("Prop_Telegraph") != null && has(w, "bares its fangs at") && !has(w, "bites"), "used " + used + " saw " + w);
tickMob(dragon);
var mid = out();
tickMob(dragon);
var fin = out();
check("release", has(mid, "bares its fangs") && (has(fin, "bites") || has(fin, "attempts to use")) && dragon.fetchEffect("Prop_Telegraph") == null, "then " + mid + " / " + fin);
check("help", has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPTELEGRAPH") && has(CMLib.help().getHelpText("PROP_TELEGRAPH", M, true), "WINDUP"), "no help");

[dragon, V, tam, bo].forEach(function (m) { m.makePeace(true); m.setPlayerStats(null); m.destroy(); });
R.destroy();
done();
