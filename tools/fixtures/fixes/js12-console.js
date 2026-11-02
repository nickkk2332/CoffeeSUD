// JS12: JCONSOLE @target, its dot commands, and its history.
FIXFILE = "js12-console";
var R = M.location();
var W = WorldFiles.worldDir();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixjs12builder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var o = all.substring(mark); mark = all.length; return o; }
function jc(line) { var L = new java.util.ArrayList(); L.add("JCONSOLE"); L.add(line); CMClass.getCommand("JConsole").execute(B, L, 0); return out(); }

var smith = newMob("GenMob", R);
smith.setName("a fixjs12smith");
var J = CMClass.getBehavior("JScriptable");
J.setParms("function onSpeech(who, text) { cm.var('FIXTEST', 'FIXJS12S', who.Name() + ': ' + text); }");
smith.addBehavior(J);
scriptOn(smith, "SPEECH_PROG repair\nMPSETVAR FIXTEST FIXJS12P $n\n~\n");
["FIXJS12S", "FIXJS12P"].forEach(clearVar);

// bound to the smith
check("me", has(jc("@fixjs12smith me.Name() + '|' + cm.host().Name() + '|' + here.roomID()"), "a fixjs12smith|a fixjs12smith|" + R.roomID()), "me and cm.host()");
var said = jc("@fixjs12smith MPECHO('hello from $i')");
check("bridge-host", has(said, "hello from a fixjs12smith"), said);
check("unbound", has(jc("me.Name()"), "Fixjs12builder"), "without @, me is the user");
check("no-target", has(jc("@nobodyfixjs12 1"), "There is no 'nobodyfixjs12' to bind the console to"), "a missing target");
check("here", has(jc("@here me.roomID()"), s(R.roomID())), "@here");

// .trigger
said = jc("@fixjs12smith .trigger SPEECH repair my sword");
check("trigger", has(said, "SPEECH fired on a fixjs12smith.") && gvar("FIXJS12S") == "Fixjs12builder: repair my sword" && gvar("FIXJS12P") == "Fixjs12builder",
	said + " / FIXJS12S='" + gvar("FIXJS12S") + "' FIXJS12P='" + gvar("FIXJS12P") + "'");
check("trigger-usage", has(jc("@fixjs12smith .trigger DANCE"), ".trigger GREET|SPEECH"), "an unknown event");

// .inspect
said = jc("@fixjs12smith .inspect me");
check("inspect", has(said, "a fixjs12smith (GenMob) @ " + R.roomID()) && has(said, "LEVEL: "), said);
cm.store(smith).orders = 2;
said = jc("@fixjs12smith .inspect cm.store(me)");
check("inspect-store", has(said, '"orders": 2'), said);

// .load, .reset, .time
var LF = new java.io.File(W, "js/fixjs12load.js");
LF.getParentFile().mkdirs();
java.nio.file.Files.write(LF.toPath(), new java.lang.String("var fixLoaded = 'yes';\n").getBytes("UTF-8"));
jc("@fixjs12smith .load world:js/fixjs12load.js");
check("load", has(jc("@fixjs12smith fixLoaded"), "yes"), "a loaded file's variables stay");
check("reset", has(jc("@fixjs12smith .reset"), "The console is cleared.") && has(jc("@fixjs12smith typeof fixLoaded"), "undefined"), "after .reset");
said = jc("@fixjs12smith .time on");
check("time", has(said, "Each line's time is shown.") && has(jc("@fixjs12smith 40+2"), " ms)"), said);
jc("@fixjs12smith .time off");

// .history and !n, kept in builder mode
said = jc(".history");
check("history", has(said, ": 40+2") && has(said, ": me.Name()"), said);
var n = 0;
var H = CM.core.JSConsole.history(B);
for (var i = 0; i < H.size(); i++) if (s(H.get(i)) == "40+2") n = i + 1;
check("bang", n > 0 && has(jc("!" + n), "42"), "!" + n);
var HF = new java.io.File(new java.io.File(W, ".history"), "jconsole-Fixjs12builder.txt");
check("history-kept", HF.isFile() && has(new java.lang.String(java.nio.file.Files.readAllBytes(HF.toPath()), "UTF-8"), "40+2"), "no " + HF);

LF["delete"](); HF["delete"]();
smith.destroy(); B.destroy();
done();
