// DT2: QUESTINFO shows a quest on one screen; MODIFY QUEST tests it.
FIXFILE = "dt2-questinfo";
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixquester");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
function run(cmdName, words, who) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(who || M, L, 0); }

run("Create", "CREATE QUEST LOAD=_fixes/dt2.quest");
var Q = CMLib.quests().fetchQuest("dt2quest");
check("made", Q != null, "no quest dt2quest");
if (Q != null) {
	if (!Q.running()) Q.startQuest();
	run("QuestInfo", "QUESTINFO dt2quest", B);
	var info = out();
	check("info", has(info, "Quest 'dt2quest': running") && has(info, "Step: 1") && has(info, "Designated: MOB a herald in ")
		&& has(info, "Holds: mobs a herald") && has(info, "DefaultScriptingEngine on a herald"), "QUESTINFO said " + info);

	run("Modify", "MODIFY QUEST dt2quest VAR FIXQV hello there");
	check("var", ("" + Q.getQuestVars().get("FIXQV")) == "hello there", "vars " + Q.getQuestVars());
	run("Modify", "MODIFY QUEST dt2quest WIN Fixwinner");
	var won = Q.wasWinner("Fixwinner");
	run("Modify", "MODIFY QUEST dt2quest UNWIN fixwinner");
	check("win-unwin", won && !Q.wasWinner("Fixwinner"), "won " + won + ", after UNWIN " + Q.wasWinner("Fixwinner"));
	run("Modify", "MODIFY QUEST dt2quest WIN Fixwinner");
	run("Modify", "MODIFY QUEST dt2quest RESETWINNERS");
	check("resetwinners", Q.getWinners().size() == 0, "winners " + Q.getWinners());

	run("Modify", "MODIFY QUEST dt2quest STEP 2");
	check("step", ("" + Q.getStat("STEPNUM")) == "2", "the step is " + Q.getStat("STEPNUM"));

	// RELOAD reads the quest's file and its scripts again, and restarts at the step it was at
	new CM.core.CMFile("/resources/_fixes/dt2.quest", M).saveText(("" + new CM.core.CMFile("/resources/_fixes/dt2.quest", M).text()).replace(/set duration 1000/g, "set duration 2000"));
	new CM.core.CMFile("/resources/_fixes/dt2herald.script", M).saveText("RAND_PROG 100\nmpsetvar FIXTEST FIXDT2 second\n~\n");
	run("Modify", "MODIFY QUEST dt2quest RELOAD");
	check("reload", Q.running() && Q.duration() == 2000 && ("" + Q.getStat("STEPNUM")) == "2" && CMLib.quests().fetchQuest("dt2quest") == Q,
		"running " + Q.running() + ", duration " + Q.duration() + ", step " + Q.getStat("STEPNUM") + ", listed " + (CMLib.quests().fetchQuest("dt2quest") == Q));
	// the step's herald has the script as the file says now (a quest's
	// scripts keep their variables in the quest's scope)
	var questScope = CMClass.getCommon("DefaultScriptingEngine");
	questScope.setVarScope("DT2QUEST");
	questScope.setVar("FIXTEST", "FIXDT2", "");
	var herald = Q.getDesignatedObject("MOB");
	if (herald != null) tickMob(herald);
	check("reload-script", ("" + questScope.getVar("FIXTEST", "FIXDT2")) == "second", "the herald's script set '" + questScope.getVar("FIXTEST", "FIXDT2") + "'");
	check("reload-vars", ("" + Q.getQuestVars().get("FIXQV")) == "hello there", "vars after RELOAD " + Q.getQuestVars());
	Q.stopQuest();
	CMLib.quests().delQuest(Q);
}
B.setSession(null);
B.destroy();
done();
