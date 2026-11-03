// W4: WAIT n HOURS and WAIT UNTIL, MPPASSTIME, MPINTERRUPT, Prop_InnRoom; a running clock paused for a wait and started again after.
FIXFILE = "w4-wait";
var PS = CM.Common.interfaces.PhyStats;
var GameTime = CM.core.GameTime;
var Waits = CM.core.Waits;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
function words(t) { var L = new java.util.ArrayList(); t.split(" ").forEach(function (w) { L.add(w); }); return L; }
var wasStopped = GameTime.isStopped(), wasPaused = GameTime.isPaused();
var X = CMClass.getAreaType("StdArea"); X.setName("Fixw4Inn");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var TC = CMLib.time().globalClock().copyOf(); X.setTimeObj(TC);
var INN = CMClass.getLocale("StdRoom"); INN.setRoomID("Fixw4Inn#0"); INN.setDisplayText("Fixw4 inn room"); INN.setArea(X);
var V = CMClass.getMOB("GenMob"); V.setName("Fixw4pip"); V.baseState().setHitPoints(100); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(INN, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }
function cleanup() { GameTime.cancelAdvance(); if ((!wasStopped) && GameTime.isPaused() && (!wasPaused)) GameTime.resume(); }
try {
	// the conditions
	var why = java.lang.reflect.Array.newInstance(java.lang.String, 1); why[0] = "";
	TC.setHourOfDay(3);
	var atFive = Waits.until(V, words("HOUR 5"), why);
	var before = atFive.getAsBoolean();
	TC.setHourOfDay(5);
	var dusk = TC.getDawnToDusk()[2];
	TC.setHourOfDay((dusk + 2) % TC.getHoursInDay());
	var untilDusk = Waits.until(V, words("DUSK"), why);
	var notYet = untilDusk.getAsBoolean();
	TC.setHourOfDay(dusk);
	check("until", (!before) && (!notYet) && untilDusk.getAsBoolean() && Waits.until(V, words("DOCK"), why) == null && has(why[0], "no vessel")
		&& Waits.until(V, words("FOREVER"), why) == null, "five " + before + " dusk " + notYet + " why " + why[0]);
	// a wait: the clock stopped for it, the hours told, ended by a variable, the clock as it was after
	clearVar("W4V");
	var byVar = Waits.until(V, words("VAR FIXTEST W4V == done"), why);
	TC.setHourOfDay(7); out();
	var started = Waits.start(V, 2 * CMProps.getMillisPerMudHour(), byVar, false, false);
	var stoppedFor = GameTime.isStopped() && GameTime.isAdvancing() && Waits.waiter() == V;
	TC.setHourOfDay(8); GameTime.checkStop();
	var told = has(out(), "An hour passes. It is now 8 o'clock.");
	VARS.setVar("FIXTEST", "W4V", "done"); GameTime.checkStop();
	check("wait", started && stoppedFor && told && (!GameTime.isAdvancing()) && Waits.waiter() == null && GameTime.isStopped() == wasStopped,
		"started " + started + " stopped " + stoppedFor + " told " + told + " advancing " + GameTime.isAdvancing() + " stopped now " + GameTime.isStopped() + "/" + wasStopped);
	clearVar("W4V");
	// WAIT n HOURS and WAIT UNTIL, the command
	var Wait = CMClass.getCommand("Wait");
	Wait.execute(V, words("WAIT 2 HOURS"), 0);
	var cmdWait = GameTime.isAdvancing() && has(out(), "You wait 2 hours.");
	cleanup();
	Wait.execute(V, words("WAIT UNTIL TEATIME"), 0);
	check("command", cmdWait && (!GameTime.isAdvancing()) && has(out(), "Wait until TEATIME?"), "wait " + cmdWait);
	// MPINTERRUPT and MPPASSTIME
	Waits.start(V, 5 * CMProps.getMillisPerMudHour(), null, false, true);
	var m = newMob("GenMob", INN); m.setName("Fixw4bell");
	scriptOn(m, "RAND_PROG 100\nmpinterrupt Fixw4pip The fixw4 bell rings.\n~\n"); tickMob(m); m.destroy();
	var interrupted = (!GameTime.isAdvancing()) && has(out(), "The fixw4 bell rings.");
	var m2 = newMob("GenMob", INN); m2.setName("Fixw4ferryman");
	scriptOn(m2, "RAND_PROG 100\nmppasstime 4 HOURS QUIET\n~\n"); tickMob(m2); m2.destroy();
	var passing = GameTime.isAdvancing();
	cleanup();
	check("scripts", interrupted && passing, "interrupted " + interrupted + " passing " + passing);
	// an inn's room: a full rest after an hour's wait
	var IR = CMClass.getAbility("Prop_InnRoom"); IR.setMiscText("HOURS=1 MSG=\"You wake in the fixw4 inn, rested.\""); INN.addNonUninvokableEffect(IR);
	V.curState().setHitPoints(10);
	Waits.innRest(V, CMProps.getMillisPerMudHour() / 2);
	var tooShort = V.curState().getHitPoints() == 10;
	Waits.innRest(V, 2 * CMProps.getMillisPerMudHour());
	check("inn", tooShort && V.curState().getHitPoints() == V.maxState().getHitPoints() && has(out(), "You wake in the fixw4 inn, rested."), "hp " + V.curState().getHitPoints());
	var hw = s(CMLib.help().getHelpText("WAIT", M, false)), hi = s(CMLib.help().getHelpText("PROP_INNROOM", M, true)), hs = s(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true));
	check("help", has(hw, "WAIT UNTIL") && has(hi, "HOURS") && has(hs, "MPPASSTIME"), "wait " + has(hw, "WAIT UNTIL") + " inn " + has(hi, "HOURS") + " scriptable " + has(hs, "MPPASSTIME") + ": " + hw.substring(0, 200));
} finally {
	cleanup();
	V.setSession(null); V.destroy();
	CMLib.map().delArea(X);
}
done();
