// SC13: LOAD=file KEY=value...; ARG(name) reads them, ARGDEFAULT lines in the
// file give defaults; each object's arguments are its own.
FIXFILE = "sc13-loadargs";
var R = M.location();
["FIXSC13A", "FIXSC13B", "FIXSC13C", "FIXSC13D"].forEach(clearVar);
new CM.core.CMFile("/resources/_fixes/sc13guard.script", M).saveText(
	"ARGDEFAULT GREETING=\"Halt!\" POST=nowhere\n"
	+ "FUNCTION_PROG who\n"
	+ "mpsetvar FIXTEST $%ARG(VAR)% $%ARG(POST)%:$%ARG(GREETING)%:$%ARG(MISSING)%\n"
	+ "if arg(POST == castle#12)\n mpsetvar FIXTEST FIXSC13B match\nendif\n"
	+ "~\n");
var g1 = newMob("GenMob", R); g1.setName("a fixsc13 guard");
var S1 = scriptOn(g1, "LOAD=_fixes/sc13guard.script POST=castle#12 VAR=FIXSC13A");
var g2 = newMob("GenMob", R); g2.setName("a fixsc13 guard");
var S2 = scriptOn(g2, "LOAD=_fixes/sc13guard.script GREETING=\"Who goes there?\" VAR=FIXSC13C");
var r1 = S1.callFunction(g1, "who", "", M);
var r2 = S2.callFunction(g2, "who", "", M);
check("args", r1 != null && gvar("FIXSC13A") == "castle#12:Halt!:" && gvar("FIXSC13B") == "match", "the function " + (r1 == null ? "is not there" : "ran") + ", FIXSC13A='" + gvar("FIXSC13A") + "', FIXSC13B='" + gvar("FIXSC13B") + "'");
check("own", r2 != null && gvar("FIXSC13C") == "nowhere:Who goes there?:", "FIXSC13C='" + gvar("FIXSC13C") + "'");
check("file", ("" + S1.externalFiles()).indexOf("_fixes/sc13guard.script") >= 0 && ("" + S1.externalFiles()).indexOf("POST") < 0, "the files: " + S1.externalFiles());
g1.destroy(); g2.destroy();
done();
