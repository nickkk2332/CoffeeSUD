// P27: creation's REVIEW (the sheet, BACK [step] puts the character back as it was then, DONE) and NEW [name] QUICK.
FIXFILE = "p27-review";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var CSt = CM.core.CreationSteps, CR = CM.core.CreationReview;
var CS = CM.Common.interfaces.CharStats;
var CF = CSt.file();
var had = CF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(CF.toPath()), "UTF-8")) : null;
var R = scratchRoom();
var F = CMLib.factions().factions().nextElement(), fid = s(F.factionID());
var H = newMob("GenMob", R); H.setName("Fixp27hero");
H.baseCharStats().setMyRace(CMClass.getRace("Human"));
H.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
H.baseCharStats().setStat(CS.STAT_STRENGTH, 14); H.recoverCharStats();
H.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); H.setSession(VS); VS.setMob(H);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var CC = CMLib.login(), LSI = null, STATE = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; if (s(c.getSimpleName()) == "LoginState") STATE = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function lget(name) { var f = LSI.getDeclaredField(name); f.setAccessible(true); return f.get(lo); }
function st(name) { return java.lang.Enum.valueOf(STATE, name); }
// one turn of the login, as the session runs it
function sub(input) {
	if (input != null) lset("lastInput", input);
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == "loginSubsystem") { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no loginSubsystem";
}
function state() { return s(lget("state")); }
function items(m) { return m.numItems(); }
try {
	// QUICK
	check("quick args", s(CSt.quickArgs(["NEW", "Bran", "QUICK", "Hale", "<CONFIRMED>"])) == "Hale" && CSt.quickArgs(["NEW", "Bran", "<CONFIRMED>"]) == null
		&& s(CSt.quickArgs(["NEW", "Bran", "quick", "<CONFIRMED>"])) == "", s(CSt.quickArgs(["NEW", "Bran", "QUICK", "Hale", "<CONFIRMED>"])));
	CSt.setQuick(true);
	var qm = s(CSt.mode("RACE")) + s(CSt.mode("CLASS")) + s(CSt.mode("ORIGIN")) + s(CSt.mode("REVIEW"));
	CSt.setQuick(false);
	check("quick modes", qm == "RANDOMRANDOMRANDOMSKIP" && s(CSt.mode("RACE")) == "ASK", qm);
	// a snapshot, and the character put back
	H.addTattoo("FIXP27EARLY"); H.addFaction(fid, 100);
	var book = CMClass.getItem("GenItem"); book.setName("a fixp27 book"); H.addItem(book);
	var n0 = items(H), a0 = H.numAbilities();
	var snap = CR.take(H);
	H.baseCharStats().setCurrentClass(CMClass.getCharClass("Thief")); H.baseCharStats().setStat(CS.STAT_STRENGTH, 9);
	H.addAbility(CMClass.getAbility("Skill_Recall")); H.addItem(CMClass.getItem("GenItem")); H.addTattoo("FIXP27LATE"); H.addFaction(fid, 900);
	CR.restore(H, snap);
	check("restored", s(H.baseCharStats().getCurrentClass().ID()) == "Fighter" && H.baseCharStats().getStat(CS.STAT_STRENGTH) == 14
		&& H.fetchAbility("Skill_Recall") == null && H.numAbilities() == a0 && items(H) == n0 && H.findItem("fixp27 book") != null
		&& H.findTattoo("FIXP27LATE") == null && H.findTattoo("FIXP27EARLY") != null && H.fetchFaction(fid) == 100,
		H.baseCharStats().getCurrentClass().ID() + " " + items(H) + "/" + n0 + " " + H.fetchFaction(fid));
	// REVIEW, with BACK to a question
	write(CF, ["STEPS=RACE,GENDER,STATS,CLASS,FIXQ,FACTIONS,REVIEW",
		"[FIXQ]", "PROMPT=Fixp27 which way?", 'CHOICE=NAME="Left"', 'CHOICE=NAME="Right"', ""].join("\n"));
	lset("mob", H); lset("question", "FIXQ"); lget("queue").clear(); lset("resume", st("CHARCR_STARTFINISH")); lset("state", st("CHARCR_QUESTIONSTART"));
	lget("snapshots").clear(); lset("reviewed", false);
	said();
	var r1 = sub(null), o1 = said();
	check("question", s(r1) == "INPUT_REQUIRED" && state() == "CHARCR_QUESTIONPICKED" && has(o1, "Fixp27 which way?") && lget("snapshots").containsKey("FIXQ"), state());
	sub("left");
	check("answered", state() == "CHARCR_STARTFINISH" && H.findTattoo("CHARCR_FIXQ_LEFT") != null, state());
	var r2 = sub(null), o2 = said().replace(/\^./g, "");
	check("sheet", s(r2) == "INPUT_REQUIRED" && state() == "CHARCR_REVIEWPICKED" && has(o2, "Fixp27hero") && has(o2, "Fighter") && has(o2, "Fixq: Left")
		&& has(o2, "STR 14") && has(o2, "BACK"), o2.substring(0, 300));
	sub("back wand"); var o3 = said();
	check("no such step", state() == "CHARCR_REVIEWPICKED" && has(o3, "You can go back to: FIXQ"), o3);
	H.addTattoo("FIXP27AFTER");
	sub("BACK FIXQ");
	check("back", state() == "CHARCR_QUESTIONSTART" && H.findTattoo("CHARCR_FIXQ_LEFT") == null && H.findTattoo("FIXP27AFTER") == null
		&& !lget("snapshots").containsKey("FIXQ"), state());
	sub(null); sub("right");
	check("again", state() == "CHARCR_STARTFINISH" && H.findTattoo("CHARCR_FIXQ_RIGHT") != null, state());
	sub(null);
	var r3 = sub("done");
	check("done", state() == "CHARCR_FINISH" && lget("reviewed") == true, state());
	// without REVIEW in STEPS there is no review
	write(CF, "STEPS=RACE,GENDER,STATS,CLASS,FACTIONS\n");
	lset("reviewed", false); lset("state", st("CHARCR_STARTFINISH"));
	sub(null);
	check("no review", state() == "CHARCR_FINISH", state());
	check("help", has(CMLib.help().getHelpText("CREATIONSTEPS", M, true), "BACK"), "no help");
} finally {
	CSt.setQuick(false);
	if (had != null) write(CF, had); else if (CF.isFile()) CF["delete"]();
	H.setSession(null); H.setPlayerStats(null); H.destroy();
}
done();
