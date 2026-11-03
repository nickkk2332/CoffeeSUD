// P9: an NPC joins the profile as a player character: Recruits.recruit (for JavaScript), MPRECRUIT, and RECRUIT with Prop_Recruitable.
FIXFILE = "p9-recruit";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var RC = CM.core.Recruits, Party = CM.core.Party, WR = CM.core.WorldRules;
var CS = CM.Common.interfaces.CharStats;
var P = CM.core.CMProps.instance();
var hadMax = P.getProperty("PROFILEMAX");
var R = M.location();
var acct = Party.profileOf(M);
var F = CMLib.factions().factions().nextElement(), fid = s(F.factionID());
var made = [];
function npc(name, race, cls, level) {
	var m = newMob("GenMob", R); m.setName(name);
	m.baseCharStats().setMyRace(CMClass.getRace(race)); m.baseCharStats().setCurrentClass(CMClass.getCharClass(cls));
	m.basePhyStats().setLevel(level); m.recoverCharStats(); m.recoverPhyStats();
	made.push(m); return m;
}
function seenSay() { return ""; }
try {
	check("name", s(RC.nameFor(npc("Fixmira the ranger", "Elf", "Fighter", 4), null)) == "Fixmira" && s(RC.nameFor(made[0], "Ranger Two")) == "Rangertwo", s(RC.nameFor(made[0], null)));
	var mira = made[0];
	mira.baseCharStats().setStat(CS.STAT_DEXTERITY, 17); mira.recoverCharStats();
	var A = CMClass.getAbility("Skill_Recall"); A.setProficiency(70); mira.addAbility(A);
	var bow = CMClass.getItem("GenWeapon"); bow.setName("a fixp9 longbow"); mira.addItem(bow);
	var pouch = CMClass.getItem("GenContainer"); pouch.setName("a fixp9 pouch"); mira.addItem(pouch);
	var arrow = CMClass.getItem("GenItem"); arrow.setName("a fixp9 arrow"); mira.addItem(arrow); arrow.setContainer(pouch);
	mira.addFaction(fid, 222); mira.addTattoo("FIXP9MIRA"); mira.setDescription("A ranger with green eyes.");
	var smell = CMClass.getAbility("Prop_Smell"); smell.setMiscText("pine needles"); mira.addNonUninvokableEffect(smell);
	check("name taken", has(RC.refusal(mira, M, false, s(M.Name())), "There is a character called"), s(RC.refusal(mira, M, false, s(M.Name()))));
	var r1 = RC.recruit(mira, M, "REST", null);
	var T = CMLib.players().getLoadPlayer("Fixmira");
	check("recruited", r1 == null && T != null && mira.amDestroyed() && Party.profileOf(T) == acct && !CMLib.flags().isInTheGame(T, true), s(r1));
	if (T != null) {
		var t = T.findItem("fixp9 pouch"), a2 = T.findItem("fixp9 arrow");
		check("kept", s(T.baseCharStats().getMyRace().ID()) == "Elf" && s(T.baseCharStats().getCurrentClass().ID()) == "Fighter" && T.basePhyStats().level() == 4
			&& T.baseCharStats().getStat(CS.STAT_DEXTERITY) == 17 && T.fetchAbility("Skill_Recall") != null && T.fetchAbility("Skill_Recall").proficiency() == 70,
			T.baseCharStats().getMyRace().ID() + " " + T.basePhyStats().level());
		check("things", T.findItem("fixp9 longbow") != null && t != null && a2 != null && a2.container() == t && T.fetchFaction(fid) == 222
			&& T.findTattoo("FIXP9MIRA") != null && s(T.description()) == "A ranger with green eyes." && T.fetchEffect("Prop_Smell") != null, s(a2 == null ? "no arrow" : a2.container()));
	}
	// MPRECRUIT from a script, resting
	var sage = scriptedMob("Fixsage the wise", "ONCE_PROG 100\nmprecruit $i " + s(M.Name()) + " REST NAME=Fixsage\n~\n");
	made.push(sage);
	tickMob(sage);
	check("mprecruit", CMLib.players().getLoadPlayer("Fixsage") != null && sage.amDestroyed(), "no Fixsage");
	// RECRUIT, with Prop_Recruitable: the mask, the cost, then the party
	var dwarf = npc("Fixgrim", "Dwarf", "Fighter", 2);
	var PR = CMClass.getAbility("Prop_Recruitable"); PR.setMiscText('MASK="-RACE +Dwarf"'); dwarf.addNonUninvokableEffect(PR);
	check("mask", has(RC.recruitHere(M, dwarf), "will not join you"), s(RC.recruitHere(M, dwarf)));
	var bran = npc("Fixbran", "Human", "Fighter", 3);
	var PR2 = CMClass.getAbility("Prop_Recruitable"); PR2.setMiscText('COST=5 MESSAGE="<S-NAME> grin(s) and join(s) <T-NAMESELF>."'); bran.addNonUninvokableEffect(PR2);
	var money = CMLib.beanCounter().getTotalAbsoluteNativeValue(M);
	if (money < 10) { CMLib.beanCounter().giveSomeoneMoney(M, 10); money = CMLib.beanCounter().getTotalAbsoluteNativeValue(M); }
	CMClass.getCommand("RecruitCmd").execute(M, list("RECRUIT", "Fixbran"), 0);
	var B = CMLib.players().getLoadPlayer("Fixbran");
	check("recruit cmd", B != null && bran.amDestroyed() && Math.abs(CMLib.beanCounter().getTotalAbsoluteNativeValue(M) - (money - 5)) < 0.01, s(B) + " " + CMLib.beanCounter().getTotalAbsoluteNativeValue(M) + "/" + money);
	check("in the party", B != null && CMLib.flags().isInTheGame(B, true) && Party.isInParty(Party.leaderOf(M), B), "not in the party");
	if (B != null && CMLib.flags().isInTheGame(B, true)) Party.rest(B);
	// a full profile
	P.setProperty("PROFILEMAX", "" + acct.numPlayers());
	var full = npc("Fixlast", "Human", "Fighter", 1);
	check("full", has(RC.refusal(full, M, false, "Fixlast"), "Your profile is full"), s(RC.refusal(full, M, false, "Fixlast")));
	check("help", has(CMLib.help().getHelpText("RECRUIT", M, false), "RECRUIT") && has(CMLib.help().getHelpText("PROP_RECRUITABLE", M, true), "MASK"), "no help");
} finally {
	if (hadMax == null) P.remove("PROFILEMAX"); else P.setProperty("PROFILEMAX", hadMax);
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
}
done();
