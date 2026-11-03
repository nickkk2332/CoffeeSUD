// P29: ageing as data: a GenRace's AGEEFFECTS in place of the coded ones, AGEDISEASES, AGE_PROG and the AGECATEGORY event on birthdays, STARTAGE_ entries.
FIXFILE = "p29-ageing";
function s(x) { return "" + x; }
var AG = CM.core.Ageing;
var CS = CM.Common.interfaces.CharStats;
var RC = CM.Races.interfaces.Race;
var Agent = CM.Common.interfaces.AccountStats.Agent;
var P = CM.core.CMProps.instance();
var KEYS = ["AGEDISEASES", "STARTAGE_VETERAN"];
var had = {};
KEYS.forEach(function (k) { had[k] = P.getProperty(k); P.remove(k); });
var R = scratchRoom();
var made = [];
["P29CAT", "P29BDAY"].forEach(clearVar);
function mk(name, race) {
	var m = CMClass.getMOB("StdMOB"); m.setName(name); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	m.baseCharStats().setMyRace(race); m.basePhyStats().setLevel(5);
	m.baseCharStats().setStat(CS.STAT_WISDOM, 12); m.baseCharStats().setStat(CS.STAT_STRENGTH, 12);
	m.recoverCharStats(); m.recoverPhyStats(); m.bringToLife(R, true); made.push(m); return m;
}
function ageTo(m, years) { m.baseCharStats().setStat(CS.STAT_AGE, years); m.recoverCharStats(); }
try {
	var GR = CMClass.getRace("GenRace").copyOf(); GR.setStat("ID", "FixP29Elf"); GR.setStat("NAME", "Fixelf");
	GR.setStat("AGEEFFECTS", "MIDDLEAGED: WIS+2 STR-1; ANCIENT: STR-3");
	var GR2 = CMClass.getRace("GenRace").copyOf(); GR2.setRacialParms(GR.racialParms());
	check("xml", s(GR2.getStat("AGEEFFECTS")) == "MIDDLEAGED: WIS+2 STR-1; ANCIENT: STR-3", s(GR2.getStat("AGEEFFECTS")));
	var chart = GR.getAgingChart();
	var m = mk("Fixp29elf", GR);
	ageTo(m, chart[RC.AGE_MATURE]);
	var w0 = m.charStats().getStat(CS.STAT_WISDOM), s0 = m.charStats().getStat(CS.STAT_STRENGTH), a0 = m.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ);
	ageTo(m, chart[RC.AGE_MIDDLEAGED]);
	check("ageeffects", m.baseCharStats().ageCategory() == RC.AGE_MIDDLEAGED && m.charStats().getStat(CS.STAT_WISDOM) == w0 + 2 && m.charStats().getStat(CS.STAT_STRENGTH) == s0 - 1
		&& m.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ) == a0, m.charStats().getStat(CS.STAT_WISDOM) + "/" + w0 + " " + m.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ) + "/" + a0);
	// a race without them keeps the coded effects
	var H = CMClass.getRace("Human"), hchart = H.getAgingChart();
	var h = mk("Fixp29human", H);
	ageTo(h, hchart[RC.AGE_MATURE]); var ha0 = h.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ);
	ageTo(h, hchart[RC.AGE_MIDDLEAGED]);
	check("coded kept", h.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ) == ha0 - 1, h.charStats().getStat(CS.STAT_MAX_STRENGTH_ADJ) + "/" + ha0);
	// AGEDISEASES
	check("no ageing diseases", !AG.diseases(m), "set");
	P.setProperty("AGEDISEASES", "MIDDLEAGED:Disease_Cold:100");
	// at 100% it is given, but a save always keeps 5% (MUDFight.checkSavingThrows), and one made gives immunity:
	// taken away, and tried again, so the dice cannot end it
	var caught = false;
	for (var t = 0; t < 10 && !caught; t++) {
		var TI = m.fetchEffect("TemporaryImmunity");
		if (TI != null) m.delEffect(TI);
		caught = AG.diseases(m) && m.fetchEffect("Disease_Cold") != null;
	}
	check("agediseases", caught, s(m.fetchEffect("Disease_Cold")));
	P.remove("AGEDISEASES");
	// a birthday into a new age: AGE_PROG and AGECATEGORY
	CMLib.achievements().evaluateAchievement(Agent.PLAYER, 'ACH_FIXP29OLD=EVENT="AGECATEGORY" DISPLAY="Grow old" TITLE="" REWARDS="" PLAYERMASK="" CATEGORY=OLD', true);
	var elder = newMob("GenMob", R); elder.setName("Fixp29 elder"); made.push(elder);
	scriptOn(elder, "AGE_PROG CATEGORY\nmpsetvar FIXTEST P29CAT $0\n~\nAGE_PROG BIRTHDAY\nmpsetvar FIXTEST P29BDAY $1\n~\n");
	ageTo(m, chart[RC.AGE_OLD] - 1);
	var was = m.baseCharStats().ageCategory();
	ageTo(m, chart[RC.AGE_OLD]);
	AG.aged(m, was);
	check("age_prog", gvar("P29CAT") == "OLD" && gvar("P29BDAY") == s(chart[RC.AGE_OLD]), gvar("P29CAT") + " " + gvar("P29BDAY"));
	check("agecategory", m.findTattoo("ACH_FIXP29OLD") != null, "no achievement");
	// STARTAGE_
	P.setProperty("STARTAGE_VETERAN", "-TATTOOS +FIXP29VET:35");
	AG.startAge(h);
	var notYet = h.baseCharStats().getStat(CS.STAT_AGE);
	h.addTattoo("FIXP29VET"); AG.startAge(h);
	check("startage", notYet != 35 && h.baseCharStats().getStat(CS.STAT_AGE) == 35, notYet + " " + h.baseCharStats().getStat(CS.STAT_AGE));
	check("keys and help", CM.core.save.WorldSettings.isWorldKey("AGEDISEASES") && CM.core.save.WorldSettings.isWorldKey("STARTAGE_VETERAN")
		&& s(CMLib.help().getHelpText("AGEING", M, true)).indexOf("AGEEFFECTS") >= 0, "no keys or help");
} finally {
	KEYS.forEach(function (k) { if (had[k] == null) P.remove(k); else P.setProperty(k, had[k]); });
	CMLib.achievements().deleteAchievement("ACH_FIXP29OLD");
	made.forEach(function (x) { if (!x.amDestroyed()) x.destroy(); });
}
done();
