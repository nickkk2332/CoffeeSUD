// C15: Prop_MobRank RANK=MINION|NORMAL|ELITE|BOSS: the rank's hit points, damage, attack, armor and experience from
// MOBRANK_<NAME> (an edit retunes every mob of the rank); RANK($i) and RANK($i == ELITE); -RANK +ELITE and
// +RANK -BOSS in masks; the tag in LOOK and CONSIDER; a rank the ini adds.
FIXFILE = "c15-mobrank";
var MR = CM.core.MobRanks;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixc15pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
V.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter")); V.recoverCharStats();
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function run(cmd, words) { var J = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) J.add(w[i]); out(); CMClass.getCommand(cmd).execute(V, J, 0); return out(); }
function ranked(name, parms) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.basePhyStats().setDamage(10); m.basePhyStats().setAttackAdjustment(50); m.basePhyStats().setArmor(50);
	m.baseState().setHitPoints(100);
	if (parms != null) { var A = CMClass.getAbility("Prop_MobRank"); A.setMiscText(parms); m.addNonUninvokableEffect(A); }
	m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState();
	return m;
}
check("names", s(MR.names()).indexOf("MINION") >= 0 && s(MR.names()).indexOf("BOSS") >= 0, s(MR.names()));
var plain = ranked("Fixc15peasant", null);
var guard = ranked("Fixc15guard", "RANK=ELITE");
var king = ranked("Fixc15king", "BOSS");
var rat = ranked("Fixc15rat", "RANK=minion");
function stats(m) { return m.maxState().getHitPoints() + "/" + m.phyStats().damage() + "/" + m.phyStats().attackAdjustment() + "/" + m.phyStats().armor(); }
check("normal", stats(plain) == "100/10/50/50", stats(plain));
check("elite", stats(guard) == "200/13/60/40", stats(guard));
check("boss", stats(king) == "400/15/70/30", stats(king));
check("minion", stats(rat) == "50/8/40/50", stats(rat));
// one edit retunes every mob of the rank
CMProps.instance().setProperty("MOBRANK_ELITE", "HP+50% DAMAGE+0% TAG=[veteran] RESIST=\"fire 30%\"");
guard.recoverPhyStats(); guard.recoverCharStats(); guard.recoverMaxState();
var CS = CM.Common.interfaces.CharStats;
var fireBase = plain.charStats().getStat(CS.STAT_SAVE_FIRE);
check("retuned", stats(guard) == "150/10/50/50" && guard.charStats().getStat(CS.STAT_SAVE_FIRE) == fireBase + 30, stats(guard) + " fire " + guard.charStats().getStat(CS.STAT_SAVE_FIRE));
CMProps.instance().setProperty("MOBRANK_ELITE", "");
guard.recoverPhyStats(); guard.recoverCharStats(); guard.recoverMaxState();
check("back", stats(guard) == "200/13/60/40", stats(guard));
// experience for the kill
var got = CMLib.leveler().postExperience(V, "COMBAT:", guard, "", 100, true);
var got2 = CMLib.leveler().postExperience(V, "COMBAT:", plain, "", 100, true);
// (what else changes combat experience applies to both)
check("xp", got2 > 0 && Math.abs(got - (got2 * 2.5)) <= 1, "elite " + got + " normal " + got2);
// scripts
["FIXC15R", "FIXC15I"].forEach(clearVar);
scriptOn(guard, "RAND_PROG 100\nmpsetvar FIXTEST FIXC15R $%RANK($i)%\nif RANK($i == ELITE)\nmpsetvar FIXTEST FIXC15I yes\nendif\nmpdisable\n~\n");
tickMob(guard);
check("script", gvar("FIXC15R") == "ELITE" && gvar("FIXC15I") == "yes", "RANK " + gvar("FIXC15R") + " test " + gvar("FIXC15I"));
// masks
var Z = CMLib.masking();
check("mask-req", Z.maskCheck("-RANK +ELITE +BOSS", guard, true) && Z.maskCheck("-RANK +ELITE +BOSS", king, true) && !Z.maskCheck("-RANK +ELITE +BOSS", plain, true),
	"-RANK +ELITE +BOSS");
check("mask-not", !Z.maskCheck("+RANK -BOSS", king, true) && Z.maskCheck("+RANK -BOSS", guard, true) && Z.maskCheck("-RANK +NORMAL", plain, true), "+RANK -BOSS");
check("mask-desc", has(Z.maskDesc("-RANK +ELITE"), "Requires the following rank: ELITE"), s(Z.maskDesc("-RANK +ELITE")));
// LOOK and CONSIDER
var seen = run("Look", "LOOK Fixc15guard");
check("look", has(seen, "Fixc15guard [elite]"), seen.substring(Math.max(0, seen.length - 200)));
seen = run("Consider", "CONSIDER Fixc15king");
check("consider", has(seen, "Fixc15king [boss]"), seen);
check("look-plain", !has(run("Look", "LOOK Fixc15peasant"), "["), "a mob without a rank shows one");
// a rank the ini adds
CMProps.instance().setProperty("MOBRANK_CHAMPION", "HP+200% TAG=[champion] POWER=3");
var champ = ranked("Fixc15champ", "RANK=CHAMPION");
check("ini-rank", champ.maxState().getHitPoints() == 300 && s(MR.names()).indexOf("CHAMPION") >= 0 && MR.powerOf(champ) == 3 && MR.powerOf(king) == 5,
	stats(champ) + " power " + MR.powerOf(champ));
CMProps.instance().setProperty("MOBRANK_CHAMPION", "");
// the help
check("help", has(CMLib.help().getHelpText("PROP_MOBRANK", M, true), "MOBRANK_"), "no AHELP PROP_MOBRANK");

[plain, guard, king, rat, champ].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
