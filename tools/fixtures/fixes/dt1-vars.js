// DT1: VARS lists, sets, clears and watches script variables; VARNAMES lists names.
FIXFILE = "dt1-vars";
var R = M.location();
var cmd = CMClass.getCommand("Vars");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
// a builder whose output is kept
var B = newMob("GenMob", R);
B.setName("Fixwatcher");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
function vars(words) { var L = new java.util.ArrayList(); L.add("VARS"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(B, L, 0); }
function listing(scope, host, mask) { return "" + String(call(cmd, "listing", [scope, host, mask])); }

// SET, as MPSETVAR does: ++ adds
vars("SET FIXHOST FIXA 5");
vars("SET FIXHOST FIXA ++");
check("set", ("" + VARS.getVar("FIXHOST", "FIXA")) == "6", "FIXA is '" + VARS.getVar("FIXHOST", "FIXA") + "'");
// SET in a scope of its own
vars("SET FIXHOST FIXB hello world SCOPE FIXSCOPE");
var scoped = CMClass.getCommon("DefaultScriptingEngine");
scoped.setVarScope("FIXSCOPE");
check("set-scope", ("" + scoped.getVar("FIXHOST", "FIXB")) == "hello world" && ("" + VARS.getVar("FIXHOST", "FIXB")) == "",
	"in the scope '" + scoped.getVar("FIXHOST", "FIXB") + "', global '" + VARS.getVar("FIXHOST", "FIXB") + "'");

// a script that asks for the global scope shares the store unscoped ones use
var globalist = scriptedMob("a fixes globalist", "RAND_PROG 100\nmpsetinternal SCOPE GLOBAL\nmpsetvar FIXGLOBHOST FIXG shared\n~\n");
tickMob(globalist);
check("scope-global", ("" + VARS.getVar("FIXGLOBHOST", "FIXG")) == "shared", "SCOPE GLOBAL wrote elsewhere: '" + VARS.getVar("FIXGLOBHOST", "FIXG") + "'");
VARS.setVar("FIXGLOBHOST", "FIXG", "");
globalist.destroy();

// LIST by scope, host and mask
var all = listing("*", "FIXHOST", "FIX*");
check("list", has(all, "GLOBAL  FIXHOST  FIXA = 6") && has(all, "FIXSCOPE  FIXHOST  FIXB = hello world"), "the list is " + all);
var only = listing("FIXSCOPE", "*", "*");
check("list-scope", has(only, "FIXB") && !has(only, "FIXA"), "FIXSCOPE gave " + only);
vars("LIST * FIXHOST FIXA");
check("list-cmd", has(out(), "FIXHOST  FIXA = 6"), "LIST said " + out());

// an engine that keeps its own variables is listed under INDIVIDUAL
var keeper = newMob("GenMob", R);
keeper.setName("Fixkeeper");
var own = CMClass.getCommon("DefaultScriptingEngine");
own.setVarScope("*");
own.setScript("");
keeper.addScript(own);
own.setVar("FIXIND", "FIXC", "7");
var ind = listing("INDIVIDUAL", "FIXIND", "*");
check("individual", has(ind, "INDIVIDUAL:Fixkeeper  FIXIND  FIXC = 7"), "INDIVIDUAL gave " + ind);

// DUMP writes the list to a file (the builder may write files)
var dumpL = new java.util.ArrayList(); dumpL.add("VARS"); dumpL.add("DUMP"); dumpL.add("/resources/_fixes/fixvars.txt");
cmd.execute(M, dumpL, 0);
var dumped = "" + new CM.core.CMFile("/resources/_fixes/fixvars.txt", M).text();
check("dump", has(dumped, "GLOBAL  FIXHOST  FIXA = 6") && has(dumped, "FIXSCOPE  FIXHOST  FIXB = hello world"), "the file holds '" + dumped + "'");

// SCOPES counts them
vars("SCOPES");
check("scopes", has(out(), "FIXSCOPE: 1 hosts, 1 variables"), "SCOPES said " + out());

// WATCH tells of each change; UNWATCH stops it
vars("WATCH FIXHOST FIXA");
VARS.setVar("FIXHOST", "FIXA", "9");
check("watch", has(out(), "GLOBAL FIXHOST FIXA = '9'"), "no word of the change: " + out());
var setter = scriptedMob("a fixes setter", "RAND_PROG 100\nmpsetvar FIXHOST FIXA 12\n~\n");
tickMob(setter);
check("watch-where", has(out(), "FIXA = '12' (an inline script in "), "no word of where: " + out());
vars("UNWATCH");
VARS.setVar("FIXHOST", "FIXA", "10");
check("unwatch", !has(out(), "FIXA = '10'"), "told after UNWATCH");

// CLEAR one, then all a host has
vars("CLEAR FIXHOST FIXA");
check("clear", ("" + VARS.getVar("FIXHOST", "FIXA")) == "", "FIXA is still '" + VARS.getVar("FIXHOST", "FIXA") + "'");

// VARNAMES in a script, as a value and as a condition
VARS.setVar("FIXNAMER", "QUEST_ONE", "1");
VARS.setVar("FIXNAMER", "QUEST_TWO", "2");
VARS.setVar("FIXNAMER", "OTHER", "3");
clearVar("FIXNAMES"); clearVar("FIXNAMEOK");
var namer = scriptedMob("a fixes namer", "RAND_PROG 100\nmpsetvar FIXTEST FIXNAMES $%VARNAMES(FIXNAMER QUEST_)%\nif varnames(FIXNAMER QUEST_ == QUEST_ONE QUEST_TWO)\n  mpsetvar FIXTEST FIXNAMEOK yes\nendif\n~\n");
tickMob(namer);
check("varnames", gvar("FIXNAMES") == "QUEST_ONE QUEST_TWO", "VARNAMES gave '" + gvar("FIXNAMES") + "'");
check("varnames-if", gvar("FIXNAMEOK") == "yes", "the condition did not hold");

vars("CLEAR FIXNAMER");
VARS.setVar("FIXHOST", "FIXB", "");
B.setSession(null);
B.destroy(); keeper.destroy(); setter.destroy(); namer.destroy();
done();
