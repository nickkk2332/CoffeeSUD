// P18: creation screens that follow the content: @ClassList@, @RaceList?FORMAT=TABLE@, @DeityList@, @StatList@, @if?Tattoo=@, SHOWLOCKED and UNLOCKHINT.
FIXFILE = "p18-screens";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var TM = CM.core.TextMacros;
var AR = CM.Areas.interfaces.Area;
var P = CM.core.CMProps.instance();
var hadShow = P.getProperty("SHOWLOCKED");
var R = scratchRoom();
var H = newMob("GenMob", R); H.setName("Fixp18hero");
H.baseCharStats().setMyRace(CMClass.getRace("Human")); H.recoverCharStats();
H.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); H.setSession(VS); VS.setMob(H);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\^./g, "").replace(/\s+/g, " "); }
var CC = CMLib.login(), LSI = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function step(name) {
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name && ms[i].getParameterCount() == 2) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
var GR = null, GC = null;
try {
	var cl = s(TM.filter("@ClassList@"));
	check("class list", has(cl, "Fighter") && !has(cl, "StdCharClass") && !has(cl, "@"), cl.substring(0, 200));
	var tbl = s(TM.filter("@RaceList?FORMAT=TABLE@"));
	check("race table", has(tbl, "Human") && tbl.split("\n").length > 2, tbl.substring(0, 200));
	var sl = s(TM.filter("@StatList@"));
	check("stat list", has(sl, "Strength") && has(sl, "Charisma"), sl);
	check("deity list", !has(TM.filter("[@DeityList@]"), "@"), s(TM.filter("[@DeityList@]")));
	// @if?Tattoo=@: the character's, or their profile's
	var IF = "@if?Tattoo=FIXP18T@yes@else@no@endif@";
	check("tattoo no", s(TM.filter(IF, H)) == "no" && s(TM.filter(IF)) == "no", s(TM.filter(IF, H)));
	H.addTattoo("FIXP18T");
	check("tattoo yes", s(TM.filter(IF, H)) == "yes" && s(TM.filter("@if?Tattoo=!FIXP18T@a@else@b@endif@", H)) == "b", s(TM.filter(IF, H)));
	H.delTattoo("FIXP18T");
	var acct = CMClass.getCommon("DefaultPlayerAccount"); H.playerStats().setAccount(acct); acct.addTattoo("FIXP18T");
	check("profile tattoo", s(TM.filter(IF, H)) == "yes", s(TM.filter(IF, H)));
	// a race and a class kept for those who unlock them
	var lockedAvail = "" + (AR.THEME_FANTASY | AR.THEME_SKILLONLYMASK);
	GR = CMClass.getRace("GenRace").copyOf(); GR.setStat("ID", "FixP18Drow"); GR.setStat("NAME", "Fixp18 Drow");
	GR.setStat("AVAIL", lockedAvail); GR.setStat("UNLOCKHINT", "reach the Underdark"); CMClass.addRace(GR);
	GC = CMClass.getCharClass("GenCharClass").copyOf(); GC.setStat("ID", "FixP18Seer"); GC.setStat("NAME", "Fixp18 Seer");
	GC.setStat("PLAYER", lockedAvail); GC.setStat("UNLOCKHINT", "find the oracle"); CMClass.addCharClass(GC);
	check("hidden", !has(TM.filter("@RaceList@", H), "Fixp18 Drow") && !has(TM.filter("@ClassList@", H), "Fixp18 Seer"), s(TM.filter("@RaceList@", H)));
	P.setProperty("SHOWLOCKED", "YES");
	check("shown locked", has(TM.filter("@RaceList@", H), "Fixp18 Drow (locked: reach the Underdark)") && has(TM.filter("@ClassList@", H), "Fixp18 Seer (locked: find the oracle)"),
		s(TM.filter("@RaceList@", H)).substring(0, 300));
	check("saved", has(GR.racialParms(), "<UNLOCKHINT>reach the Underdark</UNLOCKHINT>") && has(GC.classParms(), "<UNLOCKHINT>find the oracle</UNLOCKHINT>") && s(GR.getStat("UNLOCKHINT")) == "reach the Underdark", "no");
	// the creation prompts
	lset("mob", H); lset("theme", java.lang.Integer.valueOf(-1));
	said(); step("charcrRaceStart"); var o1 = said();
	check("race prompt", has(o1, "Fixp18 Drow (locked: reach the Underdark)") && has(o1, "Human"), o1.substring(Math.max(0, o1.length - 300)));
	lset("lastInput", "FixP18Drow"); step("charcrRaceReEntered"); var o2 = said();
	check("typed locked", has(o2, "Fixp18 Drow is locked: reach the Underdark."), o2.substring(0, 200));
	said(); step("charcrClassStart"); var o3 = said();
	check("class prompt", has(o3, "Fixp18 Seer (locked: find the oracle)"), o3.substring(Math.max(0, o3.length - 300)));
	// unlocked by the profile's tattoo
	acct.addTattoo("RACE_FIXP18DROW");
	check("unlocked", has(TM.filter("@RaceList@", H), "Fixp18 Drow") && !has(TM.filter("@RaceList@", H), "Fixp18 Drow (locked"), s(TM.filter("@RaceList@", H)).substring(0, 300));
	check("help", has(CMLib.help().getHelpText("CREATIONSCREENS", M, true), "SHOWLOCKED"), "no help");
} finally {
	if (hadShow == null) P.remove("SHOWLOCKED"); else P.setProperty("SHOWLOCKED", hadShow);
	if (GR != null) CMClass.delRace(GR);
	if (GC != null) CMClass.delCharClass(GC);
	H.setSession(null); H.setPlayerStats(null); H.destroy();
}
done();
