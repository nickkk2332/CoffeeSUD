// P10: world rules for the profile and the party: PROFILEMAX, NEWCHARS (FIRSTONLY, CHEAT, MASK), PARTYMAX, PARTYROLES, CHEAT ON/OFF in the save.
FIXFILE = "p10-rules";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var WR = CM.core.WorldRules, WS = CM.core.save.WorldSettings;
var P = CM.core.CMProps.instance();
var KEYS = ["PROFILEMAX", "NEWCHARS", "NEWCHARMASK", "NEWCHARMESSAGE", "PARTYMAX", "PARTYROLES"];
var was = {}; KEYS.forEach(function (k) { was[k] = P.getProperty(k); });
function setp(k, v) { if (v == null) P.remove(k); else P.setProperty(k, v); }
var R = scratchRoom(), made = [];
function npc(name, race, cls) {
	var m = newMob("GenMob", R); m.setName(name);
	m.baseCharStats().setMyRace(CMClass.getRace(race)); m.baseCharStats().setCurrentClass(CMClass.getCharClass(cls)); m.recoverCharStats();
	made.push(m); return m;
}
var cheatWas = WR.cheatsOn();
try {
	var empty = CMClass.getCommon("DefaultPlayerAccount"), acct = CMClass.getCommon("DefaultPlayerAccount");
	var human = npc("Fixp10lead", "Human", "Fighter"), dwarf = npc("Fixp10dwarf", "Dwarf", "Thief");
	acct.addNewPlayer(human);
	check("always", WR.newCharRefusal(acct, human) == null && s(WR.newChars()) == "ALWAYS", s(WR.newCharRefusal(acct, human)));
	setp("NEWCHARS", "FIRSTONLY");
	check("first only", WR.newCharRefusal(empty, null) == null && has(WR.newCharRefusal(acct, human), "one character"), s(WR.newCharRefusal(acct, human)));
	setp("NEWCHARMESSAGE", "Fixp10 seek the guild.");
	check("message", s(WR.newCharRefusal(acct, human)) == "Fixp10 seek the guild.", s(WR.newCharRefusal(acct, human)));
	setp("NEWCHARMESSAGE", null);
	setp("NEWCHARS", "CHEAT");
	WR.setCheats(false);
	var c1 = WR.newCharRefusal(acct, human);
	var CH = CMClass.getCommand("CheatCmd");
	CH.execute(M, list("CHEAT", "ON"), 0);
	check("cheat", has(c1, "CHEAT ON") && WR.cheatsOn() && WR.newCharRefusal(acct, human) == null, s(c1));
	CH.execute(M, list("CHEAT", "OFF"), 0);
	check("cheat off", !WR.cheatsOn(), "still on");
	setp("NEWCHARS", "MASK"); setp("NEWCHARMASK", "-RACE +Dwarf");
	check("mask", has(WR.newCharRefusal(acct, human), "New characters can be made only when") && WR.newCharRefusal(acct, dwarf) == null, s(WR.newCharRefusal(acct, human)));
	setp("NEWCHARS", null); setp("NEWCHARMASK", null);
	setp("PROFILEMAX", "1");
	check("profile max", has(WR.newCharRefusal(acct, human), "only have 1 character") && WR.newCharRefusal(empty, null) == null, s(WR.newCharRefusal(acct, human)));
	setp("PROFILEMAX", null);
	setp("PARTYMAX", "2");
	check("party max", has(WR.partyRefusal(acct, 2, 2, true), "at most 2 characters") && WR.partyRefusal(acct, 1, 1, true) == null && WR.partyMax() == 2, s(WR.partyRefusal(acct, 2, 2, true)));
	setp("PARTYMAX", "1");
	check("lone wolf", has(WR.partyRefusal(acct, 1, 1, false), "at most 1 character in it"), s(WR.partyRefusal(acct, 1, 1, false)));
	setp("PARTYROLES", "healer:-BASECLASS +Cleric +Druid; scout:-BASECLASS +Thief");
	check("roles", s(WR.missingRoles(list(human, dwarf))) == "[healer]" && s(WR.roleWarning(list(human))) == "Your party has no healer or scout."
		&& s(WR.roleWarning(list(human, dwarf))) == "Your party has no healer.", s(WR.roleWarning(list(human))));
	check("world keys", WS.isWorldKey("PARTYMAX") && WS.isWorldKey("NEWCHARS") && WS.isWorldKey("SHOWLOCKED") && WS.isWorldKey("CREATIONTEMPLATES"), "not world keys");
	// a save made with cheats on says so
	var SL = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.SaveSlots$Slot");
	var sc = SL.getDeclaredConstructor(java.lang.String, java.util.Properties); sc.setAccessible(true);
	var hdr = new java.util.Properties(); hdr.setProperty("character", "Fixp10lead"); hdr.setProperty("cheat", "true");
	check("saves", has(CM.core.save.SaveSlots.describe(sc.newInstance("fixp10", hdr)), "[cheat]"), s(CM.core.save.SaveSlots.describe(sc.newInstance("fixp10", hdr))));
	check("help", has(CMLib.help().getHelpText("WORLDRULES", M, true), "PARTYMAX") && has(CMLib.help().getHelpText("CHEAT", M, false), "CHEAT ON"), "no help");
} finally {
	KEYS.forEach(function (k) { setp(k, was[k]); });
	WR.setCheats(cheatWas);
	made.forEach(function (m) { m.destroy(); });
}
done();
