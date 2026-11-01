// DT11: SCRIPTCHECK finds, by file and line, what a script would get wrong, without running it.
FIXFILE = "dt11-scriptcheck";
var SC = CM.core.ScriptCheck;
var cmd = CMClass.getCommand("ScriptCheckCmd");
function has(list, part) { return ("" + list).indexOf(part) >= 0; }
var F = "_fixes/dt11bad.script:";
clearVar("FIXDT11");
var bad = "" + SC.checkFile("_fixes/dt11bad.script");
check("trigger", has(bad, F + "2 unknown trigger GREETPROG"), bad);
check("command", has(bad, F + "7 unknown command MPECHOT"), bad);
check("prefix", has(bad, F + "8 MPSE is not a command; write MPSET or MPSETVAR"), bad);
check("value-func", has(bad, F + "10 unknown function $%NOSUCHVALUE()%"), bad);
check("cond-func", has(bad, F + "11 unknown function NOSUCHCOND()"), bad);
check("next", has(bad, F + "14 NEXT without FOR"), bad);
check("callfunc", has(bad, F + "15 MPCALLFUNC NOSUCHFUNCTION: no such FUNCTION_PROG") && !has(bad, "REALFUNCTION: no such"), bad);
check("room", has(bad, F + "17 MPGOTO: no room NoSuchArea#99"), bad);
check("game-command", has(bad, F + "18 unknown command FLURBLE") && !has(bad, "unknown command SAY") && !has(bad, "unknown command SMILE"), bad);
check("javascript", has(bad, F + "20 JavaScript: "), bad);
check("if-open", has(bad, F + "6 IF without ENDIF") && !has(bad, F + "11 IF without"), bad);
check("for-open", has(bad, F + "24 FOR without NEXT"), bad);
check("ran-nothing", gvar("FIXDT11") == "", "the check ran the script");

// a host: its own script, with LOAD= followed, and a missing file
var host = newMob("GenMob", M.location());
host.setName("Fixchecked");
scriptOn(host, "LOAD=_fixes/dt7loaded.script~LOAD=_fixes/dt11-missing.script");
var hp = "" + SC.checkHost(host, null);
check("host-missing", has(hp, "Fixchecked (inline):1 LOAD=_fixes/dt11-missing.script: no such file"), hp);
check("host-clean", !has(hp, "dt7loaded"), "the clean loaded file had problems: " + hp);
check("clean", ("" + SC.checkFile("_fixes/dt7loaded.script")) == "[]", "" + SC.checkFile("_fixes/dt7loaded.script"));

// the command, on a file and on a target
var B = newMob("GenMob", M.location());
B.setName("Fixcheckreader");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
var L = new java.util.ArrayList(); L.add("SCRIPTCHECK"); L.add("_fixes/dt11bad.script");
cmd.execute(B, L, 0);
check("cmd-file", has(out(), "SCRIPTCHECK _fixes/dt11bad.script: 12 problems:"), out());
L = new java.util.ArrayList(); L.add("SCRIPTCHECK"); L.add("Fixchecked");
cmd.execute(B, L, 0);
check("cmd-target", has(out(), "SCRIPTCHECK Fixchecked: 1 problems:"), out());
// the whole world, which ships without problems
host.destroy();
var all = SC.checkAll();
check("world", all.size() == 0, all.size() + " problems in the world: " + all);

B.setSession(null);
B.destroy();
done();
