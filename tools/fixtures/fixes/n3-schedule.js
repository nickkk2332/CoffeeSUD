// N3: Schedule: entries WHEN: PLACE [ACTIVITY] [SAY/EMOTE]; it walks to the entry in force, does what it says there
// (WORK, SLEEP, SHOP open here and shut elsewhere), and STAT($i SCHEDULE) is the entry in force.
FIXFILE = "n3-schedule";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
// a room next to this one, by an open exit
var R2 = null;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
	var E = R.getExitInDir(d);
	if ((R.getRoomInDir(d) != null) && (E != null) && E.isOpen() && R.getRoomInDir(d) != R) { R2 = R.getRoomInDir(d); break; }
}
check("setup", R2 != null, "no room next to " + R.roomID());
var C = CMLib.time().localClock(R);
var was = C.getHourOfDay();
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); var t = s(C.getTODCode()); if (t == "NIGHT" && night < 0) night = h; if (t == "DAY" && day < 0) day = h; }
var V = newMob("GenMob", R2);
V.setName("Fixn3customer");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o; }

var miller = newMob("GenShopkeeper", R);
miller.setName("Fixn3miller");
miller.setStartRoom(R);
var S = CMClass.getBehavior("Schedule");
S.setParms("DAY: " + s(R2.roomID()) + " SHOP WORK \"pours grain into the hopper|wipes flour from his hands\" SAY Fixn3 back to work.; "
	+ "NIGHT: HOME SLEEP; SHOPMSG=\"Fixn3 the mill is shut till morning.\"");
miller.addBehavior(S);
var stat = scriptOn(miller, "RAND_PROG 100\nmpsetvar FIXTEST FIXN3S $%STAT($i SCHEDULE)%\n~\n");
clearVar("FIXN3S");

// by day: it walks to the mill, says so, and is open
C.setHourOfDay(day);
out();
tickMob(miller);
check("walks", miller.location() == R2, "at " + miller.location().roomID());
check("says", has(out(), "Fixn3 back to work."), "no word on arriving");
check("stat", has(S.getStat("SCHEDULE"), "DAY: ") && has(gvar("FIXN3S"), "DAY: "), "STAT says '" + gvar("FIXN3S") + "'");
var listMsg = CMClass.getMsg(V, miller, null, CMMsg.MSG_LIST, null);
check("open", miller.okMessage(miller, listMsg), "closed by day at the mill");
// WORK: now and then
var worked = false;
for (var i = 0; i < 60 && !worked; i++) { tickMob(miller); if (has(out(), "pours grain into the hopper") || has(out(), "wipes flour from his hands")) worked = true; }
check("works", worked, "no work in 60 ticks");

// by night: home to sleep, and shut
C.setHourOfDay(night);
for (var i = 0; i < 3; i++) tickMob(miller);
check("home", miller.location() == R && CMLib.flags().isSleeping(miller), "at " + miller.location().roomID() + ", sleeping " + CMLib.flags().isSleeping(miller));
listMsg = CMClass.getMsg(V, miller, null, CMMsg.MSG_LIST, null);
check("shut", !miller.okMessage(miller, listMsg), "open at night");
check("stat-night", has(S.getStat("SCHEDULE"), "NIGHT: HOME"), s(S.getStat("SCHEDULE")));
// woken in the night, it goes back to sleep after a while
CMLib.commands().postStand(miller, true, false);
for (var i = 0; i < 12; i++) tickMob(miller);
check("back-to-sleep", CMLib.flags().isSleeping(miller), "still awake");

// morning: it gets up and goes back
C.setHourOfDay(day);
tickMob(miller);
check("morning", !CMLib.flags().isSleeping(miller) && miller.location() == R2, "sleeping " + CMLib.flags().isSleeping(miller) + ", at " + miller.location().roomID());

// hours, and no entry in force: it stays where it is
var S2 = CMClass.getBehavior("Schedule");
S2.setParms(night + "-" + night + ": HOME");
var porter = newMob("GenMob", R2);
porter.setName("Fixn3porter"); porter.setStartRoom(R);
porter.addBehavior(S2);
C.setHourOfDay(day);
tickMob(porter);
check("no-entry", porter.location() == R2 && s(S2.getStat("SCHEDULE")) == "", "moved with no entry in force");
C.setHourOfDay(night);
tickMob(porter);
check("hours", porter.location() == R, "the hour range did not move it");

// WANDER 1: steps about, never more than one room from the place
var S3 = CMClass.getBehavior("Schedule");
S3.setParms("DAY: " + s(R.roomID()) + " WANDER 1");
var guard = newMob("GenMob", R);
guard.setName("Fixn3guard"); guard.setStartRoom(R);
guard.addBehavior(S3);
C.setHourOfDay(day);
var away = false, tooFar = "";
function nextTo(X) { if (X == R) return true; for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) if (R.getRoomInDir(d) == X) return true; return false; }
for (var i = 0; i < 80; i++) {
	tickMob(guard);
	var L = guard.location();
	if (L != R) away = true;
	if (!nextTo(L)) tooFar = s(L.roomID());
}
check("wander", away && tooFar == "", "away " + away + ", too far at '" + tooFar + "'");

C.setHourOfDay(was);
miller.destroy(); porter.destroy(); guard.destroy(); V.destroy();
done();
