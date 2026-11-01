// DT7: SCRIPT lists, fires, calls, resets, disables and enables a host's triggers, and lists its alarms.
FIXFILE = "dt7-script";
var R = M.location();
var cmd = CMClass.getCommand("ScriptCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
// a builder whose output is kept
var B = newMob("GenMob", R);
B.setName("Fixscripter");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function script(words) { var L = new java.util.ArrayList(); L.add("SCRIPT"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(B, L, 0); return out(); }

var text = "SPEECH_PROG hello there\nmpsetvar FIXTEST FIXSPEECH $n:$g\n~\n"
	+ "TIME_PROG 5 6\nmpsetvar FIXTEST FIXTIME ran\n~\n"
	+ "ONCE_PROG 100\nmpsetvar FIXTEST FIXONCE ++\n~\n"
	+ "FUNCTION_PROG FIXFUNC\nmpsetvar FIXTEST FIXFUNC $g\nreturn done\n~\n"
	+ "GIVE_PROG all\nmpsetvar FIXTEST FIXGIVE $o\n~\n"
	+ "RAND_PROG 100\nmpalarm 50 mpsetvar FIXTEST FIXALARM rang\n~\n";
var host = newMob("GenMob", R);
host.setName("Fixhost");
var S = scriptOn(host, text);
["FIXSPEECH", "FIXTIME", "FIXONCE", "FIXFUNC", "FIXGIVE", "FIXALARM"].forEach(clearVar);

// LIST: each trigger, with where it is written
var list = script("LIST Fixhost");
check("list", has(list, "1. SPEECH_PROG HELLO THERE  (inline:1)") && has(list, "2. TIME_PROG 5 6  (inline:4)") && has(list, "6. RAND_PROG 100  (inline:17)"), "LIST said " + list);

// a trigger in a loaded file is listed at its file and line, and fires
var loader = newMob("GenMob", R);
loader.setName("Fixloader");
scriptOn(loader, "LOAD=_fixes/dt7loaded.script");
var loaded = script("LIST Fixloader");
check("list-file", has(loaded, "1. GREET_PROG 100  (_fixes/dt7loaded.script:3)") && has(loaded, "2. BRIBE_PROG 10  (_fixes/dt7loaded.script:7)"), "LIST said " + loaded);
clearVar("FIXGREET");
script("FIRE Fixloader GREET");
check("fire-greet", gvar("FIXGREET") == "Fixscripter", "FIXGREET is '" + gvar("FIXGREET") + "'");
clearVar("FIXGREET");
loader.destroy();

// FIRE a SPEECH_PROG as someone else, with the words said
var bob = newMob("GenMob", R);
bob.setName("Fixbob");
var fired = script("FIRE Fixhost SPEECH hello AS Fixbob");
check("fire-speech", gvar("FIXSPEECH") == "Fixbob:hello", "FIXSPEECH is '" + gvar("FIXSPEECH") + "', and SCRIPT said " + fired);
check("fire-said", has(fired, "Ran 1 SPEECH_PROG of Fixhost, as Fixbob."), "SCRIPT said " + fired);
var none = script("FIRE Fixhost SPEECH goodbye");
check("fire-none", has(none, "Fixhost has no SPEECH_PROG answering 'goodbye'.") && gvar("FIXSPEECH") == "Fixbob:hello", "SCRIPT said " + none);
// a TIME_PROG at the hour given, without changing the clock
var hour = CMLib.time().globalClock().getHourOfDay();
script("FIRE Fixhost TIME_PROG 6");
check("fire-time", gvar("FIXTIME") == "ran" && CMLib.time().globalClock().getHourOfDay() == hour, "FIXTIME is '" + gvar("FIXTIME") + "'");
// a GIVE_PROG with an item
var sword = CMClass.getItem("GenItem");
sword.setName("a fixes sword");
B.addItem(sword);
script("FIRE Fixhost GIVE WITH sword");
check("fire-give", gvar("FIXGIVE") == "a fixes sword", "FIXGIVE is '" + gvar("FIXGIVE") + "'");

// CALL a FUNCTION_PROG with arguments; it says what the function returned
var called = script("CALL Fixhost FIXFUNC some words");
check("call", gvar("FIXFUNC") == "some words" && has(called, "FIXFUNC returned 'done'."), "FIXFUNC is '" + gvar("FIXFUNC") + "', and SCRIPT said " + called);
check("call-none", has(script("CALL Fixhost NOSUCH"), "Fixhost has no function NOSUCH."), "an unknown function");

// RESET ONCE: a ONCE_PROG runs again
tickMob(host); tickMob(host);
check("once", gvar("FIXONCE") == "1", "FIXONCE is '" + gvar("FIXONCE") + "'");
check("list-done", has(script("LIST Fixhost"), "3. ONCE_PROG 100  (inline:7) [done]"), "LIST did not mark the ONCE_PROG done");
check("reset-said", has(script("RESET Fixhost ONCE"), "Fixhost will run its ONCE_PROG again."), "RESET said otherwise");
tickMob(host);
check("reset", gvar("FIXONCE") == "2", "FIXONCE is '" + gvar("FIXONCE") + "' after RESET");

// ALARMS: the MPALARM the RAND_PROG queued, then forgotten
var alarms = script("ALARMS Fixhost");
check("alarms", has(alarms, "MPALARM in ") && has(alarms, "mpsetvar FIXTEST FIXALARM rang"), "ALARMS said " + alarms);
var cleared = script("ALARMS Fixhost CLEAR");
check("alarms-clear", has(cleared, "queued to run forgotten.") && has(script("ALARMS Fixhost"), "Fixhost has nothing queued to run."), "CLEAR said " + cleared);

// DISABLE stops a trigger answering what is said; ENABLE lets it again
clearVar("FIXSPEECH");
function say(words) {
	var msg = CMClass.getMsg(bob, null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) '" + words + "'.");
	S.executeMsg(host, msg);
	runQueued(S);
}
check("disable-said", has(script("DISABLE Fixhost SPEECH"), "Fixhost: SPEECH_PROG off."), "DISABLE said otherwise");
say("hello");
check("disable", gvar("FIXSPEECH") == "", "a disabled SPEECH_PROG ran: " + gvar("FIXSPEECH"));
check("list-off", has(script("LIST Fixhost"), "1. SPEECH_PROG HELLO THERE  (inline:1) [off]"), "LIST did not mark it off");
script("ENABLE Fixhost SPEECH");
say("hello");
check("enable", has(gvar("FIXSPEECH"), "Fixbob:"), "the enabled SPEECH_PROG did not run: '" + gvar("FIXSPEECH") + "'");

// the room as a target, and a script in a Scriptable behaviour
var RS = scriptOn(R, "FUNCTION_PROG FIXROOMFUNC\nmpsetvar FIXTEST FIXROOM yes\n~\n");
clearVar("FIXROOM");
script("CALL ROOM FIXROOMFUNC");
check("room", gvar("FIXROOM") == "yes", "the room's function did not run");
R.delScript(RS);
var beh = newMob("GenMob", R);
beh.setName("Fixbehaved");
var SB = CMClass.getBehavior("Scriptable");
SB.setParms("FUNCTION_PROG FIXBEHFUNC\nmpsetvar FIXTEST FIXBEH yes\n~\n");
beh.addBehavior(SB);
clearVar("FIXBEH");
script("CALL Fixbehaved FIXBEHFUNC");
check("behaviour", gvar("FIXBEH") == "yes", "the Scriptable behaviour's function did not run");
check("no-scripts", has(script("LIST Fixbob"), "Fixbob has no scripts."), "a mob without scripts");

["FIXSPEECH", "FIXTIME", "FIXONCE", "FIXFUNC", "FIXGIVE", "FIXALARM", "FIXROOM", "FIXBEH"].forEach(clearVar);
B.setSession(null);
B.destroy(); host.destroy(); bob.destroy(); beh.destroy(); sword.destroy();
done();
