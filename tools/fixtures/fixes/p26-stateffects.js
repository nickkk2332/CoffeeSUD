// P26: STATEFFECT_<STAT> (per point over N: +k saves, attack, hit points ...) applied as stats are worked out, and @STAT(NAME) and @BASESTAT(NAME) in formulas.
FIXFILE = "p26-stateffects";
var SE = CM.core.StatEffects;
var CS = CM.Common.interfaces.CharStats;
var P = CM.core.CMProps.instance();
var KEYS = ["STATEFFECT_CHARISMA", "STATEFFECT_WISDOM"];
var had = {};
KEYS.forEach(function (k) { had[k] = P.getProperty(k); P.remove(k); });
var R = scratchRoom();
var made = [];
function recover(m) { m.recoverCharStats(); m.recoverPhyStats(); m.recoverMaxState(); }
try {
	SE.reload();
	var m = CMClass.getMOB("StdMOB"); m.setName("Fixp26hero"); m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	m.baseCharStats().setMyRace(CMClass.getRace("Human")); m.basePhyStats().setLevel(5);
	m.baseCharStats().setStat(CS.STAT_CHARISMA, 14); m.baseCharStats().setStat(CS.STAT_WISDOM, 16);
	m.bringToLife(R, true); made.push(m); recover(m);
	var save0 = m.charStats().getStat(CS.STAT_SAVE_GENERAL), fire0 = m.charStats().getStat(CS.STAT_SAVE_FIRE);
	var att0 = m.phyStats().attackAdjustment(), dmg0 = m.phyStats().damage(), hp0 = m.maxState().getHitPoints(), crit0 = m.charStats().getStat(CS.STAT_CRIT_CHANCE_PCT_WEAPON);
	P.setProperty("STATEFFECT_CHARISMA", "per point over 10: +1 SAVES, +2 ATTACK, +5 HITPOINTS");
	P.setProperty("STATEFFECT_WISDOM", "per 2 points over 12: +1 DAMAGE, +3 CRITCHANCE%");
	SE.reload(); recover(m);
	check("saves", m.charStats().getStat(CS.STAT_SAVE_GENERAL) == save0 + 4 && m.charStats().getStat(CS.STAT_SAVE_FIRE) == fire0 + 4, m.charStats().getStat(CS.STAT_SAVE_GENERAL) + "/" + save0);
	check("attack and hit points", m.phyStats().attackAdjustment() == att0 + 8 && m.maxState().getHitPoints() == hp0 + 20, m.phyStats().attackAdjustment() + "/" + att0 + " " + m.maxState().getHitPoints() + "/" + hp0);
	check("per 2 points", m.phyStats().damage() == dmg0 + 2 && m.charStats().getStat(CS.STAT_CRIT_CHANCE_PCT_WEAPON) == crit0 + 6, m.phyStats().damage() + "/" + dmg0 + " " + m.charStats().getStat(CS.STAT_CRIT_CHANCE_PCT_WEAPON) + "/" + crit0);
	// below the line, nothing
	m.baseCharStats().setStat(CS.STAT_CHARISMA, 9); recover(m);
	check("below", m.charStats().getStat(CS.STAT_SAVE_GENERAL) == save0 && m.phyStats().attackAdjustment() == att0, m.charStats().getStat(CS.STAT_SAVE_GENERAL) + "/" + save0);
	m.baseCharStats().setStat(CS.STAT_CHARISMA, 14); recover(m);
	// @STAT and @BASESTAT, for the formula's mob
	var CMth = CM.core.CMath;
	var was = CMth.setFormulaSubject(m);
	var v1 = CMth.parseMathExpression("@STAT(CHARISMA)*2");
	var F = CMth.compileMathExpression("@BASESTAT(WISDOM)+@x1");
	var v2 = CMth.parseMathExpression(F, [3], 0.0);
	var v3 = CMth.parseMathExpression("@STAT(NOSUCHSTAT)+1");
	CMth.setFormulaSubject(was);
	var v4 = CMth.parseMathExpression("@STAT(CHARISMA)+1");
	check("formulas", v1 == m.charStats().getStat(CS.STAT_CHARISMA) * 2 && v2 == 19 && v3 == 1 && v4 == 1, v1 + " " + v2 + " " + v3 + " " + v4);
	// in a formula key: a mob's carrying capacity
	// REPLACECHARSTAT renames a stat, and the stat list follows (CODES are rebuilt from the ini at boot)
	P.setProperty("REPLACECHARSTAT_CHARISMA", "PRESENCE,pr,presence,commanding"); CS.CODES.reset();
	var nm = "" + CS.CODES.NAME(CS.STAT_CHARISMA), list = "" + CM.core.TextMacros.filter("@StatList@", m);
	P.remove("REPLACECHARSTAT_CHARISMA"); CS.CODES.reset();
	check("replacecharstat", nm == "PRESENCE" && list.toUpperCase().indexOf("PRESENCE") >= 0 && ("" + CS.CODES.NAME(CS.STAT_CHARISMA)) == "CHARISMA", nm + " / " + list);
	check("world key", CM.core.save.WorldSettings.isWorldKey("STATEFFECT_LUCK"), "not a world key");
	check("help", ("" + CMLib.help().getHelpText("STATEFFECTS", M, true)).indexOf("@STAT(") >= 0, "no help");
} finally {
	if (P.getProperty("REPLACECHARSTAT_CHARISMA") != null) { P.remove("REPLACECHARSTAT_CHARISMA"); CS.CODES.reset(); }
	KEYS.forEach(function (k) { if (had[k] == null) P.remove(k); else P.setProperty(k, had[k]); });
	SE.reload();
	made.forEach(function (x) { if (!x.amDestroyed()) x.destroy(); });
}
done();
