// SC10: string functions.
FIXFILE = "sc10-strings";
["FIXSC10A", "FIXSC10B", "FIXSC10C", "FIXSC10D", "FIXSC10E"].forEach(clearVar);
var m = scriptedMob("a fixsc10 scribe", "RAND_PROG 100\n"
	+ "mpsetvar FIXTEST FIXSC10A $%UPPER(ab)%:$%LOWER(CD)%:$%CAP(ef)%:$%LEN(hello)%:$%TRIM(  x  )%\n"
	+ "mpsetvar FIXTEST FIXSC10B $%SUBSTR(abcdef 2 3)%:$%SUBSTR(abcdef 4)%:$%REPLACE(a-b-c '-' '+')%\n"
	+ "mpsetvar FIXTEST FIXSC10C $%ANAME(apple)%:$%PLURAL(sword 2)%:$%PLURAL(sword 1)%:$%STRPOS(hello ll)%\n"
	+ "mpsetvar FIXTEST FIXSC10D [$%PADRIGHT(ab 4)%]\n"
	+ "if strstarts(hello he) and strends(hello lo) and !strstarts(hello lo)\n  mpsetvar FIXTEST FIXSC10E yes\nendif\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("case", gvar("FIXSC10A") == "AB:cd:Ef:5:x", "FIXSC10A='" + gvar("FIXSC10A") + "'");
check("substr-replace", gvar("FIXSC10B") == "cde:ef:a+b+c", "FIXSC10B='" + gvar("FIXSC10B") + "'");
check("english", gvar("FIXSC10C") == "an apple:swords:sword:2", "FIXSC10C='" + gvar("FIXSC10C") + "'");
check("pad", gvar("FIXSC10D") == "[ab  ]", "FIXSC10D='" + gvar("FIXSC10D") + "'");
check("tests", gvar("FIXSC10E") == "yes", "FIXSC10E='" + gvar("FIXSC10E") + "'");
m.destroy();
done();
