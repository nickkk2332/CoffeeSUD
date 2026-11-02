// I17: the Riddler behaviour: RIDDLE/ANSWERS (and RIDDLE1..9), RANDOM, FUZZY, ASK=GREET,LOOK,COMMAND, TRIES, ONRIGHT,
// ONWRONG, ONOUT, ONCE=CHARACTER|SAVE|NONE; SAY or ANSWER.
FIXFILE = "i17-riddler";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi17Desert");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi17Desert#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var DUNES = room(0, "Fixi17 dunes"), GATE = room(1, "Fixi17 gate");
DUNES.rawDoors()[Dir.NORTH] = GATE; DUNES.setRawExit(Dir.NORTH, CMClass.getExit("Open"));
GATE.rawDoors()[Dir.SOUTH] = DUNES; GATE.setRawExit(Dir.SOUTH, CMClass.getExit("Open"));
function player(name) {
	var p = CMClass.getMOB("GenMob"); p.setName(name); p.bringToLife(DUNES, true); p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var se = CMClass.getCommon("FakeSession"); se.initialize(null, "MEMORY"); p.setSession(se); se.setMob(p);
	return { m: p, se: se, mark: 0 };
}
function out(w) { var all = s(field(w.se, "bout").toString()); var o = all.substring(w.mark); w.mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function say(w, text) { out(w); CMLib.commands().postSay(w.m, null, text, false, false); return out(w); }
function cmd(w, line) { out(w); var l = new java.util.ArrayList(); line.split(" ").forEach(function (x) { l.add(x); }); var took = SE.verb(w.m, l); return { took: took, said: out(w) }; }
var sphinx = CMClass.getMOB("GenMob"); sphinx.setName("the fixi17 sphinx"); sphinx.bringToLife(GATE, true);
var B = CMClass.getBehavior("Riddler");
B.setParms("RIDDLE=\"What has roots nobody sees, and is taller than trees?\" ANSWERS=\"mountain|mountains\" FUZZY=1 TRIES=2 "
	+ "ONRIGHT=\"setvar FIXTEST FIXI17R $n\" ONWRONG=\"setvar FIXTEST FIXI17W $n\" ONOUT=\"setvar FIXTEST FIXI17O $n\" RIGHTMSG=\"The sphinx steps aside.\"");
sphinx.addBehavior(B);
["FIXI17R", "FIXI17W", "FIXI17O"].forEach(clearVar);
var A = player("Fixi17ann"), Bo = player("Fixi17bo");
out(A);
CMLib.tracking().walk(A.m, Dir.NORTH, false, false);
var said = out(A);
check("greet", has(said, "taller than trees"), "'" + said + "'");
said = say(A, "A Mountian!");
check("right", gvar("FIXI17R") == "Fixi17ann" && has(said, "The sphinx steps aside.") && A.m.findTattoo("RIDDLED:THE_FIXI17_SPHINX") != null, "'" + gvar("FIXI17R") + "' '" + said + "'");
// once each: no more for her
var again = cmd(A, "riddle sphinx");
check("once", again.took && has(again.said, "no more riddles"), "'" + again.said + "'");
// two wrong answers: out
CMLib.tracking().walk(Bo.m, Dir.NORTH, false, false);
var r1 = cmd(Bo, "answer a river");
var w1 = gvar("FIXI17W");
var r2 = cmd(Bo, "answer the sea");
check("wrong", r1.took && has(r1.said, "That is not right.") && w1 == "Fixi17bo" && gvar("FIXI17O") == "Fixi17bo", "'" + w1 + "' out '" + gvar("FIXI17O") + "' '" + r1.said + "'");
var r3 = cmd(Bo, "answer mountain");
check("after", (!r3.took) && gvar("FIXI17R") == "Fixi17ann", "an answer after the tries was taken");
// a stone that asks once a game, when looked at
var stone = CMClass.getItem("GenItem"); stone.setName("a fixi17 talking stone"); GATE.addItem(stone);
var SB = CMClass.getBehavior("Riddler");
SB.setParms("RIDDLE1=\"What walks on four legs at dawn?\" ANSWERS1=man RIDDLE2=\"What is always coming but never arrives?\" ANSWERS2=tomorrow ASK=LOOK ONCE=SAVE");
stone.addBehavior(SB);
out(Bo); GATE.send(Bo.m, CMClass.getMsg(Bo.m, stone, null, CMMsg.MSG_LOOK, null)); said = out(Bo);
check("look", has(said, "four legs at dawn"), "'" + said + "'");
var ok1 = cmd(Bo, "answer man");
check("save", ok1.took && has(SB.getParms(), "SOLVED=true"), "parms " + SB.getParms());
check("norm", s(CM.Behaviors.Riddler.norm("The Mountain!")) == "mountain", s(CM.Behaviors.Riddler.norm("The Mountain!")));
check("help", has(CMLib.help().getHelpText("BEHAVIOR_RIDDLER", M, true), "FUZZY"), "no help");

[A, Bo].forEach(function (w) { w.m.setPlayerStats(null); w.m.destroy(); });
sphinx.destroy();
CMLib.map().obliterateMapArea(X);
done();
