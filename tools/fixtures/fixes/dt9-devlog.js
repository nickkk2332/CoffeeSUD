// DT9: DEVLOG tells the builder each line logged, by level and module, a repeat once with a count, and replays the last ones.
FIXFILE = "dt9-devlog";
var Log = CM.core.Log;
var cmd = CMClass.getCommand("DevLogCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", M.location());
B.setName("Fixlogwatcher");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function devlog(words) { var L = new java.util.ArrayList(); L.add("DEVLOG"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(B, L, 0); return out(); }
var was = "" + CM.core.DevLog.setting();

check("on", has(devlog("ON WARN MODULE Fixmod*,Scripting"), "you are told each line logged at WARN or worse, from Fixmod*,Scripting"), "ON said otherwise");
Log.errOut("Fixmodule", "the first fixes error");
check("error", has(out(), "[ERROR Fixmodule]^N the first fixes error"), "the error was not told");
Log.warnOut("FixmodTwo", "a fixes warning");
check("warn", has(out(), "[WARN FixmodTwo]^N a fixes warning"), "the warning was not told");
Log.infoOut("Fixmodule", "a fixes note");
check("level", !has(out(), "a fixes note"), "an INFO line was told at WARN");
Log.errOut("Elsewhere", "a fixes error elsewhere");
check("module", !has(out(), "elsewhere"), "a line from another module was told");
// a repeat is told once, with a count when something else comes
Log.errOut("Fixmodule", "the same fixes error");
Log.errOut("Fixmodule", "the same fixes error");
Log.errOut("Fixmodule", "the same fixes error");
var rep = out();
check("repeat-once", rep.split("the same fixes error").length == 2, "the repeat was told " + (rep.split("the same fixes error").length - 1) + " times");
Log.errOut("Fixmodule", "a different fixes error");
var after = out();
check("repeat-count", has(after, "(the last line 2 more times)") && has(after, "a different fixes error"), "after the repeats: " + after);
// a script's error comes too
var bad = scriptedMob("a fixes broken scripter", "RAND_PROG 100\nmpcallfunc nosuchfixfunc\n~\n");
tickMob(bad);
check("script-error", has(out(), "[ERROR Scripting]^N a fixes broken scripter"), "the script's error was not told");
bad.destroy();
// LAST replays, by level and module, repeats counted
var last = devlog("LAST 50 ERROR MODULE Fixmod*");
check("last", has(last, "the first fixes error") && has(last, "the same fixes error ^w(x3)^N") && !has(last, "a fixes warning") && !has(last, "elsewhere"), "LAST said " + last);
check("last-info", has(devlog("LAST 50 INFO MODULE Fixmodule"), "a fixes note"), "LAST INFO did not have the note");
// CLEAR forgets them; OFF stops the telling
devlog("CLEAR");
check("clear", !has(devlog("LAST 50 DEBUG MODULE Fixmod*"), "fixes"), "CLEAR kept lines");
check("off", has(devlog("OFF"), "DEVLOG is off."), "OFF said otherwise");
Log.errOut("Fixmodule", "a fixes error after OFF");
check("off-quiet", !has(out(), "after OFF"), "told after OFF");
check("setting", ("" + CM.core.DevLog.setting()) == "OFF", "the setting is " + CM.core.DevLog.setting());
check("module-mask", CM.core.DevLog.matches("Scripting,Quest*", "QuestEngine") && CM.core.DevLog.matches("Scripting,Quest*", "scripting") && !CM.core.DevLog.matches("Scripting,Quest*", "WorldFiles"), "the mask matched wrongly");

B.setSession(null);
B.destroy();
done();
