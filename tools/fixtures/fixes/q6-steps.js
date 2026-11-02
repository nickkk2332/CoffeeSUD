// Q6: named steps and jumps: STEP label, STEP GOTO label, MPSTEPQUEST quest =label, QVAR(quest STEPNAME),
// MODIFY QUEST STEP label, the step's name in saves, and QUESTCHECK's word on them.
FIXFILE = "q6-steps";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function run(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(M, L, 0); }
["FIXQ6A", "FIXQ6B"].forEach(clearVar);
var script = ["set name fixq6", "set duration 2000", "set room any",
	"step confront", "set duration 2000",
	"step spared", "set duration 2000", "step goto epilogue",
	"step slain", "set duration 2000", "step goto epilogue",
	"step epilogue", "set duration 2000",
	"step break", ""].join("\n");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(script, true);
CMLib.quests().addQuest(Q);
Q.startQuest();
function step() { return s(Q.getStat("STEPNUM")); }
function stepName() { return s(Q.getStat("STEPNAME")); }
check("start", Q.running() && step() == "1" && stepName() == "", "step " + step() + " '" + stepName() + "'");
check("labels", s(Q.getStat("STEPOF:confront")) == "2" && s(Q.getStat("STEPOF:spared")) == "3" && s(Q.getStat("STEPOF:slain")) == "4"
	&& s(Q.getStat("STEPOF:epilogue")) == "5" && s(Q.getStat("STEPOF:nosuch")) == "-1", "epilogue is " + Q.getStat("STEPOF:epilogue"));
Q.stepQuest();
check("next", step() == "2" && stepName() == "confront", "step " + step() + " '" + stepName() + "'");

// the choice: a jump by name, and QVAR(quest STEPNAME)
var chief = scriptedMob("a fixq6 chief", "RAND_PROG 100\nmpstepquest fixq6 =spared\nif qvar(fixq6 STEPNAME == spared)\n\tmpsetvar FIXTEST FIXQ6A yes\nendif\nmpsetvar FIXTEST FIXQ6B $%QVAR(fixq6 STEPNAME)%\n~\n");
tickMob(chief);
check("jump", step() == "3" && stepName() == "spared", "step " + step() + " '" + stepName() + "'");
check("qvar", gvar("FIXQ6A") == "yes" && gvar("FIXQ6B") == "spared", "FIXQ6A '" + gvar("FIXQ6A") + "', FIXQ6B '" + gvar("FIXQ6B") + "'");
check("inspection", has(Q.inspection(), "Step: 3 (spared)"), Q.inspection());
// the branch ends: STEP GOTO goes past the other branch
Q.stepQuest();
check("goto", Q.running() && step() == "5" && stepName() == "epilogue", "step " + step() + " '" + stepName() + "'");

// MODIFY QUEST STEP by name, and the other branch's GOTO
run("Modify", "MODIFY QUEST fixq6 STEP slain");
check("modify-step", step() == "4" && stepName() == "slain", "step " + step() + " '" + stepName() + "'");
Q.stepQuest();
check("goto-2", step() == "5", "step " + step());

// the save keeps the name, which wins over the number when the file has changed
var WS = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState");
var xml = s(callTyped(null, WS, "questsXML", ["MOB"], [M]));
var at = xml.indexOf("<NAME>fixq6</NAME>");
var mine = (at < 0) ? "" : xml.substring(xml.lastIndexOf("<QUEST", at), xml.indexOf("</QUEST>", at) + 8);
check("saved", has(mine, "<STEP>5</STEP>") && has(mine, "<STEPNAME>epilogue</STEPNAME>"), mine);
run("Modify", "MODIFY QUEST fixq6 STEP 2");
var edited = mine.replace("<STEP>5</STEP>", "<STEP>3</STEP>");
callTyped(null, WS, "setQuests", ["XMLTag"], [CMLib.xml().parseAllXML("<QUESTS>" + edited + "</QUESTS>").get(0)]);
check("restored-by-name", step() == "5" && stepName() == "epilogue", "step " + step() + " '" + stepName() + "'");

// QUESTCHECK: a GOTO to no step, and no word against the newer SETs
var QC = CM.core.QuestCheck;
var probs = s(QC.checkText("fixq6bad", "set name fixq6bad\nset duration 10\nset restart never\nset repeatable 1 mudday\nset maxwins 3\nset objective a 1 Do it\nset journal Begun.\nstep goto nowhere\nstep later\nstep goto later\n"));
check("questcheck", has(probs, "STEP GOTO nowhere: no step of that name") && !has(probs, "GOTO later") && !has(probs, "unknown"), probs);

if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
chief.destroy();
done();
