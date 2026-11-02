// SC17: IF / ELSEIF / ELSE / ENDIF; WHILE (cond) ... ENDWHILE, capped by
// passes; CONTINUE in FOR and WHILE; MPLOCAL and $(name); named function
// parameters.
FIXFILE = "sc17-grammar";
var R = M.location();
["FIXSC17A", "FIXSC17B", "FIXSC17C", "FIXSC17D", "FIXSC17E", "FIXSC17F", "FIXSC17N"].forEach(clearVar);
var m = newMob("GenMob", R); m.setName("a fixsc17 guard");
var S = scriptOn(m, "FUNCTION_PROG pick\n"
	+ "if var(FIXTEST FIXSC17N == 1)\n mpsetvar FIXTEST FIXSC17A one\n"
	+ "elseif var(FIXTEST FIXSC17N == 2)\n mpsetvar FIXTEST FIXSC17A two\n"
	+ "else if var(FIXTEST FIXSC17N == 3)\n mpsetvar FIXTEST FIXSC17A three\n"
	+ "else\n mpsetvar FIXTEST FIXSC17A other\nendif\n"
	+ "mpsetvar FIXTEST FIXSC17A $<FIXTEST FIXSC17A>.\n"
	+ "~\n"
	+ "FUNCTION_PROG count\n"
	+ "mplocal n 0\n"
	+ "while (math($(n) < 5))\n"
	+ " mplocal n $%MATH($(n) + 1)%\n"
	+ " if math($(n) == 2)\n  continue\n endif\n"
	+ " mpsetvar FIXTEST FIXSC17B $<FIXTEST FIXSC17B>$(n)\n"
	+ "endwhile\n"
	+ "for $1 = 1 to 4\n"
	+ " if math($1 == 3)\n  continue\n endif\n"
	+ " mpsetvar FIXTEST FIXSC17C $<FIXTEST FIXSC17C>$1\n"
	+ "next\n"
	+ "~\n"
	+ "FUNCTION_PROG forever\n"
	+ "mplocal k 0\n"
	+ "while (math(1 == 1)) 7\n"
	+ " mplocal k $%MATH($(k) + 1)%\n"
	+ "endwhile\n"
	+ "mpsetvar FIXTEST FIXSC17D $(k)\n"
	+ "~\n"
	+ "FUNCTION_PROG heal who amount\n"
	+ "mpsetvar FIXTEST FIXSC17E $(who):$(amount)\n"
	+ "~\n"
	+ "FUNCTION_PROG outer\n"
	+ "mplocal who me\n"
	+ "mpcallfunc heal Bob 20 more\n"
	+ "mpsetvar FIXTEST FIXSC17F $(who):$(amount)\n"
	+ "~\n");
var picks = [];
[1, 2, 3, 4].forEach(function(n) { VARS.setVar("FIXTEST", "FIXSC17N", "" + n); clearVar("FIXSC17A"); S.callFunction(m, "pick", "", M); picks.push(gvar("FIXSC17A")); });
check("elseif", picks.join(",") == "one.,two.,three.,other.", "picks " + picks.join(","));
S.callFunction(m, "count", "", M);
check("while-continue", gvar("FIXSC17B") == "1345", "FIXSC17B='" + gvar("FIXSC17B") + "'");
check("for-continue", gvar("FIXSC17C") == "124", "FIXSC17C='" + gvar("FIXSC17C") + "'");
S.callFunction(m, "forever", "", M);
check("while-cap", gvar("FIXSC17D") == "7", "FIXSC17D='" + gvar("FIXSC17D") + "'");
S.callFunction(m, "outer", "", M);
check("params", gvar("FIXSC17E") == "Bob:20 more" && gvar("FIXSC17F") == "me:", "FIXSC17E='" + gvar("FIXSC17E") + "', FIXSC17F='" + gvar("FIXSC17F") + "'");
m.destroy();
done();
