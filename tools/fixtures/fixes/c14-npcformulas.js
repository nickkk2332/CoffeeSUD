// C14: FORMULA_NPCARMOR, NPCATTACK, NPCDAMAGE, NPCSPEED, NPCREJUV and NPCMONEY: the defaults give what the code gave
// before, for every level; changed, they change new mobs, RESET MOBCOMBATSTATS's and Prop_Doppleganger's numbers.
FIXFILE = "c14-npcformulas";
var Str = CMProps.Str;
var Int = CMProps.Int;
var LV = CMLib.leveler();
function s(x) { return "" + x; }
var R = scratchRoom();
var m = newMob("GenMob", R);
m.setName("Fixc14mob");
var lpl = CMProps.getIntVar(Int.LASTPLAYERLEVEL);
var tpm = CMProps.getTicksPerMinute();
// the old expressions, as the code had them
function oldArmor(l) { var mid = lpl * 0.44, low = mid / 2.0; return 102 - Math.round((l + mid) * (l / low)); }
function oldSpeed(l) { return 1.0 + Math.floor(l / 30.0); }
function oldRejuv(l) { var r = tpm + tpm + Math.floor(l * tpm / 2); return Math.min(r, tpm * 20); }
var bad = [];
for (var l = 1; l <= 200; l++) {
	m.basePhyStats().setLevel(l);
	var bonus = LV.getAttackBonusNextLevel(m);
	if (LV.getLevelMOBArmor(m) != oldArmor(l)) bad.push("armor " + l + ": " + LV.getLevelMOBArmor(m) + " not " + oldArmor(l));
	if (LV.getLevelAttack(m) != ((l - 1) * bonus) + l) bad.push("attack " + l + ": " + LV.getLevelAttack(m) + " not " + (((l - 1) * bonus) + l));
	if (LV.getLevelMOBDamage(m) != l) bad.push("damage " + l + ": " + LV.getLevelMOBDamage(m));
	if (LV.getLevelMOBSpeed(m) != oldSpeed(l)) bad.push("speed " + l + ": " + LV.getLevelMOBSpeed(m) + " not " + oldSpeed(l));
	if (bad.length > 5) break;
}
check("defaults", bad.length == 0, bad.slice(0, 5).join("; "));
// rejuv and money from fillOutMOB
bad = [];
for (var l = 1; l <= 60; l += 7) {
	LV.fillOutMOB(m, l);
	if (m.basePhyStats().rejuv() != oldRejuv(l)) bad.push("rejuv " + l + ": " + m.basePhyStats().rejuv() + " not " + oldRejuv(l));
	var money = m.getMoney();
	if ((money < 2) || (money > l + 10)) bad.push("money " + l + ": " + money);
}
check("fill-defaults", bad.length == 0, bad.join("; "));

// changed formulas
var was = {};
["FORMULA_NPCARMOR", "FORMULA_NPCATTACK", "FORMULA_NPCDAMAGE", "FORMULA_NPCSPEED", "FORMULA_NPCREJUV", "FORMULA_NPCMONEY"].forEach(function (k) { was[k] = s(CMProps.getVar(Str[k])); });
CMProps.setVar(Str.FORMULA_NPCDAMAGE, "@x1*1.3");
CMProps.setVar(Str.FORMULA_NPCARMOR, "110-(@x1*1.8)");
CMProps.setVar(Str.FORMULA_NPCATTACK, "@x1*3");
CMProps.setVar(Str.FORMULA_NPCSPEED, "2");
CMProps.setVar(Str.FORMULA_NPCREJUV, "0.5");
CMProps.setVar(Str.FORMULA_NPCMONEY, "@x1*100");
m.basePhyStats().setLevel(10);
check("changed", LV.getLevelMOBDamage(m) == 13 && LV.getLevelMOBArmor(m) == 92 && LV.getLevelAttack(m) == 30 && LV.getLevelMOBSpeed(m) == 2,
	"damage " + LV.getLevelMOBDamage(m) + " armor " + LV.getLevelMOBArmor(m) + " attack " + LV.getLevelAttack(m) + " speed " + LV.getLevelMOBSpeed(m));
LV.fillOutMOB(m, 10);
check("fill-changed", m.basePhyStats().damage() == 13 && m.basePhyStats().armor() == 92 && m.basePhyStats().rejuv() == Math.floor(0.5 * tpm) && m.getMoney() == 1000,
	"damage " + m.basePhyStats().damage() + " armor " + m.basePhyStats().armor() + " rejuv " + m.basePhyStats().rejuv() + " money " + m.getMoney());
// RESET MOBCOMBATSTATS-style callers and Prop_Doppleganger use the same functions
var d = CMClass.getMOB("GenMob");
d.basePhyStats().setLevel(20);
check("other-callers", LV.getLevelMOBDamage(d) == 26, "damage at 20: " + LV.getLevelMOBDamage(d));
// a formula that does not compile gives 0, and the game goes on
CMProps.setVar(Str.FORMULA_NPCDAMAGE, "@y1+(");
check("bad", LV.getLevelMOBDamage(m) == 0, "a broken formula gave " + LV.getLevelMOBDamage(m));
d.destroy();

for (var k in was) CMProps.setVar(Str[k], was[k]);
m.basePhyStats().setLevel(10);
check("restored", LV.getLevelMOBDamage(m) == 10, "damage " + LV.getLevelMOBDamage(m));
// the help
var help = s(CMLib.help().getHelpText("NPCFORMULAS", M, true));
check("help", help.indexOf("FORMULA_NPCREJUV") >= 0 && s(CMLib.help().getHelpText("FORMULA_NPCARMOR", M, true)).indexOf("NPCFORMULAS") >= 0, help.substring(0, 80));
m.destroy();
R.destroy();
done();
