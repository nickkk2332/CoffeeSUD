// DT32: QUESTCHECK reads a quest's script without starting it, each problem with its line.
FIXFILE = "dt32-questcheck";
var QC = CM.core.QuestCheck;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixquestchecker");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function qc(words) { var L = new java.util.ArrayList(); L.add("QUESTCHECK"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("QuestCheckCmd").execute(B, L, 0); return out(); }

var bad = "" + new CM.core.CMFile("/resources/_fixes/dt32bad.quest", M).text();
var probs = "" + QC.checkText("dt32bad.quest", bad);
check("set-var", has(probs, "dt32bad.quest line 2: SET DURATON unknown"), probs);
check("command", has(probs, "dt32bad.quest line 3: FROBNICATE is no quest command"), probs);
check("load-missing", has(probs, "dt32bad.quest line 4: LOAD=_fixes/nosuchfix.script not found"), probs);
check("own-file", has(probs, "dt32bad.quest (dt32own.script): ") && has(probs, "MPNOSUCHFIXCMD"), probs);
check("import-missing", has(probs, "dt32bad.quest line 6: IMPORT file _fixes/nosuch_items.xml not found"), probs);
check("give-kind", has(probs, "dt32bad.quest line 8: GIVE FLURB unknown"), probs);
check("no-duration", has(probs, "dt32bad.quest: no SET DURATION") && !has(probs, "no SET NAME"), probs);
check("wait", has(probs, "SET WAIT with no SET INTERVAL"), probs);
// a quest that is right has none
var good = "" + new CM.core.CMFile("/resources/_fixes/dt2.quest", M).text();
var none = QC.checkText("dt2.quest", good);
check("good", none.size() == 0, "dt2.quest: " + none);
// the command: a file in the resources, a loaded quest, ALL
var said = qc("_fixes/dt32bad.quest");
check("cmd-file", has(said, "QUESTCHECK _fixes/dt32bad.quest: ") && has(said, "FROBNICATE is no quest command"), said);
check("cmd-none", has(qc("nosuchfixquest"), "There is no quest or quest file 'nosuchfixquest'."), "an unknown quest");
said = qc("ALL");
check("cmd-all", has(said, "QUESTCHECK world/quests: "), said);
java.lang.System.out.println("FIXINFO dt32 " + ("" + said).split("\n")[0]);
var lines = ("" + said).split("\n");
for (var i = 1; i < lines.length && i < 30; i++) java.lang.System.out.println("FIXINFO dt32 " + lines[i]);

B.setSession(null);
B.destroy();
done();
