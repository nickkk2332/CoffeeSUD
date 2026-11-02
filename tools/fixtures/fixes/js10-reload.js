// JS10: JS RELOAD puts changed world/js files into the running world; JS WATCH.
FIXFILE = "js10-reload";
var JR = CM.core.JSReload;
var R = M.location();
var W = WorldFiles.worldDir();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var later = 0;
function touch(f) { later += 10000; f.setLastModified(java.lang.System.currentTimeMillis() + later); }
function report(which) { return s(JR.reload(which)); }
// what changed before this test is not this test's
JR.reload(null);

// a Prop_JScript file: read again by each property running it, with onReload(old)
var PF = new java.io.File(W, "js/props/fixjs10.js");
write(PF, "var version = 1;\nfunction tick(host, id) { parms.V = version; }\n");
var thing = CMClass.getItem("GenItem");
thing.setName("a fixjs10 thing");
R.addItem(thing);
var P = CMClass.getAbility("Prop_JScript");
P.setMiscText("SCRIPT=world:js/props/fixjs10.js");
thing.addNonUninvokableEffect(P);
P.tick(thing, 0);
check("before", has(P.text(), "V=1"), "text " + P.text());
write(PF, "var version = 2;\nfunction tick(host, id) { parms.V = version; }\nfunction onReload(old) { parms.WAS = old.version; }\n");
touch(PF);
var said = report(null);
check("changed", has(said, "world/js/props/fixjs10.js: read again by 1 property or behaviour"), said);
P.tick(thing, 0);
check("reloaded", has(P.text(), "V=2") && has(P.text(), "WAS=1"), "text " + P.text());
check("nothing", has(report(null), "No JavaScript file in world/js has changed."), "a second reload finds nothing");
check("named", has(report("props/fixjs10.js"), "read again by 1"), "by name");
check("no-file", has(report("props/fixjs10nothere.js"), "There is no file world/js/props/fixjs10nothere.js."), "a missing file");

// a file that does not compile is not reloaded, and says where
var BF = new java.io.File(W, "js/props/fixjs10bad.js");
write(BF, "var ok = 1;\nfunction ( {\n");
touch(BF);
said = report(null);
check("compile-error", has(said, "world/js/props/fixjs10bad.js:2:") && has(said, "(not reloaded)"), said);

// a library: require() reads it again
var LF = new java.io.File(W, "js/lib/fixjs10lib.js");
write(LF, "exports.v = 1;\n");
CM.core.JSScope.reload();
var v1 = require("fixjs10lib").v;
write(LF, "exports.v = 2;\n");
touch(LF);
said = report(null);
// a scope keeps what it required: a new one gets the new file
clearVar("FIXJS10L");
var lm = scriptedMob("a fixjs10 reader", "RAND_PROG 100\n<SCRIPT>\nsetVar('FIXTEST', 'FIXJS10L', '' + require('fixjs10lib').v);\n</SCRIPT>\nmpdisable\n~\n");
tickMob(lm);
runQueued(lm.scripts().nextElement());
check("library", v1 == 1 && has(said, "world/js/lib/fixjs10lib.js: a library") && gvar("FIXJS10L") == "2", said + " / FIXJS10L=" + gvar("FIXJS10L"));
lm.destroy();

// world/js/init.js: read again, with onReload(old)
var IF = CM.core.WorldJS.file();
if (!IF.isFile()) {
	clearVar("FIXJS10I");
	write(IF, "var gen = 1;\n");
	CM.core.WorldJS.reset();
	CM.core.WorldJS.has("onBoot");
	write(IF, "var gen = 2;\nfunction onReload(old) { cm.var('FIXTEST', 'FIXJS10I', 'was ' + old.gen + ', now ' + gen); }\n");
	touch(IF);
	said = report("init.js");
	check("init", has(said, "world/js/init.js read again, and its onReload called.") && gvar("FIXJS10I") == "was 1, now 2", said + " / " + gvar("FIXJS10I"));
	IF["delete"]();
	CM.core.WorldJS.reset();
}

// a class: the behaviours of it in the world are replaced, their parameters kept
var CF = new java.io.File(W, "js/behaviors/FixJs10Beh.js");
write(CF, "//extends com.planet_ink.coffee_mud.Behaviors.StdBehavior\nfunction ID() { return 'FixJs10Beh'; }\nfunction accountForYourself() { return 'one'; }\n");
check("class-load", CMClass.loadClass(CMClass.CMObjectType.BEHAVIOR, "world/js/behaviors/FixJs10Beh.js", false), "it did not load");
var holder = newMob("GenMob", R);
holder.setName("a fixjs10 holder");
var B = CMClass.getBehavior("FixJs10Beh");
B.setParms("KEEP=me");
holder.addBehavior(B);
check("class-before", s(holder.fetchBehavior("FixJs10Beh").accountForYourself()) == "one", "account " + holder.fetchBehavior("FixJs10Beh").accountForYourself());
write(CF, "//extends com.planet_ink.coffee_mud.Behaviors.StdBehavior\nfunction ID() { return 'FixJs10Beh'; }\nfunction accountForYourself() { return 'two'; }\n");
touch(CF);
said = report("behaviors/FixJs10Beh.js");
var NB = holder.fetchBehavior("FixJs10Beh");
check("class-replaced", has(said, "class FixJs10Beh compiled again; 1 in the world replaced.") && NB != null && s(NB.accountForYourself()) == "two" && s(NB.getParms()) == "KEEP=me",
	said + " / " + (NB && NB.accountForYourself()));

// JS RELOAD and JS WATCH as commands
var BM = newMob("GenMob", R);
BM.setName("Fixjs10builder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
BM.setSession(BS);
BS.setMob(BM);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var o = all.substring(mark); mark = all.length; return o; }
function js(words) { var L = new java.util.ArrayList(); L.add("JS"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("JSCmd").execute(BM, L, 0); return out(); }
check("cmd-reload", has(js("RELOAD"), "No JavaScript file in world/js has changed."), "JS RELOAD");
said = js("WATCH ON 2");
check("cmd-watch-on", has(said, "JS WATCH is on: every 2 seconds") && JR.isWatching(), said);
said = js("WATCH OFF");
check("cmd-watch-off", has(said, "JS WATCH is off.") && !JR.isWatching(), said);

PF["delete"](); BF["delete"](); LF["delete"](); CF["delete"]();
thing.destroy(); holder.destroy(); BM.destroy();
done();
