// P28: EXPDEFER on game time: experience held until the game clock (not the wall clock) passes the deferral, the award stamped in game time, and a stamp ahead of the game clock treated as long ago.
FIXFILE = "p28-xptime";
var GT = CM.core.GameTime;
var Int = CM.core.CMProps.Int;
var pct0 = CMProps.getIntVar(Int.EXPDEFER_PCT), secs0 = CMProps.getIntVar(Int.EXPDEFER_SECS);
var R = scratchRoom();
var t0 = GT.now(), w0 = java.lang.System.currentTimeMillis();
var made = [];
try {
	CMProps.setIntVar(Int.EXPDEFER_PCT, 100); CMProps.setIntVar(Int.EXPDEFER_SECS, 3600);
	var h = CMClass.getMOB("StdMOB"); h.setName("Fixp28hero"); h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	h.baseCharStats().setMyRace(CMClass.getRace("Human")); var F = CMClass.getCharClass("Fighter"); h.baseCharStats().setCurrentClass(F); h.baseCharStats().setClassLevel(F, 3);
	h.basePhyStats().setLevel(3); h.recoverCharStats(); h.recoverPhyStats(); h.bringToLife(R, true); made.push(h);
	h.setExperience(CMLib.leveler().getLevelExperience(h, 2) + 1);
	var ps = h.playerStats();
	check("stamped in game time", Math.abs(ps.getLastXPAwardMillis() - GT.now()) < 5000, ps.getLastXPAwardMillis() + " vs " + GT.now());
	var xp0 = h.getExperience();
	CMLib.leveler().postExperience(h, "TEST", null, null, 10, true);
	check("held", h.getExperience() == xp0 && ps.getDeferredXP() == 10, h.getExperience() + "/" + xp0 + " deferred " + ps.getDeferredXP());
	// two game hours pass, with no wall-clock time
	GT.set(GT.now() + 2 * 3600 * 1000);
	CMLib.leveler().postExperience(h, "TEST", null, null, 10, true);
	check("released by game time", h.getExperience() == xp0 + 20 && ps.getDeferredXP() == 0 && Math.abs(ps.getLastXPAwardMillis() - GT.now()) < 5000,
		h.getExperience() + "/" + (xp0 + 20) + " deferred " + ps.getDeferredXP());
	// an old wall-clock stamp, ahead of the game clock, does not hold experience for ever
	ps.setLastXPAwardMillis(GT.now() + 10 * 24 * 3600 * 1000);
	var xp1 = h.getExperience();
	CMLib.leveler().postExperience(h, "TEST", null, null, 10, true);
	check("stamp ahead", h.getExperience() == xp1 + 10 && ps.getDeferredXP() == 0, h.getExperience() + "/" + (xp1 + 10));
} finally {
	CMProps.setIntVar(Int.EXPDEFER_PCT, pct0); CMProps.setIntVar(Int.EXPDEFER_SECS, secs0);
	GT.set(t0 + (java.lang.System.currentTimeMillis() - w0)); // the clock as it would be
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
}
done();
