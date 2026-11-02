// Q14: Prop_QuestBoard: COUNT jobs generated through RandomQuests, listed on LOOK, taken with ACCEPT n,
// refreshed on the game clock; the generator told the reader's PLAYER_LEVEL, PARTY_SIZE, QUESTS_DONE and VARS.
FIXFILE = "q14-board";
var R = M.location();
var QB = CM.core.QuestBoards;
var GT = CM.core.GameTime;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq14rook");
V.basePhyStats().setLevel(7); V.recoverPhyStats();
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
function npc(name) { var m = newMob("GenMob", R); m.setName(name); m.setDisplayText(name + " is here."); return m; }
var clerk = npc("fixq14 clerk"), carter = npc("fixq14 carter");
var board = CMClass.getItem("GenItem");
board.setName("a fixq14 board"); board.setDisplayText("A board of jobs hangs here.");
R.addItem(board);
var parms = "COUNT=2 REFRESH=\"1 mudday\" FILE=_fixes/q14jobs.xml TAGID=fixq14jobs GIVER=\"fixq14 clerk\" AREA_NAME=\"" + s(R.getArea().Name()) + "\" VARS=\"jobword=carry\" LEVELRANGE=2";
var P = CMClass.getAbility("Prop_QuestBoard");
P.setMiscText(parms);
board.addNonUninvokableEffect(P);
var key = s(QB.key(board));
function look() { var lm = CMClass.getMsg(V, board, null, CMMsg.MSG_LOOK, null); if (R.okMessage(V, lm)) R.send(V, lm); return out(); }
function jobs() { var L = QB.quests(key); var a = []; for (var i = 0; i < L.size(); i++) a.push(s(L.get(i).name())); return a; }

var gp = s(QB.generatorParms(parms, V));
check("parms", has(gp, "PLAYER_LEVEL=\"7\"") && has(gp, "PARTY_SIZE=\"1\"") && has(gp, "QUESTS_DONE=\"0\"") && has(gp, "JOBWORD=\"carry\"")
	&& has(gp, "PATH=\"_fixes/q14jobs.xml\"") && has(gp, "TARGET_MIN_AREALEVEL=5") && has(gp, "TARGET_MAX_AREALEVEL=9") && !has(gp, "COUNT="), gp);

// LOOK: two jobs made, listed with the reader's level and the VARS, shown for QUESTS OFFERED
out();
var said = look();
var first = jobs();
check("posted", first.length == 2, "jobs " + first.join(", "));
check("listed", has(said, "Jobs on a fixq14 board:") && has(said, "1) A level 7 job, 0 done, party of 1") && has(said, "2) ") && has(said, "Fixq14 carry a word to the carter.")
	&& has(said, "Reward: ") && has(said, "ACCEPT [NUMBER]"), said);
check("offered", CM.core.QuestRecords.offeredTo(V).size() >= 2, "offered " + CM.core.QuestRecords.offeredTo(V).size());

// ACCEPT 1
var A = CMClass.getCommand("Accept");
var L = new java.util.ArrayList(); L.add("ACCEPT"); L.add("1");
A.execute(V, L, 0);
said = out();
var taken = CMLib.quests().fetchQuest(QB.chosen(V, "1").name());
check("accept", taken != null && CM.core.QuestMarkers.accepted(V, taken) && has(said, "You are now on the job"), said);

// the same day: the same jobs, the one taken no longer offered to its taker
said = look();
check("same-day", jobs().join() == first.join() && has(said, "1) ") && !has(said, "2) "), jobs().join() + " / " + said);

// the job done; at the next refresh it goes and a new one comes, with the win counted
var qp = V.getQuestPoint();
R.send(V, CMClass.getMsg(V, R, null, CMMsg.MSG_ENTER, null)); // the job (story_talk1): speak with the carter
tickMob(carter);
said = out();
check("job-won", taken.wasWinner(V.Name()) && !CM.core.QuestMarkers.accepted(V, taken) && V.getQuestPoint() == qp + 2 && has(said, "Fixq14 thank you."),
	"winner " + taken.wasWinner(V.Name()) + ", on it " + CM.core.QuestMarkers.accepted(V, taken) + ", qp " + qp + ">" + V.getQuestPoint() + ": " + said);
var day = CMProps.getMillisPerMudHour() * 24;
function age() { var L = QB.quests(key); for (var i = 0; i < L.size(); i++) L.get(i).setQuestVar("POSTED", "" + (GT.now() - 2 * day)); }
age();
said = look();
var second = jobs();
check("refresh-done-goes", second.length == 2 && second.indexOf(s(taken.name())) < 0 && second.indexOf(first[0]) + second.indexOf(first[1]) > -2, first.join() + " > " + second.join());
check("refresh-counts-wins", has(said, "1 done"), said);

// none taken: the oldest is replaced
age();
look();
var third = jobs();
var kept = third.filter(function (n) { return second.indexOf(n) >= 0; });
check("refresh-replaces-oldest", third.length == 2 && kept.length == 1, second.join() + " > " + third.join());

// ACCEPT with no board read falls back to QUESTS ACCEPT
L = new java.util.ArrayList(); L.add("ACCEPT"); L.add("nosuchfixq14");
A.execute(V, L, 0);
check("accept-fallback", has(out(), "No quest offered to you and open to you is called"), "no fallback");

QB.quests(key).forEach(function (Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
if (CMLib.quests().fetchQuest(taken.name()) != null) CMLib.quests().delQuest(taken);
board.destroy(); clerk.destroy(); carter.destroy();
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
