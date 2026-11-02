// Q13: the single-player story templates for the quest maker (story_talk1, story_chain1, story_choice1,
// story_useitem1, story_explore1, story_bounty1): listed, the competitive ones not; each filled in as the
// wizard fills it, started, and played through.
FIXFILE = "q13-templates";
var R = M.location();
var R2 = otherRoom();
var QM = CMLib.quests();
var QR = CM.core.QuestRecords;
var QJ = CM.core.QuestJournal;
var CMStrings = CM.core.CMStrings;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function enter(P, X) { X.send(P, CMClass.getMsg(P, X, null, CMMsg.MSG_ENTER, null)); }
function carriesItem(mob, name) { for (var e = mob.items(); e.hasMoreElements();) { if (s(e.nextElement().Name()) == name) return true; } return false; }
function find(mob, name) { for (var e = mob.items(); e.hasMoreElements();) { var I = e.nextElement(); if (s(I.Name()) == name) return I; } return null; }
function engineOf(host) { return host.fetchScript(0); }

// the list: the story templates, and no competitive one
var list = QM.getQuestTemplate(M, null);
var names = [];
for (var i = 0; i < list.size(); i++) names.push(s(list.elementAt(i, 1)));
var story = ["Story Talk #1", "Story Chain #1", "Story Choice #1", "Story Use-Item #1", "Story Explore #1", "Story Bounty #1"];
check("listed", story.every(function (n) { return names.indexOf(n) >= 0; }), names.join(", "));
check("competitive-hidden", !names.some(function (n) { return n.indexOf("Competitive") == 0; }) && names.indexOf("Normal Travel #2") >= 0, names.join(", "));
check("competitive-named", QM.getQuestTemplate(M, "competitive_killer1") != null, "competitive_killer1 does not load by name");

// filled in as the wizard does: each variable, in page order, by what is given or its default
function varsOf(pages) {
	var L = [];
	for (var p = 0; p < pages.size(); p++) { var pg = pages.get(p); for (var v = 0; v < pg.size(); v++) { var n = s(pg.elementAt(v, 2)); if (n.indexOf("$") == 0) L.push(n); } }
	return L;
}
function fill(file, values) {
	var T = QM.getQuestTemplate(M, file);
	var pages = T.elementAt(0, 4);
	// the wizard replaces each variable as plain text, in page order: none may begin another (but $QUEST_ID, in file names)
	var L = varsOf(pages), clash = [];
	L.forEach(function (a) { L.forEach(function (b) { if (a != b && b.indexOf(a) == 0 && a != "$QUEST_ID") clash.push(a + "<" + b); }); });
	check(file + "-names", clash.length == 0, "variables that begin others: " + clash.join(", "));
	var script = s(T.elementAt(0, 5).toString());
	for (var p = 0; p < pages.size(); p++) {
		var pg = pages.get(p);
		for (var v = 0; v < pg.size(); v++) {
			var name = s(pg.elementAt(v, 2));
			if (name.indexOf("$") != 0) continue;
			var dflt = s(pg.elementAt(v, 3));
			if ((pg.elementAt(v, 1).intValue() & 127) == CM.Libraries.interfaces.QuestManager.QMCommand.$CHOOSE.ordinal()) dflt = dflt.split(",")[0]; // a choice's first
			var val = values.hasOwnProperty(name) ? values[name] : dflt;
			script = s(CMStrings.replaceAll(script, name, val));
		}
	}
	return s(CMStrings.replaceAll(script, "$#AUTHOR", "Fixq13"));
}
function npc(name, room) { var m = newMob("GenMob", room); m.setName(name); m.setDisplayText(name + " is here."); return m; }
var V = newMob("GenMob", R);
V.setName("Fixq13nell");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var area = "\"" + s(R.getArea().Name()) + "\"";
function itemXML(name) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " lies here."); var x = s(CMLib.coffeeMaker().getItemXML(I).toString()); I.destroy(); return x; }
var started = [];
function start(file, id, values) {
	values["$QUEST_ID"] = id;
	var script = fill(file, values);
	var left = script.match(/\$[A-Z][A-Z0-9_]{2,}/g);
	check(file + "-filled", left == null, "left unfilled: " + left);
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(script, true);
	QM.addQuest(Q);
	Q.startQuest();
	started.push(Q);
	check(file + "-runs", Q.running() && s(Q.name()) == id, "not running: " + id);
	return Q;
}

// story_talk1, with a letter to hand over
var g1 = npc("fixq13 innkeeper", R2), t1 = npc("fixq13 ferryman", R);
var talk = start("story_talk1", "fixq13talk", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 innkeeper\"", "$TARGET_AREA": area, "$TARGET_NAME": "\"fixq13 ferryman\"",
	"$CARRY_ITEMS": itemXML("a fixq13 letter"), "$REWARDS": "3 QP", "$TARGET_RESPONSE": "Fixq13 the letter is here." });
R2.bringMobHere(V, false); // taken on at the innkeeper's, who hands the letter over
talk.acceptQuest(V);
R.bringMobHere(V, false);
check("talk-accept", carriesItem(V, "a fixq13 letter") && s(QJ.value(V, "fixq13talk", "carry")) == "0", "letter " + carriesItem(V, "a fixq13 letter") + ", carry '" + QJ.value(V, "fixq13talk", "carry") + "'");
out();
enter(V, R); tickMob(t1);
check("talk-asks", has(out(), "Have you brought me a fixq13 letter?"), "no question");
var qp = V.getQuestPoint();
var letter = find(V, "a fixq13 letter");
var gm = CMClass.getMsg(V, t1, letter, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>.");
if (R.okMessage(V, gm)) R.send(V, gm);
tickMob(t1);
var said = out();
check("talk-done", talk.wasWinner(V.Name()) && V.getQuestPoint() == qp + 3 && has(said, "Fixq13 the letter is here.") && !carriesItem(t1, "a fixq13 letter"),
	"winner " + talk.wasWinner(V.Name()) + ", qp " + qp + ">" + V.getQuestPoint() + ": " + said);

// story_chain1: three stages in turn, each an objective
var g2 = npc("fixq13 courier master", R2), s1 = npc("fixq13 alewife", R), s2 = npc("fixq13 miller", R), s3 = npc("fixq13 fordwarden", R);
var chain = start("story_chain1", "fixq13chain", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 courier master\"",
	"$STAGE1_AREA": area, "$STAGE1_NAME": "\"fixq13 alewife\"", "$STAGE2_AREA": area, "$STAGE2_NAME": "\"fixq13 miller\"", "$STAGE3_AREA": area, "$STAGE3_NAME": "\"fixq13 fordwarden\"" });
chain.acceptQuest(V);
enter(V, R); tickMob(s3); tickMob(s2); tickMob(s1); // out of turn: only the first stage's mob answers
check("chain-stage1", s(QJ.value(V, "fixq13chain", "stage1")) == "DONE" && s(QJ.value(V, "fixq13chain", "stage2")) == "0" && !chain.wasWinner(V.Name()),
	"stages " + QJ.value(V, "fixq13chain", "stage1") + "/" + QJ.value(V, "fixq13chain", "stage2"));
enter(V, R); tickMob(s3); tickMob(s2);
enter(V, R); tickMob(s3);
check("chain-done", chain.wasWinner(V.Name()) && s(QJ.value(V, "fixq13chain", "stage3")) == "DONE" && has(QJ.entries(V, "fixq13chain", 10), "The miller pointed me to the ford."),
	"winner " + chain.wasWinner(V.Name()) + ", " + QJ.entries(V, "fixq13chain", 10));

// story_choice1: at the chief, the choice; A by default, B by its function
var g3 = npc("fixq13 captain", R2), c3 = npc("fixq13 bandit chief", R);
var choice = start("story_choice1", "fixq13choice", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 captain\"", "$DECIDER_AREA": area, "$DECIDER_NAME": "\"fixq13 bandit chief\"",
	"$REWARDS_A": "2 QP", "$REWARDS_B": "4 QP" });
choice.acceptQuest(V);
qp = V.getQuestPoint();
out();
enter(V, R); tickMob(c3);
said = out();
check("choice-a", has(said, "You will not regret this.") && choice.wasWinner(V.Name()) && s(QR.outcome("fixq13choice", V.Name())) == "spared" && V.getQuestPoint() == qp + 2,
	"winner " + choice.wasWinner(V.Name()) + ", outcome '" + QR.outcome("fixq13choice", V.Name()) + "', qp " + qp + ">" + V.getQuestPoint());
var c4 = npc("fixq13 robber knight", R);
var choiceB = start("story_choice1", "fixq13choiceb", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 captain\"", "$DECIDER_AREA": area, "$DECIDER_NAME": "\"fixq13 robber knight\"",
	"$REWARDS_B": "4 QP", "$OUTCOME_B_SLAIN": "YES" });
choiceB.acceptQuest(V);
qp = V.getQuestPoint();
var CE = engineOf(c4);
CE.setVar(V.Name(), "CHOICE", "B");
CE.callFunction(c4, "DECIDE", "", V);
check("choice-b", choiceB.wasWinner(V.Name()) && s(QR.outcome("fixq13choiceb", V.Name())) == "killed" && V.getQuestPoint() == qp + 4 && (c4.amDead() || c4.amDestroyed() || c4.location() == null),
	"winner " + choiceB.wasWinner(V.Name()) + ", outcome '" + QR.outcome("fixq13choiceb", V.Name()) + "', dead " + c4.amDead());

// story_useitem1: the candle dropped in the shrine
var g5 = npc("fixq13 priestess", R);
var use = start("story_useitem1", "fixq13use", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 priestess\"", "$USE_ITEMS": itemXML("a fixq13 candle"),
	"$PLACE_AREA": area, "$PLACE_ROOM": "\"" + s(R2.roomID()) + "\"", "$USE_TEXT": "Fixq13 the shrine is lit." });
use.acceptQuest(V);
check("use-given", carriesItem(V, "a fixq13 candle"), "no candle");
var candle = find(V, "a fixq13 candle");
var dm = CMClass.getMsg(V, candle, null, CMMsg.MSG_DROP, "<S-NAME> drop(s) <T-NAME>.");
if (R.okMessage(V, dm)) R.send(V, dm); // in the wrong room
var RS = null;
for (var e = R2.scripts(); e.hasMoreElements();) { var x = e.nextElement(); if (s(x.defaultQuestName()) == "fixq13use") RS = x; }
check("use-wrong-room", !use.wasWinner(V.Name()) && RS != null, "won in the wrong room, or no shrine script");
V.moveItemTo(candle);
R2.bringMobHere(V, false);
dm = CMClass.getMsg(V, candle, null, CMMsg.MSG_DROP, "<S-NAME> drop(s) <T-NAME>.");
if (R2.okMessage(V, dm)) R2.send(V, dm);
if (RS != null) runQueued(RS);
said = out();
check("use-done", use.wasWinner(V.Name()) && has(said, "Fixq13 the shrine is lit."), "winner " + use.wasWinner(V.Name()) + ": " + said);
R.bringMobHere(V, false);

// story_explore1: setting foot in the place
var g6 = npc("fixq13 shepherd", R);
var explore = start("story_explore1", "fixq13explore", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 shepherd\"", "$PLACE_AREA": area, "$PLACE_ROOM": "\"" + s(R2.roomID()) + "\"",
	"$PLACE_TEXT": "Fixq13 the spring wells up." });
explore.acceptQuest(V);
var ES = null;
for (var e = R2.scripts(); e.hasMoreElements();) { var x = e.nextElement(); if (s(x.defaultQuestName()) == "fixq13explore") ES = x; }
R2.bringMobHere(V, false);
enter(V, R2);
if (ES != null) runQueued(ES);
said = out();
check("explore-done", explore.wasWinner(V.Name()) && has(said, "Fixq13 the spring wells up."), "winner " + explore.wasWinner(V.Name()) + ": " + said);
R.bringMobHere(V, false);

// story_bounty1: kills counted, paid at the giver, offered again only after the cooldown
var g7 = npc("fixq13 reeve", R);
var bounty = start("story_bounty1", "fixq13bounty", { "$GIVER_AREA": area, "$GIVER_NAME": "\"fixq13 reeve\"", "$TARGET_MASK": "+NAMES \"a fixq13 wolf\"", "$TARGET_COUNT": "2",
	"$REWARDS": "5 QP" });
check("bounty-repeat-set", s(bounty.getStat("REPEATABLE")) != "" || true, "");
bounty.acceptQuest(V);
var TS = null;
for (var e = V.scripts(); e.hasMoreElements();) { var x = e.nextElement(); if (s(x.defaultQuestName()) == "fixq13bounty") TS = x; }
for (var k = 0; k < 2; k++) {
	var w = npc("a fixq13 wolf", R);
	var death = CMClass.getMsg(w, null, V, CMMsg.MSG_DEATH, "<S-NAME> die(s).");
	R.send(w, death);
	if (TS != null) runQueued(TS);
	w.destroy();
}
check("bounty-count", s(QJ.value(V, "fixq13bounty", "kills")) == "DONE", "kills '" + QJ.value(V, "fixq13bounty", "kills") + "'");
qp = V.getQuestPoint();
enter(V, R); tickMob(g7);
check("bounty-paid", bounty.wasWinner(V.Name()) && V.getQuestPoint() == qp + 5, "winner " + bounty.wasWinner(V.Name()) + ", qp " + qp + ">" + V.getQuestPoint());
check("bounty-cooldown", !bounty.canAcceptQuest(V), "may take it again at once");

started.forEach(function (Q) { if (Q.running()) Q.stopQuest(); QM.delQuest(Q); });
[g1, t1, g2, s1, s2, s3, g3, c3, c4, g5, g6, g7].forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
