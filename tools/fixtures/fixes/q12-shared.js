// Q12: SET SHARED PARTY (accepting, winning, failing go to the party members) and SET SHARED PROFILE (a win, the
// status, the wins and the journal answer for every character of the profile); NONE stays each character's own.
FIXFILE = "q12-shared";
var R = M.location();
var QR = CM.core.QuestRecords;
var QJ = CM.core.QuestJournal;
var QS = CM.core.QuestSharing;
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var acct = CMClass.getCommon("DefaultPlayerAccount");
acct.setAccountName("Fixq12profile");
var names = new java.util.ArrayList(); names.add("Fixq12mira"); names.add("Fixq12tobin"); names.add("Fixq12una");
acct.setPlayerNames(names);
var sessions = {};
function character(name, room) {
	var V = newMob("GenMob", room);
	V.setName(name);
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	sessions[name] = VS;
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(acct);
	CMLib.players().addPlayer(V);
	return V;
}
var V = character("Fixq12mira", R);
var T = character("Fixq12tobin", R);
var U = character("Fixq12una", otherRoom());
T.setFollowing(V);
U.setFollowing(V);
var g = CMClass.getMOB("GenMob"); g.setName("a fixq12 warden"); CMLib.catalog().addCatalog(g); g.destroy();
function quest(name, extra) {
	var Q = CMClass.getCommon("DefaultQuest");
	Q.setScript(["set name " + name, "set duration 2000"].concat(extra || []).concat(["set room any", "load mob catalog a fixq12 warden",
		"give script LOAD=" + name + "giver.script",
		"<?xml version=\"1.0\"?>",
		"<FILE><NAME>" + name + "giver.script</NAME><DATA>",
		"FUNCTION_PROG CAN_ACCEPT", "\tif !QUESTSCRIPTED($n *)", "\t\tRETURN TRUE", "\tendif", "\tRETURN CANCEL", "~",
		"FUNCTION_PROG DO_ACCEPT", "\tmpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=" + name + "track.script", "~",
		"</DATA></FILE>",
		"<FILE><NAME>" + name + "track.script</NAME><DATA>", "ONCE_PROG 100\n\tmpsetvar $i FIXQ12 on\n~", "</DATA></FILE>", ""]).join("\n"), true);
	CMLib.quests().addQuest(Q);
	Q.startQuest();
	return Q;
}
function carries(mob, Q) { return CM.core.QuestMarkers.accepted(mob, Q); }
function run(text) { var m = scriptedMob("a fixq12 clerk", "RAND_PROG 100\n" + text + "\n~\n"); tickMob(m); m.destroy(); }
check("profile", s(QS.profileNames("Fixq12tobin")) == "[Fixq12tobin, Fixq12mira, Fixq12una]", s(QS.profileNames("Fixq12tobin")));

// PARTY: accepting gives it to the party members here, not to one elsewhere
var P = quest("fixq12p", ["set shared party", "set rewards \"3 QP\""]);
check("set", s(P.getStat("SHARED")) == "PARTY", s(P.getStat("SHARED")));
P.acceptQuest(V);
check("party-accept", carries(V, P) && carries(T, P) && !carries(U, P), "mira " + carries(V, P) + ", tobin " + carries(T, P) + ", una " + carries(U, P));
// a win with REWARD: both win and are paid
var qv = V.getQuestPoint(), qt = T.getQuestPoint(), qu = U.getQuestPoint();
run("mpquestwin Fixq12mira fixq12p OUTCOME bounty REWARD");
check("party-win", P.wasWinner("Fixq12mira") && P.wasWinner("Fixq12tobin") && !P.wasWinner("Fixq12una")
	&& s(QR.outcome("fixq12p", "Fixq12tobin")) == "bounty", "tobin " + P.wasWinner("Fixq12tobin") + ", una " + P.wasWinner("Fixq12una"));
check("party-paid", V.getQuestPoint() == qv + 3 && T.getQuestPoint() == qt + 3 && U.getQuestPoint() == qu,
	"mira " + qv + ">" + V.getQuestPoint() + ", tobin " + qt + ">" + T.getQuestPoint());
// a failure goes to them all
var F = quest("fixq12f", ["set shared party"]);
F.acceptQuest(V);
run("mpquestfail Fixq12mira fixq12f the wolves got away");
check("party-fail", s(QR.status("fixq12f", V, V.Name())) == "FAILED" && s(QR.status("fixq12f", T, T.Name())) == "FAILED"
	&& !carries(T, F) && s(QR.status("fixq12f", U, U.Name())) == "", "tobin " + QR.status("fixq12f", T, T.Name()) + ", una '" + QR.status("fixq12f", U, U.Name()) + "'");

// NONE: each character's own
var N = quest("fixq12n");
N.acceptQuest(V);
check("none-accept", carries(V, N) && !carries(T, N), "tobin " + carries(T, N));
run("mpquestwin Fixq12mira fixq12n");
check("none-win", N.wasWinner("Fixq12mira") && !N.wasWinner("Fixq12tobin") && s(QR.status("fixq12n", T, T.Name())) == "", s(QR.status("fixq12n", T, T.Name())));

// PROFILE: Mira's win is Tobin's too, for QUESTWINNER, QUESTSTATUS, QUESTWINS, the masks and REPEATABLE NO
var B = quest("fixq12b", ["set shared profile", "set repeatable no"]);
function unfollow(X) { X["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](null); }
unfollow(T); unfollow(U); // no party here
B.acceptQuest(V);
check("profile-accept", carries(V, B) && !carries(T, B), "tobin " + carries(T, B));
check("profile-active", s(QR.status("fixq12b", T, T.Name())) == "ACTIVE", s(QR.status("fixq12b", T, T.Name())));
QJ.journal(V, "fixq12b", "Mira heard the bell under the water.");
run("mpquestwin Fixq12mira fixq12b OUTCOME saved");
check("profile-winner", B.wasWinner("Fixq12tobin") && B.wasWinner("Fixq12una"), "tobin " + B.wasWinner("Fixq12tobin"));
check("profile-status", s(QR.status("fixq12b", T, T.Name())) == "WON" && s(QR.outcome("fixq12b", "Fixq12tobin")) == "saved"
	&& QR.wins("fixq12b", "Fixq12tobin") == 1, s(QR.status("fixq12b", T, T.Name())) + "/" + QR.outcome("fixq12b", "Fixq12tobin") + "/" + QR.wins("fixq12b", "Fixq12tobin"));
check("profile-mask", CMLib.masking().maskCheck("-QUESTWIN +fixq12b", T, true) && !CMLib.masking().maskCheck("+QUESTWIN -fixq12b", T, true), "mask");
clearVar("FIXQ12W");
run("if questwinner(Fixq12tobin fixq12b)\n\tmpsetvar FIXTEST FIXQ12W yes\nendif");
check("profile-questwinner", gvar("FIXQ12W") == "yes", gvar("FIXQ12W"));
check("profile-repeat", !B.canAcceptQuest(T), "tobin may take it again");

// PROFILE: the journal is read by all of them; QUESTS name for one who does not carry it
var L2 = quest("fixq12l", ["set shared profile"]);
L2.acceptQuest(V);
QJ.journal(V, "fixq12l", "Mira found the warden's ledger.");
var hist = s(QJ.entries(T, "fixq12l", 10));
check("profile-log", has(hist, "(Fixq12mira) Mira found the warden's ledger."), hist);
var TS = sessions["Fixq12tobin"]; var mark = s(field(TS, "bout").toString()).length;
var Lc = new java.util.ArrayList(); Lc.add("QUESTS"); Lc.add("fixq12l");
CMClass.getCommand("Questwins").execute(T, Lc, 0);
var said = s(field(TS, "bout").toString()).substring(mark).replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " ");
check("profile-quests", has(said, "carried by Fixq12mira") && has(said, "Mira found the warden's ledger."), said);

[P, F, N, B, L2].forEach(function (Q) { if (Q.running()) Q.stopQuest(); CMLib.quests().delQuest(Q); });
[V, T, U].forEach(function (X) { CMLib.players().delPlayer(X); X.setPlayerStats(null); X.destroy(); });
done();
