// JS20: cm.command (a command of the world's) and cm.verb (a phrase near one thing).
FIXFILE = "js20-verbs";
var R = M.location();
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function list(t) { var L = new java.util.ArrayList(); var w = t.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
["FIXJS20C", "FIXJS20V", "FIXJS20Y"].forEach(clearVar);
var B = newMob("GenMob", R);
B.setName("Fixjs20digger");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var o = all.substring(mark); mark = all.length; return o; }

// a command
var id = cm.command("fixjs20dig", {help: "FIXJS20DIG [direction]: dig a hole.", cost: 2, combatCost: 3, words: ["fixjs20excavate"]},
	function(mob, args) { cm.var("FIXTEST", "FIXJS20C", mob.Name() + ":" + args); });
var C = CMClass.findCommandByTrigger("FIXJS20DIG", true);
check("command", C != null && s(id) == "JSFIXJS20DIG" && s(C.ID()) == "JSFIXJS20DIG" && CMClass.findCommandByTrigger("FIXJS20EXCAVATE", true) == C, "command " + C);
check("cost", C.actionsCost(B, null) == 2 && C.combatActionsCost(B, null) == 3, "costs " + C.actionsCost(B, null) + "/" + C.combatActionsCost(B, null));
C.execute(B, list("fixjs20dig north"), 0);
check("run", gvar("FIXJS20C") == "Fixjs20digger:north", "FIXJS20C='" + gvar("FIXJS20C") + "'");
C.execute(B, list("fixjs20dig ?"), 0);
check("help", has(out(), "FIXJS20DIG [direction]: dig a hole."), "the help");
cm.command("fixjs20dig", function(mob, args) { cm.var("FIXTEST", "FIXJS20C", "again:" + args); });
var C2 = CMClass.findCommandByTrigger("FIXJS20DIG", true);
C2.execute(B, list("fixjs20dig up"), 0);
check("replaced", C2 != C && gvar("FIXJS20C") == "again:up" && CMClass.findCommandByTrigger("FIXJS20EXCAVATE", true) == null, "FIXJS20C='" + gvar("FIXJS20C") + "'");
check("uncommand", cm.uncommand("fixjs20dig") && CMClass.findCommandByTrigger("FIXJS20DIG", true) == null, "uncommand");

// a phrase near one thing
var lever = CMClass.getItem("GenItem");
lever.setName("a fixjs20 lever");
R.addItem(lever);
cm.verb(lever, "pull lever", function(mob, rest) { cm.var("FIXTEST", "FIXJS20V", "pulled:" + rest); });
check("verb", SE.verb(B, list("pull the lever")) && gvar("FIXJS20V") == "pulled:the lever", "FIXJS20V='" + gvar("FIXJS20V") + "'");
check("verb-other", !SE.verb(B, list("pull the rope")), "another phrase goes on to the game");
cm.verb(lever, "fixjs20yank lever", function(mob, rest) { cm.var("FIXTEST", "FIXJS20Y", "yanked"); });
R.delItem(lever);
out();
check("not-here", SE.verb(B, list("fixjs20yank lever")) && has(out(), "You can't do that here.") && gvar("FIXJS20Y") == "", "away from the lever");
check("not-here-cmd", !SE.verb(B, list("pull lever")) || CMClass.findCommandByTrigger("PULL", true) == null, "a real command goes on to the game");
check("unverb", cm.unverb(lever, "pull lever") == 1, "unverb");
CM.core.JSVerbs.clear();
lever.destroy(); B.destroy();
done();
