// P8: ready-made characters: catalog PLAYERTEMPLATE mobs offered by NEW (CREATIONTEMPLATES), LOCK=NAME,GENDER, NAME=RANDOM, and what a template gives.
FIXFILE = "p8-templates";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var PT = CM.core.PlayerTemplates;
var CS = CM.Common.interfaces.CharStats;
var P = CM.core.CMProps.instance();
var hadMode = P.getProperty("CREATIONTEMPLATES");
var R = scratchRoom();
var F = CMLib.factions().factions().nextElement(), fid = s(F.factionID());
var made = [];
function tmpl(name, race, gender, cls, level, parms) {
	var T = CMClass.getMOB("GenMob"); T.setName(name);
	T.baseCharStats().setMyRace(CMClass.getRace(race));
	T.baseCharStats().setStat(CS.STAT_GENDER, gender.charCodeAt(0));
	T.baseCharStats().setCurrentClass(CMClass.getCharClass(cls));
	T.baseCharStats().setStat(CS.STAT_STRENGTH, 17);
	T.basePhyStats().setLevel(level); T.recoverPhyStats(); T.recoverCharStats();
	var Pr = CMClass.getAbility("Prop_PlayerTemplate"); Pr.setMiscText(parms); T.addNonUninvokableEffect(Pr);
	CMLib.catalog().addCatalog("PLAYERTEMPLATE", T);
	made.push(T);
	return CMLib.catalog().getCatalogMob(name);
}
var heroes = [];
function hero(name) {
	var h = CMClass.getMOB("StdMOB"); h.setName(name);
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	h.baseCharStats().setCurrentClass(CMClass.getCharClass("Apprentice")); h.baseCharStats().setClassLevel(CMClass.getCharClass("Apprentice"), 1);
	h.basePhyStats().setLevel(1); h.recoverCharStats(); h.recoverPhyStats();
	h.setStartRoom(R); h.setLocation(R);
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
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
function answer(text, stepName) { lset("lastInput", text); return step(stepName); }
try {
	check("none", PT.offered().size() == 0 || !has(s(PT.offered()), "Fixp8"), s(PT.offered()));
	var hale = tmpl("Fixp8hale", "Dwarf", "F", "Fighter", 3, 'PITCH="A tired inspector of the Yard." LOCK=NAME,GENDER');
	var A = CMClass.getAbility("Skill_Recall"); A.setProficiency(60); hale.addAbility(A);
	hale.addExpertise("FIXPEIGHTBONUS"); hale.addFaction(fid, 321); hale.addTattoo("FIXP8HALE");
	var book = CMClass.getItem("GenItem"); book.setName("a fixp8 notebook"); hale.addItem(book);
	CMLib.beanCounter().giveSomeoneMoney(hale, 30);
	hale.setDescription("A weary woman in a grey coat.");
	tmpl("Fixp8wanderer", "Elf", "F", "Fighter", 1, 'PITCH="Nobody knows where from." NAME=RANDOM');
	tmpl(s(M.Name()), "Human", "M", "Fighter", 1, 'LOCK=NAME');
	var H = hero("Fixp8new"); H.setSession(VS); VS.setMob(H); lset("mob", H);
	said();
	var r1 = step("charcrTemplateStart"), o1 = said();
	check("menu", s(r1) == "INPUT_REQUIRED" && s(lget("state")) == "CHARCR_TEMPLATEPICKED" && has(o1, "Fixp8hale") && has(o1, "A tired inspector of the Yard.")
		&& has(o1, "Make your own"), o1.substring(0, 300));
	answer("nobody", "charcrTemplatePicked"); var o2 = said();
	check("not one", s(lget("state")) == "CHARCR_TEMPLATESTART" && has(o2, "is not one of them"), o2);
	answer(s(M.Name()), "charcrTemplatePicked"); var o3 = said();
	check("name taken", s(lget("state")) == "CHARCR_TEMPLATESTART" && has(o3, "cannot be made again") && s(H.Name()) == "Fixp8new", o3);
	answer("0", "charcrTemplatePicked");
	check("own", s(lget("state")) == "CHARCR_RACESTART", s(lget("state")));
	// Inspector Hale: name and gender locked
	answer("fixp8hale", "charcrTemplatePicked"); var o4 = said();
	check("hale", s(H.Name()) == "Fixp8hale" && s(H.baseCharStats().getMyRace().ID()) == "Dwarf" && H.baseCharStats().getStat(CS.STAT_GENDER) == "F".charCodeAt(0)
		&& s(H.baseCharStats().getCurrentClass().ID()) == "Fighter" && H.basePhyStats().level() == 3, s(H.Name()) + " " + H.baseCharStats().getMyRace().ID() + " " + H.basePhyStats().level() + " " + o4.substring(0, 200));
	var parts = { recall: H.fetchAbility("Skill_Recall") != null && H.fetchAbility("Skill_Recall").proficiency() >= 60,
		expertise: s(java.util.Collections.list(H.expertises())) == s(java.util.Collections.list(CMLib.catalog().getCatalogMob("Fixp8hale").expertises())) && has(s(java.util.Collections.list(H.expertises())), "FIXPEIGHT"), faction: H.fetchFaction(fid) == 321,
		tattoo: H.findTattoo("FIXP8HALE") != null, item: H.findItem("fixp8 notebook") != null,
		money: CMLib.beanCounter().getTotalAbsoluteNativeValue(H) == 30, desc: s(H.description()) == "A weary woman in a grey coat.",
		noprop: H.fetchEffect("Prop_PlayerTemplate") == null, str: H.baseCharStats().getStat(CS.STAT_STRENGTH) == 17 };
	var bad = []; for (var k in parts) if (!parts[k]) bad.push(k);
	check("gives", bad.length == 0, bad.join(" "));
	check("finished", s(lget("state")) == "CHARCR_FINISH" && H.getStartRoom() != null, s(lget("state")));
	// a wanderer: a drawn name, the gender asked
	var W = hero("Fixp8new2"); W.setSession(VS); VS.setMob(W); lset("mob", W);
	answer("fixp8wanderer", "charcrTemplatePicked");
	var drawn = s(W.Name());
	check("drawn name", drawn != "Fixp8new2" && drawn.length > 0 && drawn.indexOf(" ") < 0 && s(lget("state")) == "CHARCR_TEMPLATEGENDER", drawn + " " + s(lget("state")));
	answer("x", "charcrTemplateGender");
	check("bad gender", s(lget("state")) == "CHARCR_TEMPLATEGENDER", s(lget("state")));
	answer("m", "charcrTemplateGender");
	check("gender", W.baseCharStats().getStat(CS.STAT_GENDER) == "M".charCodeAt(0) && s(lget("state")) == "CHARCR_FINISH", s(lget("state")));
	// ONLY and NO
	P.setProperty("CREATIONTEMPLATES", "ONLY");
	said(); step("charcrTemplateStart"); var o5 = said();
	answer("0", "charcrTemplatePicked");
	check("only", !has(o5, "Make your own") && s(lget("state")) == "CHARCR_TEMPLATESTART", s(lget("state")));
	P.setProperty("CREATIONTEMPLATES", "NO");
	check("no", PT.offered().size() == 0 && step("charcrTemplateStart") == null && s(lget("state")) == "CHARCR_RACESTART", s(lget("state")));
	check("help", has(CMLib.help().getHelpText("PLAYERTEMPLATES", M, true), "CREATIONTEMPLATES"), "no help");
} finally {
	if (hadMode == null) P.remove("CREATIONTEMPLATES"); else P.setProperty("CREATIONTEMPLATES", hadMode);
	heroes.forEach(function (h) { h.setSession(null); h.setPlayerStats(null); h.destroy(); });
	made.forEach(function (T) { var C = CMLib.catalog().getCatalogMob(T.Name()); if (C != null) CMLib.catalog().delCatalog(C); });
}
done();
