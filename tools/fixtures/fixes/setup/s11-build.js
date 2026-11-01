// S11, in builder mode: a custom ability, as CREATE ABILITY makes it, with a
// script of several lines that compares with <
FIXFILE = "s11-build";
var CR = CMClass.getAbility("GenAbility").copyOf();
CR.setStat("CLASS", "Spell_FixBrand");
CR.setStat("NAME", "Fix Brand");
CR.setStat("SCRIPT", "ONCE_PROG\nif var($n FIXHEAT < 5)\n  mpechoat $n FIXBRAND burns\nendif\n~\n");
CMLib.database().DBCreateAbility(CR.ID(), "GenAbility", CR.getStat("ALLXML"));
M.tell("FIXINFO script " + String(CR.getStat("SCRIPT")).length);
check("made", String(CR.getStat("SCRIPT")).indexOf("FIXHEAT < 5") > 0, "the script is " + CR.getStat("SCRIPT"));
done();
