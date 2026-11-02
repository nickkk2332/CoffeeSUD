// SC1: FOR ... IN LIST, MPLIST and the list functions.
FIXFILE = "sc1-lists";
["FIXSC1A", "FIXSC1B", "FIXSC1C", "FIXSC1D", "FIXSC1E", "FIXSC1F"].forEach(clearVar);
VARS.setVar("FIXSC1W", "SUSPECTS", "");
var m = scriptedMob("a fixsc1 sleuth", "RAND_PROG 100\n"
	+ "mplist FIXSC1W SUSPECTS ADD cook butler gardener\n"
	+ "mplist FIXSC1W SUSPECTS ADDUNIQUE cook maid\n"
	+ "mplist FIXSC1W SUSPECTS DEL gardener\n"
	+ "mpsetvar FIXTEST FIXSC1A $<FIXSC1W SUSPECTS>\n"
	+ "mpsetvar FIXTEST FIXSC1B $%LISTCOUNT($<FIXSC1W SUSPECTS>)%:$%LISTGET($<FIXSC1W SUSPECTS> 1)%:$%LISTFIND($<FIXSC1W SUSPECTS> maid)%\n"
	+ "mpsetvar FIXTEST FIXSC1C $%LISTJOIN($<FIXSC1W SUSPECTS> '+')%\n"
	+ "for $1 in list $<FIXSC1W SUSPECTS>\n  mpsetvar FIXTEST FIXSC1D $<FIXTEST FIXSC1D>$1.\nnext\n"
	+ "if listhas($<FIXSC1W SUSPECTS> butler) and listcount($<FIXSC1W SUSPECTS> == 3)\n  mpsetvar FIXTEST FIXSC1E yes\nendif\n"
	+ "mpsetvar FIXTEST FIXSC1F $%LISTPICK($<FIXSC1W SUSPECTS>)%\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("mplist", gvar("FIXSC1A") == "cook butler maid", "FIXSC1A='" + gvar("FIXSC1A") + "'");
check("functions", gvar("FIXSC1B") == "3:butler:2", "FIXSC1B='" + gvar("FIXSC1B") + "'");
check("join", gvar("FIXSC1C") == "cook+butler+maid", "FIXSC1C='" + gvar("FIXSC1C") + "'");
check("for-in-list", gvar("FIXSC1D") == "cook.butler.maid.", "FIXSC1D='" + gvar("FIXSC1D") + "'");
check("tests", gvar("FIXSC1E") == "yes", "FIXSC1E='" + gvar("FIXSC1E") + "'");
check("pick", ["cook", "butler", "maid"].indexOf(gvar("FIXSC1F")) >= 0, "FIXSC1F='" + gvar("FIXSC1F") + "'");
VARS.setVar("FIXSC1W", "SUSPECTS", "");
m.destroy();
done();
