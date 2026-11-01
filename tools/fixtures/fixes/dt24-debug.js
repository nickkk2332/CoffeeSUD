// DT24: MPDEBUG and debug() write to the developer log, saying where they came from.
FIXFILE = "dt24-debug";
var DevLog = CM.core.DevLog;
var R = M.location();
var at = "" + CMLib.map().getExtendedRoomID(R);
// lines wrap in the session: spaces and line breaks as one space
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixdebugwatcher");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
var oldWatcher = DevLog.watcher(), oldSetting = "" + DevLog.setting();
// a narrow watch: errors from one module; the debug lines come anyway
DevLog.watch(B, DevLog.Level.ERROR, "Nothingfix*");

var s = scriptedMob("a fixes debugger", "RAND_PROG 100\nmpsetvar $i FIXDBG 5\nmpdebug chose x=$<$i FIXDBG>\n~\n");
tickMob(s);
var told = out();
check("mpdebug", has(told, "[debug a fixes debugger@" + at + " inline:3] chose x=5"), told);
check("mpdebug-kept", has(DevLog.last(20, DevLog.Level.DEBUG, "MPDEBUG").toString(), "chose x=5"), "DEVLOG LAST had no line");
check("not-in-room", !has(out(), "chose"), "the line came twice");
s.destroy();

// debug() in a script's <SCRIPT>: objects as JSON, with the line in the file
var j = scriptedMob("a fixes jsdebugger", "RAND_PROG 100\n<SCRIPT>\ndebug({a: 1, b: [2, 3]});\ndebug('host ' + host().name());\n</SCRIPT>\n~\n");
tickMob(j);
told = out();
check("js-object", has(told, "[debug a fixes jsdebugger@" + at + " inline:3] {\"a\":1,\"b\":[2,3]}"), told);
check("js-string", has(told, "[debug a fixes jsdebugger@" + at + " inline:4] host a fixes jsdebugger"), told);
j.destroy();

// JCONSOLE and JRUN have it too
var L = new java.util.ArrayList(); L.add("JCONSOLE"); L.add("debug('from the fixes console')");
// JCONSOLE runs only for a character with a session: the player; the line still comes to the watcher
CMClass.getCommand("JConsole").execute(M, L, 0);
told = out();
check("jconsole", has(told, "[debug JCONSOLE " + M.Name() + " line 1] from the fixes console"), told);
new CM.core.CMFile("/resources/_fixes/dt24run.js", M).saveText("var n = 6 * 7;\ndebug('jrun says ' + n);\n");
L = new java.util.ArrayList(); L.add("JRUN"); L.add("/resources/_fixes/dt24run.js");
CMClass.getCommand("JRun").execute(M, L, 0);
told = out();
check("jrun", has(told, "[debug JRUN " + M.Name() + " /resources/_fixes/dt24run.js:2] jrun says 42"), told);
// the static checker knows the command
var problems = "" + CM.core.ScriptCheck.checkText("inline", "RAND_PROG 100\nmpdebug hello\n~\n", null);
check("scriptcheck", problems.toUpperCase().indexOf("MPDEBUG") < 0, "SCRIPTCHECK: " + problems);

// put the watch back as it was
if (oldWatcher != null) {
	var parts = oldSetting.split(" ");
	DevLog.watch(oldWatcher, DevLog.Level.valueOf(parts[0]), parts.length > 1 ? parts.slice(1).join(" ") : "");
} else
	DevLog.unwatch();
B.setSession(null);
B.destroy();
done();
