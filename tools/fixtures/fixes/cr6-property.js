// CR6: OWNER(), ISOWNER() (the profile's rule of CR1), LANDTITLE(); MPDEED to a character or @PROFILE with a price, MPUNDEED; INROOM($n == MYPROPERTY) asks of $n.
FIXFILE = "cr6-property";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var R = M.location();
function account(name, players) {
	var a = CMClass.getCommon("DefaultPlayerAccount");
	a.setAccountName(name);
	var n = new java.util.ArrayList();
	players.forEach(function (p) { n.add(p); });
	a.setPlayerNames(n);
	return a;
}
var acct = account("Fixcr6profile", ["Fixcr6ann", "Fixcr6bo"]), other = account("Fixcr6other", ["Fixcr6cy"]);
var X = scratchRoom();
X.setRoomID(R.getArea().Name() + "#fixcr6");
R.getArea().addProperRoom(X);
var lot = CMClass.getAbility("Prop_RoomForSale");
lot.setMiscText("500");
X.addNonUninvokableEffect(lot);
var made = [];
function character(name, a) {
	var V = newMob("GenMob", X);
	V.setName(name);
	var VS = CMClass.getCommon("FakeSession");
	VS.initialize(null, "MEMORY");
	V.setSession(VS);
	VS.setMob(V);
	V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	V.playerStats().setAccount(a);
	CMLib.players().addPlayer(V);
	made.push(V);
	return V;
}
var A = character("Fixcr6ann", acct), B = character("Fixcr6bo", acct), C = character("Fixcr6cy", other);
var VARN = ["CR6A", "CR6B", "CR6C", "CR6D", "CR6E", "CR6F", "CR6G", "CR6H", "CR6I"];
function run(text) {
	VARN.forEach(clearVar);
	var m = newMob("GenMob", X);
	m.setName("a fixcr6 reeve");
	scriptOn(m, "RAND_PROG 100\n" + text + "\nmpdisable\n~\n");
	tickMob(m);
	m.destroy();
}
var id = s(X.roomID());
try {
	run("mpdeed '" + id + "' Fixcr6ann 750\nmpsetvar FIXTEST CR6A $%OWNER(HERE)%\nmpsetvar FIXTEST CR6B $%LANDTITLE('" + id + "')%\n"
		+ "if isowner(Fixcr6ann)\n mpsetvar FIXTEST CR6C yes\nendif\nif isowner(Fixcr6bo HERE)\n mpsetvar FIXTEST CR6D yes\nendif\n"
		+ "if isowner(Fixcr6cy)\n mpsetvar FIXTEST CR6E yes\nendif\nif inroom(Fixcr6ann == MYPROPERTY)\n mpsetvar FIXTEST CR6F yes\nendif\n"
		+ "if inroom(Fixcr6cy == MYPROPERTY)\n mpsetvar FIXTEST CR6G yes\nendif\n");
	check("mpdeed", s(lot.getOwnerName()) == "Fixcr6ann" && lot.getPrice() == 750 && gvar("CR6A") == "Fixcr6ann", lot.getOwnerName() + " " + lot.getPrice() + " / " + gvar("CR6A"));
	check("landtitle", gvar("CR6B") == id + " 750", gvar("CR6B"));
	check("isowner", gvar("CR6C") == "yes" && gvar("CR6D") == "yes" && gvar("CR6E") == "", gvar("CR6C") + gvar("CR6D") + "/" + gvar("CR6E"));
	check("myproperty", gvar("CR6F") == "yes" && gvar("CR6G") == "", gvar("CR6F") + "/" + gvar("CR6G"));
	run("mpdeed HERE @PROFILE\nmpsetvar FIXTEST CR6A $%OWNER(HERE)%\nif isowner(Fixcr6bo)\n mpsetvar FIXTEST CR6B yes\nendif\n");
	check("profile", s(lot.getOwnerName()) == "@PROFILE" && gvar("CR6A") == "@PROFILE" && gvar("CR6B") == "yes" && lot.getPrice() == 750, lot.getOwnerName() + " / " + gvar("CR6A") + gvar("CR6B"));
	run("mpundeed HERE\nmpsetvar FIXTEST CR6A [$%OWNER(HERE)%]\nif isowner(Fixcr6ann)\n mpsetvar FIXTEST CR6B yes\nendif\n");
	check("mpundeed", s(lot.getOwnerName()) == "" && gvar("CR6A") == "[]" && gvar("CR6B") == "", "'" + lot.getOwnerName() + "' / " + gvar("CR6A") + gvar("CR6B"));
	var errs = s(CM.core.ScriptErrors.recent(200, null));
	run("mpdeed 'Fixcr6nowhere#9' Fixcr6ann\nmpsetvar FIXTEST CR6A [$%LANDTITLE(Fixcr6nowhere#9)%]\n");
	var errs2 = s(CM.core.ScriptErrors.recent(200, null));
	check("no title", has(errs2.substring(errs.length), "has no title") && gvar("CR6A") == "[]", errs2.substring(errs.length) + " / " + gvar("CR6A"));
	check("help", has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "ISOWNER(") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPDEED"), "no help");
} finally {
	X.delEffect(lot);
	made.forEach(function (V) { CMLib.players().delPlayer(V); V.setPlayerStats(null); V.destroy(); });
	R.getArea().delProperRoom(X);
	X.destroy();
}
done();
