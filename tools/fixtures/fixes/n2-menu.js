// N2: MPMENU who FUNCTION "prompt" key:"label"[IF cond] ... [default]: the choices numbered, those whose condition
// fails hidden; the script goes on at once; the answer (number, key, or Enter for the default) runs the FUNCTION_PROG
// with the key as $0 and the character as $n.
FIXFILE = "n2-menu";
var R = M.location();
var SM = CM.core.ScriptMenus;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function enter(P, X) { X.send(P, CMClass.getMsg(P, X, null, CMMsg.MSG_ENTER, null)); }
var V = newMob("GenMob", R);
V.setName("Fixn2pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var sage = scriptedMob("a fixn2 sage",
	"GREET_PROG 100\n" +
	"MPMENU $n SAGE \"The old sage regards you.\" tower:\"Ask about the tower\" ring:\"Show him the ring\"[IF HAS($n ring)] bye:\"Leave\" bye\n" +
	"mpechoat $n fixn2 the script goes on\n" +
	"~\n" +
	"FUNCTION_PROG SAGE\n" +
	"mpechoat $n fixn2 $n chose $0 of $i\n" +
	"~\n");

// the menu: numbered, the ring hidden while it is not carried, and the script does not wait
out();
enter(V, R); tickMob(sage);
var said = out();
check("shown", has(said, "The old sage regards you.") && has(said, "1. Ask about the tower") && has(said, "2. Leave") && !has(said, "Show him the ring")
	&& has(said, "Choose 1-2 (Enter: Leave):"), said);
check("goes-on", has(said, "fixn2 the script goes on") && SM.waiting(V), said);
// answered by number
SM.answer(V, "1");
said = out();
check("number", has(said, "fixn2 Fixn2pip chose tower of a fixn2 sage") && !SM.waiting(V), said);

// with the ring: shown; answered by key
var ring = CMClass.getItem("GenItem"); ring.setName("a fixn2 ring"); V.addItem(ring);
enter(V, R); tickMob(sage);
said = out();
check("condition", has(said, "2. Show him the ring") && has(said, "3. Leave"), said);
SM.answer(V, "RING");
check("key", has(out(), "chose ring of"), "the key did not answer");

// Enter takes the default, and so does an answer that is none of them
enter(V, R); tickMob(sage);
out();
SM.answer(V, "");
check("default", has(out(), "chose bye of"), "Enter did not take the default");
enter(V, R); tickMob(sage);
out();
SM.answer(V, "dance");
check("other-default", has(out(), "chose bye of"), "an unknown answer did not take the default");

// with no default, a wrong answer asks again
var hermit = scriptedMob("a fixn2 hermit",
	"GREET_PROG 100\n" +
	"MPMENU $n PICK \"Well?\" a:\"Apples\" b:\"Bread\"\n" +
	"~\n" +
	"FUNCTION_PROG PICK\n" +
	"mpechoat $n fixn2 picked $0\n" +
	"~\n");
sage.destroy();
enter(V, R); tickMob(hermit);
out();
SM.answer(V, "9");
said = out();
check("asks-again", has(said, "Choose 1 to 2.") && SM.waiting(V) && !has(said, "picked"), said);
SM.answer(V, "2");
check("then-answers", has(out(), "fixn2 picked b") && !SM.waiting(V), "no answer after asking again");

// an answer with no menu open is nobody's
check("no-menu", !SM.answer(V, "1"), "answered with no menu");
// a missing function is a script error, and no menu opens
var bad = scriptedMob("a fixn2 mute", "GREET_PROG 100\nMPMENU $n NOSUCH \"x\" a:\"A\"\n~\n");
hermit.destroy();
enter(V, R); tickMob(bad);
check("no-function", !SM.waiting(V), "a menu opened for a missing function");

bad.destroy(); V.destroy();
done();
