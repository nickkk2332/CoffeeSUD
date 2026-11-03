// P15: NEWCHARLEVEL (N, PARTYMIN, PARTYAVG, LEADER, with +n/-n) and NEWCHARXP: a later character's start by real level-ups.
FIXFILE = "p15-startlevel";
function s(x) { return "" + x; }
var SL = CM.core.StartLevel;
var P = CM.core.CMProps.instance();
var had = { L: P.getProperty("NEWCHARLEVEL"), X: P.getProperty("NEWCHARXP") };
function setp(k, v) { if (v == null) P.remove(k); else P.setProperty(k, v); }
var R = scratchRoom();
var acct = CMClass.getCommon("DefaultPlayerAccount");
var made = [];
function pc(name, level, inWorld) {
	var h = CMClass.getMOB("StdMOB"); h.setName(name);
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); h.playerStats().setAccount(acct);
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	var F = CMClass.getCharClass("Fighter"); h.baseCharStats().setCurrentClass(F); h.baseCharStats().setClassLevel(F, level);
	h.basePhyStats().setLevel(level); h.recoverCharStats(); h.recoverPhyStats(); h.recoverMaxState(); h.resetToMaxState();
	h.setStartRoom(R);
	if (inWorld) h.bringToLife(R, true);
	acct.addNewPlayer(h); CMLib.players().addPlayer(h);
	made.push(h); return h;
}
try {
	var A = pc("Fixpfiftanna", 9, true), B = pc("Fixpfiftbo", 7, false);
	var N = CMClass.getMOB("StdMOB"); N.setName("Fixpfiftnew");
	N.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); N.playerStats().setAccount(acct);
	N.baseCharStats().setMyRace(CMClass.getRace("Human"));
	var F = CMClass.getCharClass("Fighter"); N.baseCharStats().setCurrentClass(F); N.baseCharStats().setClassLevel(F, 1);
	N.basePhyStats().setLevel(1); N.recoverCharStats(); N.recoverPhyStats(); N.setStartRoom(R);
	made.push(N);
	setp("NEWCHARLEVEL", null);
	check("default", SL.target(N, acct) == 1 && SL.apply(N, acct) == 0, SL.target(N, acct));
	setp("NEWCHARLEVEL", "5");
	check("number", SL.target(N, acct) == 5, SL.target(N, acct));
	setp("NEWCHARLEVEL", "PARTYMIN-1");
	check("partymin", SL.target(N, acct) == 6, SL.target(N, acct));
	setp("NEWCHARLEVEL", "PARTYAVG");
	check("partyavg", SL.target(N, acct) == 8, SL.target(N, acct));
	setp("NEWCHARLEVEL", "LEADER-2");
	check("leader", SL.target(N, acct) == 7, SL.target(N, acct));
	// the level-ups are real: practices and trains come with them
	setp("NEWCHARLEVEL", "PARTYMIN-1"); setp("NEWCHARXP", "FLOOR");
	var pr0 = N.getPractices(), tr0 = N.getTrains();
	var got = SL.apply(N, acct);
	check("levelled", got == 5 && N.basePhyStats().level() == 6 && N.getPractices() > pr0 && N.getTrains() > tr0 && !R.isInhabitant(N),
		got + " " + N.basePhyStats().level() + " " + N.getPractices() + "/" + pr0 + " " + N.getTrains() + "/" + tr0);
	check("floor", N.getExperience() == CMLib.leveler().getLevelExperience(N, 5), N.getExperience() + " " + CMLib.leveler().getLevelExperience(N, 5));
	check("keys", CM.core.save.WorldSettings.isWorldKey("NEWCHARLEVEL") && CM.core.save.WorldSettings.isWorldKey("NEWCHARXP"), "not world keys");
	check("help", ("" + CMLib.help().getHelpText("WORLDRULES", M, true)).indexOf("NEWCHARLEVEL") >= 0, "no help");
} finally {
	setp("NEWCHARLEVEL", had.L); setp("NEWCHARXP", had.X);
	made.forEach(function (m) { CMLib.players().delPlayer(m); m.setPlayerStats(null); m.destroy(); });
}
done();
