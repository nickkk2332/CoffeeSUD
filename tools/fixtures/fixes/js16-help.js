// JS16: JS HELP <expr>: the methods and stats of what an expression gives.
FIXFILE = "js16-help";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixjs16builder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var o = all.substring(mark); mark = all.length; return o; }
function js(words) { var L = new java.util.ArrayList(); L.add("JS"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("JSCmd").execute(B, L, 0); return out(); }
var said = js("HELP cm.room('" + R.roomID() + "')");
check("room", has(said, "getArea()") && has(said, "numInhabitants()") && has(said, "fetchInhabitant(") && has(said, "Stats:") && has(said, "DISPLAY: "), said.substring(0, 300));
said = js("HELP cm");
check("cm", has(said, "JSCm: ") && has(said, "findMob(1|2)") && has(said, "store(|1)"), said.substring(0, 300));
said = js("HELP cm.lib");
check("class", has(said, "CMLib (a class): ") && has(said, "map()"), said.substring(0, 200));
cm.store("fixjs16").a = 1;
cm.store("fixjs16").f = function() {};
said = js("HELP cm.store('fixjs16')");
check("js-object", has(said, "a JavaScript object: a, f()"), said);
check("usage", has(js("HELP"), "JS HELP [EXPRESSION]"), "no expression");
check("error", has(js("HELP nosuchfixjs16.x"), "Error: "), "a bad expression");
CM.core.JSStore.remove("fixjs16");
B.destroy();
done();
