// S11, in a game built from the world's files: the custom ability, with its
// script as it was made
FIXFILE = "s11-check";
var A = CMClass.getAbility("Spell_FixBrand");
check("there", A != null, "no Spell_FixBrand");
var script = (A == null) ? "" : String(A.getStat("SCRIPT"));
check("script", script == "ONCE_PROG\nif var($n FIXHEAT < 5)\n  mpechoat $n FIXBRAND burns\nendif\n~\n", "the script is " + JSON.stringify(script));
check("name", (A != null) && (A.name() == "Fix Brand"), "named " + ((A == null) ? "" : A.name()));
done();
