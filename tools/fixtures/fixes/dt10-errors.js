// DT10: script errors say the file and line, unknown and ambiguous MP words are errors, and SCRIPTERRORS lists them.
FIXFILE = "dt10-errors";
var SE = CM.core.ScriptErrors;
var cmd = CMClass.getCommand("ScriptErrorsCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function kept(part) { return "" + SE.recent(50, part); }
var B = newMob("GenMob", M.location());
B.setName("Fixerrwatcher");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
SE.clear();

// an inline script: an unknown MP word, an ambiguous one, an unknown function
clearVar("FIXERROK"); clearVar("FIXERRAFTER");
var bad = scriptedMob("a fixes erring scripter", "RAND_PROG 100\nmpsetvar FIXTEST FIXERROK 1\nmpechot $n hello\nmpse FIXTEST FIXERRX 2\nmpcallfunc nosuchfunc10\nmpsetvar FIXTEST FIXERRAFTER 1\n~\n");
tickMob(bad);
check("runs-on", gvar("FIXERROK") == "1" && gvar("FIXERRAFTER") == "1", "the script stopped at an error");
check("unknown", has(kept("MPECHOT"), "inline:3 MPECHOT Syntax: unknown command MPECHOT (a fixes erring scripter @ "), "kept: " + kept("MPECHOT"));
check("ambiguous", has(kept("MPSE "), "inline:4 MPSE Syntax: ambiguous command MPSE: MPSET, MPSETVAR"), "kept: " + kept("MPSE "));
check("function", has(kept("nosuchfunc10"), "inline:5 MPCALLFUNC Unknown: Function: nosuchfunc10"), "kept: " + kept("nosuchfunc10"));
// not shown to the room as it was ("Scripting Error: ...")
check("not-room", !has(out(), "Scripting Error"), "the room saw: " + out());
bad.destroy();

// a loaded file: the MP error and the JavaScript error at the file's lines
clearVar("FIXERRFILE");
var loaded = newMob("GenMob", M.location());
loaded.setName("Fixerrloader");
var LS = scriptOn(loaded, "LOAD=_fixes/dt10err.script");
LS.preApproveScripts();
tickMob(loaded);
check("file-ran", gvar("FIXERRFILE") == "ran", "the loaded script did not run");
check("file-line", has(kept("MPNOSUCHTHING"), "_fixes/dt10err.script:4 MPNOSUCHTHING Syntax: unknown command MPNOSUCHTHING"), "kept: " + kept("MPNOSUCHTHING"));
check("js-line", has(kept("fixesUndefinedFunction"), "<SCRIPT> in _fixes/dt10err.script line 7:"), "kept: " + kept("fixesUndefinedFunction"));
loaded.destroy();

// SCRIPTERRORS lists them, by text, and CLEAR forgets them
var L = new java.util.ArrayList(); L.add("SCRIPTERRORS"); L.add("MPECHOT");
cmd.execute(B, L, 0);
check("command", has(out(), "Script errors, oldest first:") && has(out(), "inline:3 MPECHOT"), "SCRIPTERRORS said " + out());
L = new java.util.ArrayList(); L.add("SCRIPTERRORS"); L.add("CLEAR");
cmd.execute(B, L, 0);
check("clear", ("" + SE.recent(50, null)) == "[]", "CLEAR kept " + SE.recent(50, null));
check("mode", ("" + SE.mode()) == "LOG", "a normal game's default is " + SE.mode());

["FIXERROK", "FIXERRAFTER", "FIXERRFILE"].forEach(clearVar);
B.setSession(null);
B.destroy();
done();
