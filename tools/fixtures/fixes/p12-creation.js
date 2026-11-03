// P12: world/creation.ini: the order of steps (questions between the engine's), RANDOM and SKIP, the world's own questions and their answers' gifts.
FIXFILE = "p12-creation";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var CSt = CM.core.CreationSteps;
var CS = CM.Common.interfaces.CharStats;
var CF = CSt.file();
var had = CF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(CF.toPath()), "UTF-8")) : null;
var R0 = M.location(), R = scratchRoom();
var F = CMLib.factions().factions().nextElement(), fid = s(F.factionID());
var heroes = [];
function hero(name, race) {
	var h = newMob("GenMob", R); h.setName(name);
	h.baseCharStats().setMyRace(CMClass.getRace(race)); h.recoverCharStats();
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	heroes.push(h);
	return h;
}
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY");
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var CC = CMLib.login(), LSI = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function lget(name) { var f = LSI.getDeclaredField(name); f.setAccessible(true); return f.get(lo); }
function step(name) {
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name && ms[i].getParameterCount() == 2) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
function states() { return s(lget("state")); }
try {
	if (CF.isFile()) CF["delete"]();
	check("default", s(CSt.after("CLASS")) == "[ORIGIN, KIT]" && s(CSt.mode("RACE")) == "ASK" && s(CSt.mode("DEITY")) == "ASK", s(CSt.after("CLASS")));
	write(CF, ["# fixp12",
		"STEPS=RACE,GENDER,FIXUPBRINGING,STATS,CLASS,KIT,FACTIONS,REVIEW,FIXLAST",
		"GENDER=RANDOM",
		"[FIXUPBRINGING]",
		"PROMPT=Where were you raised?",
		"SHOWIF=-RACE +Human",
		'CHOICE=NAME="Noble" HELP="Born in the manor." EFFECTS="1 CHARISMA" FACTION="' + fid + ' 100" START="' + CMLib.map().getExtendedRoomID(R0)
			+ '" SCRIPT="ONCE_PROG 100;mpechoat $i Fixp12 noble home;~"',
		'CHOICE=NAME="Street urchin" HELP="The docks raised you." MASK="-RACE +Dwarf"',
		'CHOICE=NAME="Farmhand" HELP="Fields and cows."',
		"[FIXLAST]",
		"PROMPT=Last one?",
		"MODE=RANDOM",
		'CHOICE=NAME="Yes"',
		'CHOICE=NAME="No"',
		""].join("\n"));
	check("plan", s(CSt.after("START")) == "[]" && s(CSt.after("GENDER")) == "[FIXUPBRINGING]" && s(CSt.after("CLASS")) == "[KIT]"
		&& s(CSt.after("FACTIONS")) == "[FIXLAST]" && s(CSt.after("DEITY")) == "[]", s(CSt.after("GENDER")) + s(CSt.after("FACTIONS")));
	check("modes", s(CSt.mode("GENDER")) == "RANDOM" && s(CSt.mode("ORIGIN")) == "SKIP" && s(CSt.mode("DEITY")) == "SKIP" && s(CSt.mode("RACE")) == "ASK"
		&& CSt.auto("GENDER") && !CSt.auto("STATS") && s(CSt.mode("FIXLAST")) == "RANDOM", s(CSt.mode("DEITY")));
	// a human: gender at random, then the question
	var H = hero("Fixp12hero", "Human"); H.setSession(VS); VS.setMob(H); lset("mob", H);
	var r0 = step("charcrGenderStart");
	check("gender random", r0 == null && states() == "CHARCR_GENDERDONE", states());
	step("charcrGenderDone");
	check("question next", states() == "CHARCR_QUESTIONSTART" && s(lget("question")) == "FIXUPBRINGING", states());
	said();
	var r1 = step("charcrQuestionStart"), o1 = said();
	check("menu", s(r1) == "INPUT_REQUIRED" && states() == "CHARCR_QUESTIONPICKED" && has(o1, "Where were you raised?") && has(o1, "Noble") && has(o1, "Born in the manor.")
		&& has(o1, "Farmhand") && !has(o1, "urchin"), o1.substring(0, 300));
	lset("lastInput", "pirate"); step("charcrQuestionPicked"); var o2 = said();
	check("not one", states() == "CHARCR_QUESTIONSTART" && has(o2, "is not one of the answers"), o2);
	lset("lastInput", "nob"); step("charcrQuestionPicked");
	check("answered", H.findTattoo("CHARCR_FIXUPBRINGING_NOBLE") != null && states() == "CHARCR_STATINIT", states());
	// a dwarf is not asked (SHOWIF)
	var D = hero("Fixp12dwarf", "Dwarf"); lset("mob", D); lset("question", "FIXUPBRINGING"); lget("queue").clear(); lset("resume", null);
	var r3 = step("charcrQuestionStart");
	check("not shown", r3 == null && states() == "CHARCR_FACTIONNEXT" && D.findTattoo("CHARCR_FIXUPBRINGING_FARMHAND") == null, states());
	// a question at random
	lset("question", "FIXLAST");
	step("charcrQuestionStart");
	check("random", D.findTattoo("CHARCR_FIXLAST_YES") != null || D.findTattoo("CHARCR_FIXLAST_NO") != null, "no answer");
	// the answer's gifts
	var cha = H.baseCharStats().getStat(CS.STAT_CHARISMA), f0 = H.fetchFaction(fid); if (f0 == java.lang.Integer.MAX_VALUE) f0 = 0;
	said();
	var n = CSt.applyAnswers(H), o4 = said();
	H.recoverCharStats();
	check("gifts", n == 1 && H.baseCharStats().getStat(CS.STAT_CHARISMA) == cha + 1 && H.fetchFaction(fid) == f0 + 100 && H.getStartRoom() == R0,
		n + " " + H.baseCharStats().getStat(CS.STAT_CHARISMA) + "/" + cha + " " + H.fetchFaction(fid));
	check("script", has(o4, "Fixp12 noble home"), o4);
	check("help", has(CMLib.help().getHelpText("CREATIONSTEPS", M, true), "creation.ini"), "no help");
} finally {
	if (had != null) write(CF, had); else if (CF.isFile()) CF["delete"]();
	heroes.forEach(function (h) { h.setSession(null); h.setPlayerStats(null); h.destroy(); });
}
done();
