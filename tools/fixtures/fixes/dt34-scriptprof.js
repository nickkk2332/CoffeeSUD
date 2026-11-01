// DT34: SCRIPTPROF counts and times each script trigger's runs; SCRIPTSLOW= logs the slow ones; a capped MPWHILE says so.
FIXFILE = "dt34-scriptprof";
var SP = CM.core.ScriptProf;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixprofiler");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function prof(words) { var L = new java.util.ArrayList(); L.add("SCRIPTPROF"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("ScriptProfCmd").execute(B, L, 0); return out(); }

check("on", has(prof("ON"), "SCRIPTPROF is on"), "ON");
prof("RESET");
var s = scriptedMob("a fixes busy scripter", "RAND_PROG 100\nmpsetvar $i FIXN 0\nmpwhile (VAR($i FIXN < 20)) mpsetvar $i FIXN ++\n~\n");
tickMob(s); tickMob(s); tickMob(s);
var said = prof("REPORT 5");
check("report", has(said, "SCRIPTPROF: 1 trigger lines") && has(said, " in 3 runs, at most ") && has(said, "(a fixes busy scripter @ ") && has(said, "inline:1 RAND_PROG 100"), said);
check("area", has(prof("REPORT AREA"), " in " + R.getArea().Name() + "."), "REPORT AREA");
// nested runs (the MPWHILE's body) count in the trigger's own
check("nested", !has(said, "MPWHILE_"), said);
check("off", has(prof("OFF"), "SCRIPTPROF is off") && !SP.isOn(), "OFF");
tickMob(s);
check("off-counts", has(prof("REPORT"), " in 3 runs,"), "a run counted while off");
check("reset", has(prof("RESET"), "forgot") && has(prof("REPORT"), "0 trigger lines"), "RESET");
s.destroy();
// SCRIPTSLOW: a run longer than it is logged, with its host and line
SP.setSlowMs(1);
var Log = CM.core.Log;
var slow = scriptedMob("a fixes slow scripter", "RAND_PROG 100\nmpsetvar $i FIXN 0\nmpwhile (VAR($i FIXN < 3000)) mpsetvar $i FIXN ++\n~\n");
var seen = CM.core.DevLog.last(200, CM.core.DevLog.Level.WARN, "ScriptProf").size();
tickMob(slow);
var after = CM.core.DevLog.last(200, CM.core.DevLog.Level.WARN, "ScriptProf");
check("slow", after.size() > seen && has(after.toString(), "slow script: ") && has(after.toString(), "a fixes slow scripter @ "), "the log had " + after);
SP.setSlowMs(0);
slow.destroy();
B.setSession(null);
B.destroy();
done();
