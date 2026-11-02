// JS11: JScriptable, a mob, item, room, exit or area scripted wholly in JavaScript.
FIXFILE = "js11-jscriptable";
var R = M.location();
var W = WorldFiles.worldDir();
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var FF = new java.io.File(W, "js/npc/fixjs11ferry.js");
var ferryJS = [
	"function onLoad() { state.loads = (state.loads || 0) + 1; }",
	"function onSpeech(who, text) { if (('' + text).toLowerCase().indexOf('ferry') >= 0) state.asked = '' + who.Name(); }",
	"function onGive(who, item) { state.got = '' + item.Name(); }",
	"function onGreet(who) { state.greeted = '' + who.Name(); }",
	"function onTick() { state.ticks = (state.ticks || 0) + 1; }",
	"function canMessage(msg) { return !(msg.targetMinor() == CMMsg.TYP_GET && msg.target() != null && ('' + msg.target().Name()) == 'a fixjs11 oar'); }",
	"function onCommand(who, verb, rest) { if (('' + verb).toLowerCase() == 'fixjs11row') { state.rowed = '' + rest; return true; } return false; }",
	"function onDeath(killer) { state.died = 'by ' + killer.Name(); }",
	"function fare(args, who) { return 'fare ' + args + ' for ' + who.Name(); }"
];
write(FF, ferryJS.join("\n") + "\n");

var ferry = newMob("GenMob", R);
ferry.setName("a fixjs11 ferryman");
var J = CMClass.getBehavior("JScriptable");
J.setParms("LOAD=world:js/npc/fixjs11ferry.js");
ferry.addBehavior(J);
var st = cm.store(ferry);
check("onLoad", st.loads == 1 && s(J.scriptPath()) == "world:js/npc/fixjs11ferry.js", "loads " + st.loads + ", path " + J.scriptPath());
J.executeMsg(ferry, CMClass.getMsg(M, null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'Ferry, please.'"));
check("onSpeech", s(st.asked) == s(M.Name()), "asked " + st.asked);
var coin = CMClass.getItem("GenItem");
coin.setName("a fixjs11 coin");
J.executeMsg(ferry, CMClass.getMsg(M, ferry, coin, CMMsg.MSG_GIVE, null));
check("onGive", s(st.got) == "a fixjs11 coin", "got " + st.got);
J.executeMsg(ferry, CMClass.getMsg(M, R, null, CMMsg.MSG_ENTER, null));
check("onGreet", s(st.greeted) == s(M.Name()), "greeted " + st.greeted);
J.tick(ferry, Tickable.TICKID_MOB);
check("onTick", st.ticks == 1, "ticks " + st.ticks);
var oar = CMClass.getItem("GenItem");
oar.setName("a fixjs11 oar");
check("canMessage", !J.okMessage(ferry, CMClass.getMsg(M, oar, null, CMMsg.MSG_GET, null)) && J.okMessage(ferry, CMClass.getMsg(M, coin, null, CMMsg.MSG_GET, null)),
	"the oar may not be got");
var words = new java.util.ArrayList(); words.add("fixjs11row"); words.add("north");
check("onCommand", SE.verb(M, words) && s(st.rowed) == "north", "rowed " + st.rowed);
var other = new java.util.ArrayList(); other.add("fixjs11sing");
check("onCommand-other", !SE.verb(M, other), "an unhandled command goes on");
J.executeMsg(ferry, CMClass.getMsg(ferry, null, M, CMMsg.MSG_DEATH, null));
check("onDeath", s(st.died) == "by " + M.Name(), "died " + st.died);

// its other functions, for MPCALLFUNC and CALLFUNC
check("callFunction", s(SE.callFunction(ferry, "fare", "5", M)) == "fare 5 for " + M.Name() && SE.callFunction(ferry, "onTick", "", M) == null,
	"fare: " + SE.callFunction(ferry, "fare", "5", M));
clearVar("FIXJS11O");
var own = newMob("GenMob", R);
own.setName("a fixjs11 caller");
var J2 = CMClass.getBehavior("JScriptable");
J2.setParms("function tally(a, who) { cm.var('FIXTEST', 'FIXJS11O', 'own ' + a); return 'yes'; }");
own.addBehavior(J2);
scriptOn(own, "RAND_PROG 100\nMPCALLFUNC tally 7\nIF CALLFUNC(tally 8)\nMPSETVAR FIXTEST FIXJS11Q true\nENDIF\nmpdisable\n~\n");
clearVar("FIXJS11Q");
tickMob(own);
runQueued(own.scripts().nextElement());
check("mpcallfunc", gvar("FIXJS11O") == "own 8" && gvar("FIXJS11Q") == "true", "FIXJS11O='" + gvar("FIXJS11O") + "', FIXJS11Q='" + gvar("FIXJS11Q") + "'");

// inline, and a shared scope
clearVar("FIXJS11I");
var J3 = CMClass.getBehavior("JScriptable");
J3.setParms("function onTick() { cm.var('FIXTEST', 'FIXJS11I', 'inline ' + me.Name() + ' ' + cm.host().Name()); }");
own.addBehavior(J3);
J3.tick(own, Tickable.TICKID_MOB);
check("inline", gvar("FIXJS11I") == "inline a fixjs11 caller a fixjs11 caller", "FIXJS11I='" + gvar("FIXJS11I") + "'");
var CF = new java.io.File(W, "js/npc/fixjs11count.js");
write(CF, "var count = 0;\nfunction onTick() { count++; state.count = count; state.last = '' + me.Name(); }\n");
var g1 = newMob("GenMob", R); g1.setName("a fixjs11 guard");
var g2 = newMob("GenMob", R); g2.setName("a fixjs11 sentry");
var B1 = CMClass.getBehavior("JScriptable"); B1.setParms("LOAD=world:js/npc/fixjs11count.js SCOPE=fixjs11guards"); g1.addBehavior(B1);
var B2 = CMClass.getBehavior("JScriptable"); B2.setParms("LOAD=world:js/npc/fixjs11count.js SCOPE=fixjs11guards"); g2.addBehavior(B2);
B1.tick(g1, Tickable.TICKID_MOB);
B2.tick(g2, Tickable.TICKID_MOB);
var gs = cm.store("scope:fixjs11guards");
check("shared", gs.count == 2 && s(gs.last) == "a fixjs11 sentry" && B1.scope() === B2.scope(), "count " + gs.count + ", last " + gs.last);

// JS RELOAD: read again, with onReload(old)
write(FF, ferryJS.join("\n") + "\nfunction onReload(old) { state.reloaded = typeof old.onSpeech; }\n");
FF.setLastModified(java.lang.System.currentTimeMillis() + 10000);
var said = s(CM.core.JSReload.reload("npc/fixjs11ferry.js"));
check("reload", has(said, "read again by 1") && s(st.reloaded) == "function", said + " / reloaded " + st.reloaded);
write(CF, "var count = 100;\nfunction onTick() { count++; state.count = count; }\nfunction onReload(old) { state.reloads = (state.reloads || 0) + 1; }\n");
CF.setLastModified(java.lang.System.currentTimeMillis() + 20000);
said = s(CM.core.JSReload.reload("npc/fixjs11count.js"));
check("reload-shared", has(said, "read again by 2") && gs.reloads == 1 && B1.scope() === B2.scope(), said + " / reloads " + gs.reloads);

FF["delete"](); CF["delete"]();
ferry.destroy(); own.destroy(); g1.destroy(); g2.destroy(); coin.destroy(); oar.destroy();
done();
