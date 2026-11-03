// P17: practices and trains per level as formulas (FORMULA_PRACTICESPERLEVEL, FORMULA_TRAINSPERLEVEL, TRAINSATLEVELS), with LVLTRAIN on GenCharClass and FSTTRAN, FSTPRAC, LVLPRAC, LVLTRAIN on GenRace.
FIXFILE = "p17-gains";
function s(x) { return "" + x; }
var LG = CM.core.LevelGains;
var CS = CM.Common.interfaces.CharStats;
var P = CM.core.CMProps.instance();
var KEYS = ["FORMULA_PRACTICESPERLEVEL", "FORMULA_TRAINSPERLEVEL", "TRAINSATLEVELS"];
var had = {};
KEYS.forEach(function (k) { had[k] = P.getProperty(k); P.remove(k); });
function setp(k, v) { if (v == null) P.remove(k); else P.setProperty(k, v); }
var R = scratchRoom();
var F = CMClass.getCharClass("Fighter");
var made = [];
function pc(name) {
	var h = CMClass.getMOB("StdMOB"); h.setName(name);
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	h.baseCharStats().setCurrentClass(F); h.baseCharStats().setClassLevel(F, 1);
	h.baseCharStats().setStat(CS.STAT_WISDOM, 18); h.baseCharStats().setStat(CS.STAT_INTELLIGENCE, 12);
	h.basePhyStats().setLevel(1); h.recoverCharStats(); h.recoverPhyStats(); h.recoverMaxState(); h.resetToMaxState();
	h.setStartRoom(R); h.bringToLife(R, true);
	made.push(h); return h;
}
// a level by itself (experience can bring a practice of its own)
// (a human's Diligent Studying gives a practice more each level, taken off here)
function gain(h, n) { var p = h.getPractices(), t = h.getTrains(), ds = 0; for (var i = 0; i < n; i++) { if (h.fetchEffect("DiligentStudying") != null) ds++; CMLib.leveler().level(h); } return [h.getPractices() - p - ds, h.getTrains() - t]; }
try {
	var h = pc("Fixpseventeen");
	var dflt = Math.max(1, 3 + F.getBonusPracLevel());
	var g = gain(h, 1);
	check("default", g[0] == dflt && g[1] == 1 && LG.practices(h, F) == dflt && LG.trains(h, F) == 1, g + " / " + dflt);
	// a flat 2 practices, a train every second level
	setp("FORMULA_PRACTICESPERLEVEL", "2"); setp("FORMULA_TRAINSPERLEVEL", "(@x1+1)%2");
	g = gain(h, 1);
	check("odd level", h.basePhyStats().level() == 3 && g[0] == 2 && g[1] == 0, h.basePhyStats().level() + ": " + g);
	g = gain(h, 1);
	check("even level", h.basePhyStats().level() == 4 && g[0] == 2 && g[1] == 1, h.basePhyStats().level() + ": " + g);
	// losing a level gives back what it gave, none included
	var p0 = h.getPractices(), t0 = h.getTrains();
	CMLib.leveler().unLevel(h, false);
	check("unlevel", h.basePhyStats().level() == 3 && h.getPractices() == p0 - 2 && h.getTrains() == t0 - 1, h.getPractices() + "/" + p0 + " " + h.getTrains() + "/" + t0);
	p0 = h.getPractices(); t0 = h.getTrains();
	CMLib.leveler().unLevel(h, false);
	check("unlevel none", h.basePhyStats().level() == 2 && h.getPractices() == p0 - 2 && h.getTrains() == t0, h.getPractices() + "/" + p0 + " " + h.getTrains() + "/" + t0);
	gain(h, 2);
	// the variables: @x2 WIS, @x3 INT, @x4 the class's bonus, @x5 the race's
	setp("FORMULA_PRACTICESPERLEVEL", "(@x2/6)+(@x3/12)+@x4+@x5");
	check("variables", LG.practices(h, F) == Math.floor(18 / 6 + 1 + F.getBonusPracLevel()), LG.practices(h, F));
	// bursts at chosen levels
	setp("TRAINSATLEVELS", "1:3, 5:2, 10:2");
	g = gain(h, 1);
	check("burst", h.basePhyStats().level() == 5 && g[1] == 2 && LG.burst(1) == 3 && LG.burst(10) == 2 && LG.burst(7) == 0, h.basePhyStats().level() + ": " + g);
	KEYS.forEach(function (k) { P.remove(k); });
	// GenRace's fields, and their XML
	var GR = CMClass.getRace("GenRace").copyOf();
	GR.setStat("ID", "FixP17Kin"); GR.setStat("NAME", "Fixkin");
	GR.setStat("FSTTRAN", "1"); GR.setStat("FSTPRAC", "2"); GR.setStat("LVLPRAC", "1"); GR.setStat("LVLTRAIN", "1");
	var GR2 = CMClass.getRace("GenRace").copyOf(); GR2.setRacialParms(GR.racialParms());
	check("race fields", s(GR2.getStat("FSTTRAN")) == "1" && s(GR2.getStat("FSTPRAC")) == "2" && s(GR2.getStat("LVLPRAC")) == "1" && s(GR2.getStat("LVLTRAIN")) == "1", s(GR.racialParms()).length);
	h.baseCharStats().setMyRace(GR); h.recoverCharStats();
	check("race gains", LG.practices(h, F) == Math.max(1, 3 + F.getBonusPracLevel() + 1) && LG.trains(h, F) == 2, LG.practices(h, F) + " " + LG.trains(h, F));
	p0 = h.getPractices(); t0 = h.getTrains();
	LG.firstLevel(h);
	check("first level", h.getPractices() == p0 + 2 && h.getTrains() == t0 + 1, h.getPractices() + "/" + p0 + " " + h.getTrains() + "/" + t0);
	// GenCharClass's LVLTRAIN
	var GC = CMClass.getCharClass("GenCharClass").copyOf();
	GC.setStat("ID", "FixP17Class"); GC.setStat("LVLTRAIN", "2");
	var GC2 = CMClass.getCharClass("GenCharClass").copyOf(); GC2.setClassParms(GC.classParms());
	check("class field", s(GC2.getStat("LVLTRAIN")) == "2" && LG.trains(h, GC) == 1 + 2 + 1, s(GC2.getStat("LVLTRAIN")) + " " + LG.trains(h, GC));
	check("keys", CM.core.save.WorldSettings.isWorldKey("FORMULA_TRAINSPERLEVEL") && CM.core.save.WorldSettings.isWorldKey("FORMULA_PRACTICESPERLEVEL") && CM.core.save.WorldSettings.isWorldKey("TRAINSATLEVELS"), "not world keys");
	check("help", s(CMLib.help().getHelpText("WORLDRULES", M, true)).indexOf("FORMULA_TRAINSPERLEVEL") >= 0, "no help");
} finally {
	KEYS.forEach(function (k) { if (had[k] == null) P.remove(k); else P.setProperty(k, had[k]); });
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
}
done();
