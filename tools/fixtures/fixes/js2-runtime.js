// JS2: every script has a time limit, and its errors say where it is.
FIXFILE = "js2-runtime";
var JSR = CM.core.JSRuntime;

// a runaway loop on a thread of its own (a fresh context, with the usual
// limit) is stopped; a script's catch cannot keep it going
var t = new java.lang.Thread(new java.lang.Runnable({ run: function() {
	var n = 0;
	while (true) { try { n++; } catch (e) { } }
}}));
var started = java.lang.System.currentTimeMillis();
t.start();
t.join(15000);
var took = java.lang.System.currentTimeMillis() - started;
check("runaway-stopped", !t.isAlive(), "still running after " + took + " ms");
check("limit-is-seconds", took >= 1000 && took < 15000, "stopped after " + took + " ms");
check("default-limit", JSR.limitSeconds() == 2, "limit " + JSR.limitSeconds());

// an error in a script block names its host, room and trigger
var before = JSR.recentErrors(100).size();
var bad = scriptedMob("a broken oracle", "RAND_PROG 100\n<SCRIPT>\nvar x = undefinedThing.y;\n</SCRIPT>\n~\n");
tickMob(bad);
var errs = JSR.recentErrors(100);
var last = errs.size() > 0 ? "" + errs.get(errs.size() - 1) : "";
check("error-recorded", errs.size() > before, "no error recorded");
check("error-names-host", last.indexOf("a broken oracle") >= 0 && last.indexOf("RAND_PROG") >= 0, "'" + last + "'");
// the line is the script's own (DT10): the inline script's third
check("error-has-line", last.indexOf("<SCRIPT> in inline line 3:") >= 0, "'" + last + "'");
bad.destroy();
done();
