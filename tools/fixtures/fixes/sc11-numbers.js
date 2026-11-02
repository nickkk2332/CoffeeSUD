// SC11: =value, long arithmetic with % and ^, NUMBER() with signs and
// decimals, DICE(), PICK(), WPICK(), ROLLNORMAL(), CLAMP() and ABS().
FIXFILE = "sc11-numbers";
["FIXSC11M", "FIXSC11P", "FIXSC11N", "FIXSC11D", "FIXSC11T", "FIXSC11K", "FIXSC11W", "FIXSC11C", "FIXSC11R"].forEach(clearVar);
var m = scriptedMob("a fixsc11 dicer", "RAND_PROG 100\n"
	+ "mpsetvar FIXTEST FIXSC11M =-5\n"
	+ "mpsetvar FIXTEST FIXSC11P =17\nmpsetvar FIXTEST FIXSC11P %5\n"
	+ "mpsetvar FIXTEST FIXSC11R =3000000000\nmpsetvar FIXTEST FIXSC11R +1\n"
	+ "if number(-2.5) and !number(x2)\n  mpsetvar FIXTEST FIXSC11N yes\nendif\n"
	+ "mpsetvar FIXTEST FIXSC11D $%DICE(2d1+3)%\n"
	+ "if dice(1d1+5 >= 6)\n  mpsetvar FIXTEST FIXSC11T yes\nendif\n"
	+ "mpsetvar FIXTEST FIXSC11K $%PICK(only)%\n"
	+ "mpsetvar FIXTEST FIXSC11W $%WPICK(never:0 always:5)%\n"
	+ "mpsetvar FIXTEST FIXSC11C $%CLAMP(15 0 10)%:$%ABS(-4)%:$%ROLLNORMAL(1 1 2)%\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("literal", gvar("FIXSC11M") == "-5", "FIXSC11M='" + gvar("FIXSC11M") + "'");
check("remainder", gvar("FIXSC11P") == "2", "FIXSC11P='" + gvar("FIXSC11P") + "'");
check("long", gvar("FIXSC11R") == "3000000001", "FIXSC11R='" + gvar("FIXSC11R") + "'");
check("number", gvar("FIXSC11N") == "yes", "FIXSC11N='" + gvar("FIXSC11N") + "'");
check("dice", gvar("FIXSC11D") == "5" && gvar("FIXSC11T") == "yes", "FIXSC11D='" + gvar("FIXSC11D") + "', FIXSC11T='" + gvar("FIXSC11T") + "'");
check("picks", gvar("FIXSC11K") == "only" && gvar("FIXSC11W") == "always", "FIXSC11K='" + gvar("FIXSC11K") + "', FIXSC11W='" + gvar("FIXSC11W") + "'");
check("clamp-abs", gvar("FIXSC11C") == "10:4:3", "FIXSC11C='" + gvar("FIXSC11C") + "'");
m.destroy();
done();
