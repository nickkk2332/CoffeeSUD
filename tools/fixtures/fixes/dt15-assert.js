// DT15: ASSERT answers HERE, HAS, VAR and QUEST for playtest files, "ASSERT ok" or "ASSERT FAIL".
FIXFILE = "dt15-assert";
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixasserter");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function run(cmdName, words, who) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(who || M, L, 0); }
function assert(words) { run("AssertCmd", "ASSERT " + words, B); return out(); }

var here = "" + CMLib.map().getExtendedRoomID(R);
check("here", has(assert("HERE " + here), "ASSERT ok: HERE " + here), "HERE failed");
check("here-fail", has(assert("HERE Nowhere#99"), "ASSERT FAIL: HERE Nowhere#99: the character is in " + here), "HERE did not fail");
var gem = CMClass.getItem("GenItem");
gem.setName("a fixes gem");
B.addItem(gem);
check("has", has(assert("HAS gem"), "ASSERT ok: HAS gem"), "HAS failed");
check("has-fail", has(assert("HAS unicorn"), "ASSERT FAIL: HAS unicorn"), "HAS did not fail");
VARS.setVar("FIXTEST", "FIXASSERT", "5");
check("var", has(assert("VAR FIXTEST FIXASSERT = 5"), "ASSERT ok: VAR FIXTEST FIXASSERT = 5"), "VAR = failed");
check("var-num", has(assert("VAR FIXTEST FIXASSERT >= 10"), "ASSERT FAIL: VAR FIXTEST FIXASSERT >= 10: it is '5'"), "VAR >= did not fail");
check("var-op", has(assert("VAR FIXTEST FIXASSERT ~ 5"), "is no comparison"), "a bad comparison");
check("mark", has(assert("MARK 7"), "PLAYSTEP 7"), "MARK said nothing");

// a quest's step
new CM.core.CMFile("/resources/_fixes/dt15.quest", M).saveText("set name dt15quest\nset duration 1000\nstep break\nset duration 1000\nstep break\n");
run("Create", "CREATE QUEST LOAD=_fixes/dt15.quest");
var Q = CMLib.quests().fetchQuest("dt15quest");
check("quest-made", Q != null, "no quest dt15quest");
if (Q != null) {
	if (!Q.running()) Q.startQuest();
	check("quest", has(assert("QUEST dt15quest STEP 1"), "ASSERT ok: QUEST dt15quest STEP 1"), "QUEST STEP 1 failed: " + Q.stepNumber());
	run("Modify", "MODIFY QUEST dt15quest STEP 2");
	check("quest-step", has(assert("QUEST dt15quest STEP 2"), "ASSERT ok: QUEST dt15quest STEP 2") && has(assert("QUEST dt15quest STEP 1"), "ASSERT FAIL: QUEST dt15quest STEP 1: it is at step 2"), "after STEP 2");
	Q.stopQuest();
	CMLib.quests().delQuest(Q);
}
check("quest-none", has(assert("QUEST nosuchquest STEP 1"), "there is no such quest"), "an unknown quest");
clearVar("FIXASSERT");
B.setSession(null);
B.destroy();
done();
