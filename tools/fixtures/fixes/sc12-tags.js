// SC12: MPTAG, NUMTAGGED(), TAGGED() and TAGS().
FIXFILE = "sc12-tags";
["FIXSC12N", "FIXSC12T", "FIXSC12L", "FIXSC12X"].forEach(clearVar);
var a = newMob("GenMob", M.location()); a.setName("Fixsctwelvea");
var b = newMob("GenMob", M.location()); b.setName("Fixsctwelveb");
var m = scriptedMob("a fixsc12 mayor", "RAND_PROG 100\n"
	+ "mptag Fixsctwelvea ADD fixgrateful\nmptag Fixsctwelveb ADD fixgrateful\nmptag Fixsctwelveb TOGGLE fixdebtor\n"
	+ "mpsetvar FIXTEST FIXSC12N $%NUMTAGGED(fixgrateful)%\nmpsetvar FIXTEST FIXSC12L $%TAGS(Fixsctwelveb)%\n"
	+ "if tagged(fixgrateful) and !tagged(fixnosuchtag)\n  mpsetvar FIXTEST FIXSC12T yes\nendif\n"
	+ "mptag Fixsctwelvea DEL fixgrateful\nmpsetvar FIXTEST FIXSC12X $%NUMTAGGED(fixgrateful)%\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("numtagged", gvar("FIXSC12N") == "2" && gvar("FIXSC12X") == "1", "FIXSC12N='" + gvar("FIXSC12N") + "', FIXSC12X='" + gvar("FIXSC12X") + "'");
check("tags", gvar("FIXSC12L").toUpperCase().indexOf("FIXGRATEFUL") >= 0 && gvar("FIXSC12L").toUpperCase().indexOf("FIXDEBTOR") >= 0, "FIXSC12L='" + gvar("FIXSC12L") + "'");
check("tagged", gvar("FIXSC12T") == "yes", "FIXSC12T='" + gvar("FIXSC12T") + "'");
a.destroy(); b.destroy(); m.destroy();
done();
