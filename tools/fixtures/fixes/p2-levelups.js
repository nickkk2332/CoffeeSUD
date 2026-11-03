// P2: exact level-ups from scripts: MPLEVELUP, MPLEVELDOWN, MPEXP $n NEXTLEVEL, EXPTONEXT(), and LevelUps for JavaScript.
FIXFILE = "p2-levelups";
function s(x) { return "" + x; }
var LU = CM.core.LevelUps;
var R0 = M.location();
var H = CMClass.getMOB("StdMOB"); H.setName("Fixptwohero");
H.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
H.baseCharStats().setMyRace(CMClass.getRace("Human"));
var FC = CMClass.getCharClass("Fighter"); H.baseCharStats().setCurrentClass(FC); H.baseCharStats().setClassLevel(FC, 1);
H.basePhyStats().setLevel(1); H.recoverCharStats(); H.recoverPhyStats(); H.recoverMaxState(); H.resetToMaxState();
H.setStartRoom(R0); H.bringToLife(R0, true);
var made = [H];
function lvl() { return H.basePhyStats().level(); }
try {
	var need = LU.toNext(H);
	H.setExperience(H.getExperience() + Math.floor(need / 2)); // half way to level 2
	check("to next", LU.toNext(H) > 0 && LU.toNext(H) < need, LU.toNext(H) + " " + need);
	clearVar("FIXP2N");
	var sm = scriptedMob("Fixp2sage", "ONCE_PROG 100\nmpsetvar FIXTEST FIXP2N $%EXPTONEXT(Fixptwohero)%\nmplevelup Fixptwohero 2\n~\n");
	made.push(sm);
	var n0 = LU.toNext(H);
	tickMob(sm);
	check("exptonext", gvar("FIXP2N") == s(n0), gvar("FIXP2N") + " " + n0);
	check("levelup 2", lvl() == 3 && LU.toNext(H) > 0, "level " + lvl());
	var sm2 = scriptedMob("Fixp2sage2", "ONCE_PROG 100\nmpexp Fixptwohero NEXTLEVEL\n~\n");
	made.push(sm2);
	tickMob(sm2);
	check("nextlevel", lvl() == 4, "level " + lvl());
	var sm3 = scriptedMob("Fixp2sage3", "ONCE_PROG 100\nmpleveldown Fixptwohero\n~\n");
	made.push(sm3);
	tickMob(sm3);
	var xpAfter = H.getExperience(), floor = CMLib.leveler().getLevelExperience(H, lvl() - 1);
	check("leveldown", lvl() == 3 && xpAfter >= floor && LU.toNext(H) > 0, "level " + lvl() + " xp " + xpAfter + "/" + floor);
	check("js", LU.up(H, 1, "SCRIPT:FIXP2") == 1 && lvl() == 4 && LU.down(H, 2) == 2 && lvl() == 2, "level " + lvl());
	check("help", ("" + CMLib.help().getHelpText("SCRIPTABLE", M, true)).indexOf("MPLEVELUP") >= 0 || ("" + CMLib.help().getHelpText("MPLEVELUP", M, true)).indexOf("MPLEVELUP") >= 0, "no help");
} finally {
	clearVar("FIXP2N");
	made.forEach(function (m) { if (m.playerStats() != null) m.setPlayerStats(null); m.destroy(); });
}
done();
