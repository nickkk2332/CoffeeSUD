// N1: WHEN="mask" (on the host), TOD=DAWN|DAY|DUSK|NIGHT and HOURS=a-b on any behaviour: while the condition fails
// it neither ticks nor hears messages; STAT BEHAVIORS says whether it is on, and its key check knows the three.
FIXFILE = "n1-when";
var R = M.location();
var BW = CM.core.BehaviorWhen;
var TB = CMClass.getCommand("TraceBCmd");
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixn1watcher");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }
var C = CMLib.time().localClock(R);
var was = C.getHourOfDay();
// an hour of the night and one of the day, on this area's clock
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) {
	C.setHourOfDay(h);
	var t = s(C.getTODCode());
	if (t == "NIGHT" && night < 0) night = h;
	if (t == "DAY" && day < 0) day = h;
}
check("hours", night >= 0 && day >= 0, "night " + night + ", day " + day);

var wolf = newMob("GenMob", R);
wolf.setName("Fixn1wolf");
var E = CMClass.getBehavior("Emoter");
E.setParms("min=1 max=1 chance=100 TOD=NIGHT;fixn1 howls at the moon.");
wolf.addBehavior(E);

function statOf(what) { return s(call(TB, "describeBehaviors", [V, what])); }
// by day: it does not act (STAT BEHAVIORS: has not acted yet, its condition not met)
C.setHourOfDay(day);
tickMob(wolf); tickMob(wolf); tickMob(wolf);
var stat = statOf("Fixn1wolf");
check("day-quiet", has(stat, "has not acted yet") && !BW.active(E, wolf), stat);
check("stat-off", has(stat, "its condition (WHEN, TOD or HOURS) is not met: it is off now") && !has(stat, "unknown key TOD"), stat);
// by night: it does
C.setHourOfDay(night);
tickMob(wolf); tickMob(wolf); tickMob(wolf);
stat = statOf("Fixn1wolf");
check("night-howls", has(stat, "last acted ") && BW.active(E, wolf), stat);
check("stat-on", has(stat, "it is on now"), stat);

// HOURS=a-b, and round midnight
var last = C.getHoursInDay() - 1;
C.setHourOfDay(last);
check("hours-wrap", BW.holds("HOURS=" + last + "-1", wolf) && !BW.holds("HOURS=2-3", wolf), "hour " + last);
C.setHourOfDay(2);
check("hours-in", BW.holds("HOURS=2-3", wolf) && !BW.holds("HOURS=" + last + "-1", wolf), "hour 2");

// WHEN=mask, on the host
check("when-mask", BW.holds("WHEN=\"-NAMES +Fixn1wolf\"", wolf) && !BW.holds("WHEN=\"-NAMES +Fixn1wolf\"", V), "the mask on the host");
// a behaviour with none is always on; and so is a script, whatever its text
var F = CMClass.getBehavior("Emoter");
F.setParms("min=1 max=1 chance=100;fixn1 sniffs.");
check("none", BW.active(F, wolf) && s(BW.state(F, wolf)) == "", s(BW.state(F, wolf)));
var S = CMClass.getBehavior("Scriptable");
S.setParms("RAND_PROG 100\nsay TOD=NIGHT HOURS=1-2\n~\n");
C.setHourOfDay(day);
check("script", BW.active(S, wolf), "a script was gated");

// a room's behaviour, gated on the room
var RE = CMClass.getBehavior("Emoter");
RE.setParms("min=1 max=1 chance=100 broadcast HOURS=" + night + "-" + night + ";fixn1 a cold wind blows.");
R.addBehavior(RE);
C.setHourOfDay(day);
for (var i = 0; i < 3; i++) R.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
stat = statOf("HERE");
check("room-day", has(stat, "has not acted yet") && has(stat, "it is off now"), stat);
C.setHourOfDay(night);
for (var i = 0; i < 3; i++) R.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
stat = statOf("HERE");
check("room-night", has(stat, "last acted ") && has(stat, "it is on now"), stat);
R.delBehavior(RE);

C.setHourOfDay(was);
wolf.destroy(); V.destroy();
done();
