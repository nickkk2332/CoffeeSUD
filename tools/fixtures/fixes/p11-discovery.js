// P11: Prop_Discovery (XP, ON, ONCE, MESSAGE, TATTOO, MASK, JOURNAL) on a room, item and mob; ON=FIND from Prop_Discoverable; DISCOVERYXP for first visits.
FIXFILE = "p11-discovery";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var DS = CM.core.Discovery, QJ = CM.core.QuestJournal;
var MSG = CM.Common.interfaces.CMMsg;
var P = CM.core.CMProps.instance();
var hadXP = P.getProperty("DISCOVERYXP");
var R = scratchRoom(), R2 = scratchRoom();
var made = [];
var acct = CMClass.getCommon("DefaultPlayerAccount");
function hero(name) {
	var h = newMob("GenMob", R); h.setName(name);
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	var F = CMClass.getCharClass("Fighter"); h.baseCharStats().setCurrentClass(F); h.baseCharStats().setClassLevel(F, 5);
	h.basePhyStats().setLevel(5); h.recoverCharStats(); h.recoverPhyStats();
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); h.playerStats().setAccount(acct); acct.addNewPlayer(h);
	var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	S.initialize(null, "MEMORY"); h.setSession(S); S.setMob(h);
	made.push(h); return h;
}
function said(h) { return s(field(h.session(), "bout")).replace(/\s+/g, " "); }
function prop(on, text) { var A = CMClass.getAbility("Prop_Discovery"); A.setMiscText(text); on.addNonUninvokableEffect(A); return A; }
function send(A, who, target, code) { A.executeMsg(target, CMClass.getMsg(who, target, null, code, null)); }
try {
	var H = hero("Fixp11hero"), H2 = hero("Fixp11twin");
	check("player", !H.isMonster(), "a monster");
	var G = prop(R, 'XP=150 MESSAGE="You find the grotto!" TATTOO=FIXP11GROTTO JOURNAL="Found the grotto behind the falls."');
	var x0 = H.getExperience();
	send(G, H, R, MSG.MSG_ENTER);
	var j = QJ.read(H), jt = ""; for (var i = 0; i < j.entries.size(); i++) jt += s(j.entries.get(i).text);
	check("enter", H.getExperience() == x0 + 150 && H.findTattoo("FIXP11GROTTO") != null && has(said(H), "You find the grotto!") && has(jt, "Found the grotto"),
		(H.getExperience() - x0) + " " + jt);
	send(G, H, R, MSG.MSG_ENTER);
	check("once", H.getExperience() == x0 + 150, H.getExperience() - x0);
	send(G, H2, R, MSG.MSG_ENTER);
	check("each character", H2.findTattoo("FIXP11GROTTO") != null, "not for the twin");
	// a percent of the level, by reading, kept for the whole profile
	var tab = CMClass.getItem("GenReadable"); tab.setName("a fixp11 tablet"); R.addItem(tab); made.push(tab);
	var L = prop(tab, "XP=10% ON=READ ONCE=PROFILE TATTOO=FIXP11LORE");
	var span = CMLib.leveler().getLevelExperience(H, 5) - CMLib.leveler().getLevelExperience(H, 4);
	var x1 = H.getExperience();
	send(L, H, tab, MSG.MSG_READ);
	check("percent", H.getExperience() - x1 == Math.round(span * 0.1) && acct.findTattoo("FIXP11LORE") != null, (H.getExperience() - x1) + " of " + span);
	var x2 = H2.getExperience();
	send(L, H2, tab, MSG.MSG_READ);
	send(L, H, tab, MSG.MSG_GET);
	check("profile once", H2.getExperience() == x2 && H2.findTattoo("FIXP11LORE") == null, H2.getExperience() - x2);
	// a mask, and talking to a mob
	var sage = newMob("GenMob", R); sage.setName("Fixp11sage"); made.push(sage);
	var T = prop(sage, 'XP=20 MASK="-RACE +Dwarf"');
	var x3 = H.getExperience();
	send(T, H, sage, MSG.MSG_SPEAK);
	check("mask", H.getExperience() == x3, H.getExperience() - x3);
	T.setMiscText("XP=20");
	send(T, H, sage, MSG.MSG_SPEAK);
	check("talk", H.getExperience() == x3 + 20, H.getExperience() - x3);
	// ON=FIND, from a hidden thing found
	var gem = CMClass.getItem("GenItem"); gem.setName("a fixp11 gem"); R.addItem(gem); made.push(gem);
	var D = CMClass.getAbility("Prop_Discoverable"); D.setMiscText("FOUNDBY=search"); gem.addNonUninvokableEffect(D);
	prop(gem, "XP=40 ON=FIND");
	var x4 = H.getExperience();
	var ok1 = call(D, "tryFind", [H]);
	check("find", s(ok1) == "true" && H.getExperience() == x4 + 40, ok1 + " " + (H.getExperience() - x4));
	// DISCOVERYXP: each room first entered
	P.setProperty("DISCOVERYXP", "5+@x1");
	var x5 = H.getExperience();
	CM.core.ScriptEvents.discovered(H, R2, false);
	check("discoveryxp", H.getExperience() == x5 + 10, H.getExperience() - x5);
	check("help", has(CMLib.help().getHelpText("PROP_DISCOVERY", M, true), "ONCE") && CM.core.save.WorldSettings.isWorldKey("DISCOVERYXP"), "no help");
} finally {
	if (hadXP == null) P.remove("DISCOVERYXP"); else P.setProperty("DISCOVERYXP", hadXP);
	made.forEach(function (m) { if (m.session && m.session() != null) m.setSession(null); if (m.playerStats && m.playerStats() != null) m.setPlayerStats(null); m.destroy(); });
}
done();
