// W1: ISWANTED, WARRANTS, FINESOWED; MPPARDON, MPFINE, MPRELEASE, MPSUPPRESSLAW; +WANTED and -WANTED; cm.law()'s new members.
FIXFILE = "w1-law";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location();
var A = R.getArea();
var arrest = CMClass.getBehavior("Arrest");
A.addBehavior(arrest);
var guard = newMob("GenMob", R);
guard.setName("a cityguard");
guard.addBehavior(CMClass.getBehavior("Mobile"));
var suspect = newMob("GenMob", R);
suspect.setName("Fixw1suspect");
var SS = CMClass.getCommon("FakeSession");
SS.initialize(null, "MEMORY");
suspect.setSession(SS);
SS.setMob(suspect);
var L = arrest.legalInfo(A);
// runs a script body once, from a clerk in the room
function runScript(body) {
	var m = scriptedMob("Fixw1clerk", "RAND_PROG 100\n" + body + "\n~\n");
	tickMob(m);
	m.destroy();
}
function probe() {
	clearVar("W1W"); clearVar("W1A"); clearVar("W1M"); clearVar("W1N"); clearVar("W1F");
	runScript("if iswanted(Fixw1suspect)\n mpsetvar FIXTEST W1W yes\nelse\n mpsetvar FIXTEST W1W no\nendif\n"
		+ "if iswanted(Fixw1suspect assault)\n mpsetvar FIXTEST W1A yes\nelse\n mpsetvar FIXTEST W1A no\nendif\n"
		+ "if iswanted(Fixw1suspect murder)\n mpsetvar FIXTEST W1M yes\nelse\n mpsetvar FIXTEST W1M no\nendif\n"
		+ "mpsetvar FIXTEST W1N $%WARRANTS(Fixw1suspect)%\nmpsetvar FIXTEST W1F $%FINESOWED(Fixw1suspect)%");
	return gvar("W1W") + " " + gvar("W1A") + " " + gvar("W1M") + " " + gvar("W1N") + " " + gvar("W1F");
}
var clean = probe();
arrest.accuse(A, suspect, null, ["ASSAULT"]);
var accused = probe();
check("functions", clean == "no no no 0 0" && accused == "yes yes no 1 0", "clean " + clean + ", accused " + accused);
// masks
var MK = CMLib.masking();
check("mask", MK.maskCheck("+WANTED", suspect, true) && (!MK.maskCheck("-WANTED", suspect, true)) && MK.maskCheck("+WANTED ASSAULT", suspect, true)
	&& (!MK.maskCheck("+WANTED MURDER", suspect, true)) && MK.maskCheck("-WANTED MURDER", suspect, true)
	&& (!MK.maskCheck("+WANTED", guard, true)) && MK.maskCheck("-WANTED", guard, true),
	"+ " + MK.maskCheck("+WANTED", suspect, true) + " - " + MK.maskCheck("-WANTED", suspect, true));
check("mask-desc", has(MK.maskDesc("+WANTED MURDER"), "wanted by the law for MURDER"), MK.maskDesc("+WANTED MURDER"));
// fines
runScript("mpfine Fixw1suspect +50");
var f1 = probe().split(" ")[4];
runScript("mpfine Fixw1suspect -20");
var f2 = probe().split(" ")[4];
runScript("mpfine Fixw1suspect 5");
var f3 = probe().split(" ")[4];
check("fines", f1 == "50" && f2 == "30" && f3 == "5" && arrest.finesOwed(suspect) == 5, f1 + " " + f2 + " " + f3);
// pardons
arrest.accuse(A, suspect, null, ["MURDER"]);
var p2 = probe().split(" "); var two = p2[3]; var probe0 = p2[2];
runScript("mppardon Fixw1suspect murder");
var one = probe();
runScript("mppardon Fixw1suspect ALL");
var none = probe();
var wasMurder = probe0;
check("pardon", two == "2" && wasMurder == "yes" && one.indexOf("yes yes no 1") == 0 && none.indexOf("no no no 0") == 0, two + " / " + one + " / " + none);
// laws put aside, and kept in the law's saved state
runScript("mpsuppresslaw ASSAULT 5 HOURS");
arrest.accuse(A, suspect, null, ["ASSAULT"]);
var held = probe().split(" ")[3];
var state = s(call(arrest, "getRunState", []));
runScript("mpsuppresslaw ASSAULT OFF");
arrest.accuse(A, suspect, null, ["ASSAULT"]);
var after = probe().split(" ")[3];
check("suppress", held == "0" && has(state, "<SUPPRESS C=\"ASSAULTING") && after == "1", "held " + held + " after " + after + " state " + state);
// a prisoner let out
var P = CMClass.getAbility("Prisoner");
P.startTickDown(guard, suspect, 100);
var jailed = suspect.fetchEffect("Prisoner") != null;
runScript("mprelease Fixw1suspect");
check("release", jailed && suspect.fetchEffect("Prisoner") == null, "jailed " + jailed);
// JavaScript
var LV = cm.law(R);
LV.addFine(suspect, 7);
check("js", LV.wanted(suspect) && LV.wanted(suspect, "assault") && (!LV.wanted(suspect, "murder")) && LV.finesOwed(suspect) == 12
	&& LV.suppress("ALL", "2 TICKS") && (!LV.suppress("ALL", "soon")), "fines " + LV.finesOwed(suspect));
LV.suppress("ALL", "OFF");
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MPSUPPRESSLAW [CRIME/ALL]") && has(help, "ISWANTED([CODE] ([CRIME]))") && has(CMLib.masking().maskHelp("\n", "disallow"), "-WANTED"), "no help");

LV.pardon(suspect);
arrest.modifyAssessedFines(0, suspect);
suspect.setSession(null);
A.delBehavior(arrest);
guard.destroy(); suspect.destroy();
done();
