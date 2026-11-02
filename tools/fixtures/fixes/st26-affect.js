// ST26: AFFECT_PROG on an effect put on or coming off.
FIXFILE = "st26-affect";
var R = M.location();
["FIXST26A", "FIXST26R"].forEach(clearVar);
var S = scriptOn(R, "AFFECT_PROG ADD Prayer_Bless\nmpsetvar FIXTEST FIXST26A $t:$G\n~\nAFFECT_PROG REMOVE *\nmpsetvar FIXTEST FIXST26R $G\n~\n");
var P = newMob("GenMob", R);
P.setName("Fixsttwentysix");
var A = CMClass.getAbility("Prayer_Bless");
P.addEffect(A);
runQueued(S);
check("add", gvar("FIXST26A") == "Fixsttwentysix:Prayer_Bless", "FIXST26A='" + gvar("FIXST26A") + "'");
P.delEffect(A);
runQueued(S);
check("remove", gvar("FIXST26R") == "Prayer_Bless", "FIXST26R='" + gvar("FIXST26R") + "'");
R.delScript(S);
P.destroy();
done();
