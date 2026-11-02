// Q10: SET REWARDS, MPQUESTWIN ... REWARD, MPREWARD, and 'Reward:' in QUESTS; CoffeeSUD's N% XP,
// FACTION:id:+n and ITEM:catalog name beside the achievements' award words.
FIXFILE = "q10-rewards";
var R = M.location();
var QRw = CM.core.QuestRewards;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixq10mira");
V.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
V.recoverCharStats();
var VS = CMClass.getCommon("FakeSession");
VS.initialize(null, "MEMORY");
V.setSession(VS);
VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
CMLib.players().addPlayer(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
var charm = CMClass.getItem("GenItem"); charm.setName("a fixq10 charm"); CMLib.catalog().addCatalog(charm); charm.destroy();
var g = CMClass.getMOB("GenMob"); g.setName("a fixq10 priest"); CMLib.catalog().addCatalog(g); g.destroy();
var F = CMLib.factions().getFaction("inclination.ini");
var fid = (F == null) ? "nosuchfaction" : s(F.factionID());
var rewards = "50 XP 2 QP 10% XP ITEM:a_fixq10_charm FACTION:" + fid + ":+200";
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript(["set name fixq10", "set duration 2000", "set rewards \"" + rewards + "\"", "set room any", "load mob catalog a fixq10 priest", "give script LOAD=fixq10giver.script",
	"<?xml version=\"1.0\"?>",
	"<FILE><NAME>fixq10giver.script</NAME><DATA>",
	"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
	"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=fixq10track.script", "~",
	"</DATA></FILE>",
	"<FILE><NAME>fixq10track.script</NAME><DATA>", "ONCE_PROG 100", "\tmpsetvar $i FIXQ10T on", "~", "</DATA></FILE>", ""].join("\n"), true);
CMLib.quests().addQuest(Q);
Q.startQuest();
check("set", s(Q.getStat("REWARDS")) == rewards, "REWARDS '" + Q.getStat("REWARDS") + "'");
var told = s(QRw.describe(Q.getStat("REWARDS")));
check("describe", has(told, "50") && has(told, "10% of a level of experience") && has(told, "a fixq10 charm") && has(told, "+200 with"), told);

// QUESTS name shows it
Q.acceptQuest(V);
out();
var L = new java.util.ArrayList(); L.add("QUESTS"); L.add("fixq10");
CMClass.getCommand("Questwins").execute(V, L, 0);
var said = out();
check("quests-name", has(said, "Reward: " + told.replace(/\s+/g, " ")), said);

// the win with REWARD gives it all, with one message
if (F != null) V.addFaction(fid, 0); // a value to add to
var xp0 = V.getExperience(), qp0 = V.getQuestPoint(), f0 = (F == null) ? 0 : V.fetchFaction(fid);
var priest = scriptedMob("a fixq10 judge", "RAND_PROG 100\nmpquestwin Fixq10mira fixq10 REWARD\n~\n");
tickMob(priest);
said = out();
var gotCharm = false;
for (var e = V.items(); e.hasMoreElements();) { if (s(e.nextElement().Name()) == "a fixq10 charm") gotCharm = true; }
check("won", Q.wasWinner(V.Name()), "not a winner");
check("reward-xp", V.getExperience() > xp0, "experience " + xp0 + " > " + V.getExperience());
check("reward-qp", V.getQuestPoint() == qp0 + 2, "quest points " + qp0 + " > " + V.getQuestPoint());
check("reward-item", gotCharm, "no charm");
check("reward-faction", (F == null) || (V.fetchFaction(fid) == f0 + 200), "faction " + f0 + " > " + ((F == null) ? "" : V.fetchFaction(fid)));
check("reward-message", has(said, "Your reward:") && has(said, "a fixq10 charm"), said);

// MPREWARD: an ad-hoc one
var clerk = scriptedMob("a fixq10 clerk", "RAND_PROG 100\nmpreward Fixq10mira \"5 QP\"\n~\n");
tickMob(clerk);
check("mpreward", V.getQuestPoint() == qp0 + 7, "quest points " + V.getQuestPoint());

if (Q.running()) Q.stopQuest();
CMLib.quests().delQuest(Q);
priest.destroy(); clerk.destroy();
CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy();
done();
