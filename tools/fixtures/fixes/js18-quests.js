// JS18: a quest's JavaScript handlers (a <JSQUEST> block, or SET JSFILE), its
// objects' onQuest* handlers, and the quests helper.
FIXFILE = "js18-quests";
var R = M.location();
var W = WorldFiles.worldDir();
function s(x) { return "" + x; }
["FIXJS18O", "FIXJS18W", "FIXJS18F", "FIXJS18T"].forEach(clearVar);
// a catalog mob that hears its quest
var crier = CMClass.getMOB("GenMob");
crier.setName("a fixjs18 crier");
var J = CMClass.getBehavior("JScriptable");
J.setParms("function onQuestStep(quest, step, label) { cm.var('FIXTEST', 'FIXJS18O', '' + quest.name() + ':' + step) }\n"
	+ "function onQuestWin(quest, mob, outcome) { cm.var('FIXTEST', 'FIXJS18W', '' + mob.Name() + ':' + outcome) }\n");
crier.addBehavior(J);
CMLib.catalog().addCatalog(crier);
crier.destroy();

// no semicolons: a quest's lines may be split at them
var js = ["function onStart(state) {", "state.started = (state.started || 0) + 1", "state.objective = '' + quests.objective(quest.name())", "}",
	"function onStep(n, state) {", "state.steps = (state.steps || '') + n", "}",
	"function onWin(player) {", "state.winner = '' + player.Name()", "}",
	"function onStop() {", "state.stops = (state.stops || 0) + 1", "}"].join("\n");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript("set name fixjs18q\nset duration 1000\nset room any\nset instructions Find the fixes cat.\n<JSQUEST>\n" + js + "\n</JSQUEST>\n"
	+ "step\nset duration 1000\nset room any\nload mob catalog a fixjs18 crier\nstep break\n", true);
CMLib.quests().addQuest(Q);
check("block", s(Q.jsQuest).indexOf("function onStart") >= 0, "jsQuest '" + Q.jsQuest + "'");
var st = cm.store("quest:fixjs18q");
Q.startQuest();
check("onStart", Q.running() && st.started == 1 && s(st.objective) == "Find the fixes cat.", "running " + Q.running() + ", " + JSON.stringify(st));
Q.stepQuest();
check("onStep", s(st.steps) == "2", "steps '" + st.steps + "'");
check("onQuestStep", gvar("FIXJS18O") == "fixjs18q:2", "FIXJS18O='" + gvar("FIXJS18O") + "'");
Q.declareWinner(M.Name());
check("onWin", s(st.winner) == s(M.Name()), "winner '" + st.winner + "'");
check("onQuestWin", gvar("FIXJS18W") == M.Name() + ":WON", "FIXJS18W='" + gvar("FIXJS18W") + "'");
// the helper
var H = new CM.core.JSQuests.Helper();
check("helper", H.running("fixjs18q") && s(H.status("fixjs18q")) == "running" && H.stepNumber("fixjs18q") == 2 && s(H.objective("fixjs18q")) == "Find the fixes cat.",
	"running " + H.running("fixjs18q") + ", " + H.status("fixjs18q") + ", step " + H.stepNumber("fixjs18q"));
H.setVar("fixjs18q", "cats", 3);
check("helper-vars", s(H.getVar("fixjs18q", "cats")) == "3" && s(H.status("nosuchfixjs18")) == "none", "cats '" + H.getVar("fixjs18q", "cats") + "'");
Q.stepQuest(); // past the last step: it ends
check("onStop", !Q.running() && st.stops == 1, "running " + Q.running() + ", stops " + st.stops);
CMLib.quests().delQuest(Q);

// SET JSFILE, and onTimeUp
var F = new java.io.File(W, "js/quests/fixjs18f.js");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String("function onStart(state) { cm.var('FIXTEST', 'FIXJS18F', 'file') }\nfunction onTimeUp(state) { cm.var('FIXTEST', 'FIXJS18T', 'late') }\n").getBytes("UTF-8"));
var Q2 = CMClass.getCommon("DefaultQuest");
Q2.setScript("set name fixjs18f\nset duration 1000\nset room any\nset jsfile world:js/quests/fixjs18f.js\nstep break\n", true);
CMLib.quests().addQuest(Q2);
Q2.startQuest();
check("jsfile", s(Q2.jsFile) == "world:js/quests/fixjs18f.js" && gvar("FIXJS18F") == "file", "jsFile '" + Q2.jsFile + "', FIXJS18F='" + gvar("FIXJS18F") + "'");
var f = null;
for (var c = Q2.getClass(); c != null && f == null; c = c.getSuperclass()) { try { f = c.getDeclaredField("ticksRemaining"); } catch (e) { } }
f.setAccessible(true);
f.setInt(Q2, 0);
Q2.tick(Q2, Tickable.TICKID_QUEST);
check("onTimeUp", !Q2.running() && gvar("FIXJS18T") == "late", "running " + Q2.running() + ", FIXJS18T='" + gvar("FIXJS18T") + "'");
CMLib.quests().delQuest(Q2);
F["delete"]();
CM.core.JSStore.remove("quest:fixjs18q");
done();
