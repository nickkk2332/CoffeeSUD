// SC6: MPCALLFUNC target func and CALLFUNC(target func) call another
// thing's functions; MPSIGNAL target name [args] [DELAY n] fires SIGNAL_PROG
// on a thing, TAG:, ROOM, a room's exit; $g the args, $t the sender.
FIXFILE = "sc6-signals";
var R = M.location();
["FIXSC6A", "FIXSC6B", "FIXSC6C", "FIXSC6D", "FIXSC6E", "FIXSC6F", "FIXSC6G"].forEach(clearVar);
var G = newMob("GenMob", R); G.setName("Fixsc6gate");
var SG = scriptOn(G, "FUNCTION_PROG raise\nmpsetvar FIXTEST FIXSC6A raised:$g\n~\nFUNCTION_PROG height\nreturn 12\n~\n");
var A = newMob("GenMob", R); A.setName("Fixsc6altar");
A.addTag("fixsc6sun");
var SA = scriptOn(A, "SIGNAL_PROG lit\nmpsetvar FIXTEST FIXSC6C $<FIXTEST FIXSC6C>x\nmpsetvar FIXTEST FIXSC6D $g:$t\n~\n"
	+ "SIGNAL_PROG bell\nmpsetvar FIXTEST FIXSC6E rang\n~\n"
	+ "SIGNAL_PROG late\nmpsetvar FIXTEST FIXSC6F late\n~\n");
var L = newMob("GenMob", R); L.setName("Fixsc6lever");
var SL = scriptOn(L, "RAND_PROG 100\n"
	+ "mpcallfunc Fixsc6gate raise portcullis\n"
	+ "mpsetvar FIXTEST FIXSC6B $%CALLFUNC(Fixsc6gate height)%\n"
	+ "mpsignal Fixsc6altar lit three\n"
	+ "mpdisable\n~\n");
tickMob(L);
runQueued(SL);
check("call", gvar("FIXSC6A") == "raised:portcullis" && gvar("FIXSC6B") == "12", "FIXSC6A='" + gvar("FIXSC6A") + "', FIXSC6B='" + gvar("FIXSC6B") + "'");
runQueued(SA);
check("signal", gvar("FIXSC6C") == "x" && gvar("FIXSC6D") == "three:Fixsc6lever", "FIXSC6C='" + gvar("FIXSC6C") + "', FIXSC6D='" + gvar("FIXSC6D") + "'");

// TAG:, ROOM, and a delay
L.delScript(SL);
var SL2 = scriptOn(L, "RAND_PROG 100\nmpsignal TAG:fixsc6sun lit\nmpsignal ROOM bell\nmpsignal Fixsc6altar late DELAY 3\nmpdisable\n~\n");
tickMob(L);
runQueued(SL2);
SA.dequeResponses(null);
check("tag-room", gvar("FIXSC6C") == "xx" && gvar("FIXSC6E") == "rang" && gvar("FIXSC6F") == "", "FIXSC6C='" + gvar("FIXSC6C") + "', FIXSC6E='" + gvar("FIXSC6E") + "', FIXSC6F='" + gvar("FIXSC6F") + "' (too soon)");
runQueued(SA);
check("delay", gvar("FIXSC6F") == "late", "FIXSC6F='" + gvar("FIXSC6F") + "'");

// a room's exit: here:direction
var dir = -1;
for (var d = 0; d < 6 && dir < 0; d++) if (R.getRawExit(d) != null) dir = d;
if (dir >= 0) {
	var X = R.getRawExit(dir);
	var SX = scriptOn(X, "SIGNAL_PROG creak\nmpsetvar FIXTEST FIXSC6G creaked:$g\n~\n");
	L.delScript(SL2);
	var SL3 = scriptOn(L, "RAND_PROG 100\nmpsignal here:" + CMLib.directions().getDirectionName(dir) + " creak slowly\nmpdisable\n~\n");
	tickMob(L);
	runQueued(SL3);
	runQueued(SX);
	check("exit", gvar("FIXSC6G") == "creaked:slowly", "FIXSC6G='" + gvar("FIXSC6G") + "'");
	X.delScript(SX);
} else
	check("exit", false, "the room has no exit");
A.delTag("fixsc6sun");
[G, A, L].forEach(function(x) { x.destroy(); });
done();
