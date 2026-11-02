// N4: Sleeper: sleeps (ALWAYS, or SLEEPS=NIGHT...), woken by WAKEON events at WAKECHANCE, less by a sneak; DEEP only by
// an attack; GRUMBLE, AWAKEFOR, and ALARM, which wakes the sleepers round about; LOOK says soundly or stirs.
FIXFILE = "n4-sleeper";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
function enter(P, X) { X.send(P, CMClass.getMsg(P, X, null, CMMsg.MSG_ENTER, null)); }
function asleep(m) { return CMLib.flags().isSleeping(m); }
var R2 = null;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
	var E = R.getExitInDir(d);
	if ((R.getRoomInDir(d) != null) && (E != null) && E.isOpen() && R.getRoomInDir(d) != R) { R2 = R.getRoomInDir(d); break; }
}
var V = newMob("GenMob", R);
V.setName("Fixn4thief");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); // a player's character: the alarm does not rouse it
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
function sleeper(name, parms, room) {
	var m = newMob("GenMob", room || R); m.setName(name);
	var B = CMClass.getBehavior("Sleeper"); B.setParms(parms); m.addBehavior(B);
	tickMob(m);
	return m;
}
function look(m) { var lm = CMClass.getMsg(V, m, null, CMMsg.MSG_LOOK, null); if (R.okMessage(V, lm)) R.send(V, lm); return out(); }

// ALWAYS: asleep; LOOK: soundly
var ogre = sleeper("Fixn4ogre", "ALWAYS WAKEON=NOISE,ENTER WAKECHANCE=100 GRUMBLE=\"Fixn4 who disturbs Grok?\" AWAKEFOR=4");
check("sleeps", asleep(ogre), "not asleep");
out();
check("soundly", has(look(ogre), "is sleeping soundly"), "LOOK said no sleep");
// someone walks in: woken, grumbles
enter(V, R);
check("enter-wakes", !asleep(ogre) && has(out(), "Fixn4 who disturbs Grok?"), "still asleep after someone came in");
// AWAKEFOR: asleep again after it
for (var i = 0; i < 6; i++) tickMob(ogre);
check("sleeps-again", asleep(ogre), "awake still");
// a sneak gets past (a hundred in Sneak)
V.basePhyStats().setDisposition(V.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_SNEAKING); V.recoverPhyStats();
var sn = CMClass.getAbility("Thief_Sneak"); if (sn != null) { sn.setProficiency(100); V.addAbility(sn); }
enter(V, R);
check("sneak-past", asleep(ogre) && has(look(ogre), "stirs"), "the sneak woke it, or it did not stir");
V.basePhyStats().setDisposition(V.basePhyStats().disposition() & ~CM.Common.interfaces.PhyStats.IS_SNEAKING); V.recoverPhyStats();

// DEEP: noise does not wake it; an attack does
var bear = sleeper("Fixn4bear", "DEEP WAKECHANCE=100");
enter(V, R);
R.send(V, CMClass.getMsg(V, null, null, CMMsg.MSG_NOISE, "<S-NAME> bang(s) a pot."));
check("deep", asleep(bear), "a deep sleeper woke to noise");
var poke = CMClass.getMsg(V, bear, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> poke(s) <T-NAME>.");
R.send(V, poke);
check("deep-attack", !asleep(bear), "an attack did not wake it");
V.makePeace(true); bear.makePeace(true);

// ALARM: wakes those round about (here, and next door)
var lookout = sleeper("Fixn4lookout", "WAKEON=TOUCH WAKECHANCE=100 ALARM");
var camp = sleeper("Fixn4bandit", "WAKECHANCE=0", R2);
check("camp-asleep", asleep(camp) && asleep(lookout), "not all asleep");
out();
R.send(V, CMClass.getMsg(V, lookout, null, CMMsg.MSG_NOISYMOVEMENT, "<S-NAME> nudge(s) <T-NAME>."));
var heard = out();
check("alarm", !asleep(lookout) && !asleep(camp), "lookout " + asleep(lookout) + ", camp " + asleep(camp));
check("alarm-shout", has(heard, "'Intruders!'"), heard.substring(Math.max(0, heard.length - 120))); // a test's watcher sees it as its own: "You shout"
for (var i = 0; i < 3; i++) tickMob(camp);
check("roused-stays-up", !asleep(camp), "the roused bandit went straight back to sleep");

// SLEEPS=NIGHT: up by day
var C = CMLib.time().localClock(R);
var was = C.getHourOfDay();
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); var t = s(C.getTODCode()); if (t == "NIGHT" && night < 0) night = h; if (t == "DAY" && day < 0) day = h; }
C.setHourOfDay(day);
// WAKEON=TOUCH: no noise roll (a fight left nearby by another test would make it a dice throw)
var owl = sleeper("Fixn4watchman", "SLEEPS=NIGHT WAKECHANCE=0 WAKEON=TOUCH");
check("night-only-day", !asleep(owl), "asleep by day");
C.setHourOfDay(night);
tickMob(owl);
check("night-only-night", asleep(owl), "awake by night");
C.setHourOfDay(day);
tickMob(owl);
check("morning", !asleep(owl), "still asleep in the day");
C.setHourOfDay(was);

[ogre, bear, lookout, camp, owl].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
done();
