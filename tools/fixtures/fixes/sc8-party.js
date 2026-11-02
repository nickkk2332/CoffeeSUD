// SC8: $u is the character at the terminal; ISPLAYED(who); INPARTY(who), the
// player's party; MPECHOAT to a party member in the background goes to the
// terminal, named; so do the prompts.
FIXFILE = "sc8-party";
var R = M.location();
var Party = CM.core.Party;
["FIXSC8A", "FIXSC8B", "FIXSC8C", "FIXSC8D", "FIXSC8E"].forEach(clearVar);
function runCmd(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(M, L, 0); }
runCmd("TestCharCmd", "TESTCHAR Fixsceight PARTY");
var H = Party.findInWorld(M, "Fixsceight");
check("member", H != null && ("" + Party.isBackground(H)) == "true", "Fixsceight is " + (H == null ? "not in the world" : "not in the background"));
if (H != null) {
	if (H.location() != R) R.bringMobHere(H, false);
	var F = newMob("GenMob", R); F.setName("Fixsc8ferry");
	var S = scriptOn(F, "FUNCTION_PROG greet\n"
		+ "mpsetvar FIXTEST FIXSC8A $u\n"
		+ "if isplayed($u)\n mpsetvar FIXTEST FIXSC8B yes\nendif\n"
		+ "if isplayed(Fixsceight)\n mpsetvar FIXTEST FIXSC8C yes\nelse\n mpsetvar FIXTEST FIXSC8C no\nendif\n"
		+ "if inparty(Fixsceight)\n mpsetvar FIXTEST FIXSC8D yes\nendif\n"
		+ "if inparty(Fixsc8ferry)\n mpsetvar FIXTEST FIXSC8E wrong\nendif\n"
		+ "mpechoat Fixsceight Fixsc8 five coins a head.\n"
		+ "~\n");
	var SN = CMClass.getCommon("FakeSession");
	SN.initialize(null, "MEMORY");
	M.session().setBeingSnoopedBy(SN, true);
	S.callFunction(F, "greet", "", H);
	M.session().setBeingSnoopedBy(SN, false);
	var seen = "" + field(SN, "bout").toString();
	check("terminal", gvar("FIXSC8A") == M.name() && gvar("FIXSC8B") == "yes", "FIXSC8A='" + gvar("FIXSC8A") + "', FIXSC8B='" + gvar("FIXSC8B") + "'");
	check("played", gvar("FIXSC8C") == "no", "FIXSC8C='" + gvar("FIXSC8C") + "'");
	check("inparty", gvar("FIXSC8D") == "yes" && gvar("FIXSC8E") == "", "FIXSC8D='" + gvar("FIXSC8D") + "', FIXSC8E='" + gvar("FIXSC8E") + "'");
	check("echoat", seen.indexOf("[Fixsceight] Fixsc8 five coins a head.") >= 0, "the terminal saw: " + seen);
	var ps = callTyped(S, null, "promptSession", ["MOB"], [H]);
	check("prompt", ps == M.session() && ("" + callTyped(S, null, "promptFor", ["MOB", "String"], [H, "Pay?"])) == "[Fixsceight] Pay?", "the prompt goes to " + ps);
	F.destroy();
	Party.rest(H);
}
done();
