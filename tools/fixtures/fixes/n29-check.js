// N29: CHECK(who STAT|skill difficulty [modifier]): d20 + (stat-10)/2 or proficiency/10 + modifier against the
// difficulty; CHECKMARGIN; what the character is told (CHECKROLLS).
FIXFILE = "n29-check";
var CS = CM.Common.interfaces.CharStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = M.location();
var V = newMob("GenMob", R);
V.setName("Fixn29pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
V.baseCharStats().setStat(CS.STAT_CHARISMA, 18); V.recoverCharStats();
var haggle = CMClass.getAbility("Skill_Haggle"); haggle.setProficiency(100); V.addAbility(haggle);
var was = CMProps.instance().getProperty("CHECKROLLS");
function run(cond) {
	["FIXN29R", "FIXN29M"].forEach(clearVar);
	var m = scriptedMob("a fixn29 toll keeper", "RAND_PROG 100\nif " + cond + "\nmpsetvar FIXTEST FIXN29R yes\nelse\nmpsetvar FIXTEST FIXN29R no\nendif\n"
		+ "mpsetvar FIXTEST FIXN29M $<Fixn29pip CHECKMARGIN>\nmpdisable\n~\n");
	tickMob(m); m.destroy();
	return gvar("FIXN29R");
}
CMProps.instance().setProperty("CHECKROLLS", "");
out();
check("easy", run("CHECK(Fixn29pip CHA 5)") == "yes", "a CHA 18 check against 5 failed");
var margin = CMath.s_int(gvar("FIXN29M"));
check("margin", margin >= 0 && margin <= 19 && gvar("FIXN29M").length > 0, "margin '" + gvar("FIXN29M") + "'");
check("told", has(out(), "[Charisma check: success]"), "the brief line");
check("hard", run("CHECK(Fixn29pip CHA 25)") == "no", "beat 25 with at most 24");
check("modifier", run("CHECK(Fixn29pip CHA 11 +6)") == "yes", "1+4+6 is 11");
check("skill-hard", run("CHECK(Fixn29pip Skill_Haggle 31)") == "no", "beat 31 with at most 30");
check("skill-easy", run("CHECK(Fixn29pip Skill_Haggle 11)") == "yes", "1+10 is 11");
CMProps.instance().setProperty("CHECKROLLS", "FULL");
out();
run("CHECK(Fixn29pip CHA 5 2)");
var full = out();
check("full", has(full, "[Charisma check: success (d20 ") && has(full, "+4, +2, against 5)]"), full);
CMProps.instance().setProperty("CHECKROLLS", "OFF");
out();
run("CHECK(Fixn29pip CHA 5)");
check("off", !has(out(), "check:"), "told with OFF");
CMProps.instance().setProperty("CHECKROLLS", was == null ? "" : was);
V.destroy();
done();
