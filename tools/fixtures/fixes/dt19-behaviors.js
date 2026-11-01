// DT19: STAT BEHAVIORS shows each behaviour's state (getStat DEBUG), with keys its help does not name; TRACEB tells its decisions.
FIXFILE = "dt19-behaviors";
var R = M.location();
var TB = CMClass.getCommand("TraceBCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixbehaviourer");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function traceb(words) { var L = new java.util.ArrayList(); L.add("TRACEB"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); TB.execute(B, L, 0); return out(); }

var actor = newMob("GenMob", R);
actor.setName("Fixemoter");
var E = CMClass.getBehavior("Emoter");
E.setParms("min=1 max=1 chance=100 chanse=5;smiles at nothing.");
actor.addBehavior(E);
var stat = "" + call(TB, "describeBehaviors", [B, "Fixemoter"]);
check("stat", has(stat, "Behaviours of Fixemoter:") && has(stat, "1. Emoter: parameters: min=1 max=1 chance=100 chanse=5;smiles at nothing."), stat);
check("ticks", has(stat, "acts in ") && has(stat, " ticks (every 1 to 1, 100% chance)") && has(stat, "has not acted yet"), stat);
check("unknown-key", has(stat, "unknown key CHANSE (AHELP BEHAVIOR_EMOTER)") && !has(stat, "unknown key MIN"), stat);
check("debug", has("" + E.getStat("DEBUG"), "parameters: "), "getStat DEBUG said " + E.getStat("DEBUG"));
// TRACEB: its decisions, for a while
check("traceb", has(traceb("Fixemoter 50"), "TRACEB: the decisions of Fixemoter's timed behaviours, for 50 ticks."), "TRACEB did not start");
tickMob(actor); tickMob(actor); tickMob(actor);
var t = out();
check("acts", has(t, "[traceb Fixemoter] Emoter: acts (rolled ") && has(t, " of 100); next in "), t);
check("acted", has("" + call(TB, "describeBehaviors", [B, "Fixemoter"]), "last acted "), "STAT BEHAVIORS does not say it acted");
// a host that cannot act
actor.basePhyStats().setDisposition(actor.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_SLEEPING);
actor.recoverPhyStats();
tickMob(actor); tickMob(actor);
check("cannot", has(out(), "its time came, but the host cannot act"), "no word of the sleeping host");
check("off", has(traceb("OFF"), "TRACEB is off."), "OFF said otherwise");
tickMob(actor);
check("quiet", !has(out(), "[traceb"), "traced after OFF");
// a room without behaviours
check("none", has("" + call(TB, "describeBehaviors", [B, "HERE"]), "Behaviours of "), "HERE");
B.setSession(null);
B.destroy(); actor.destroy();
done();
