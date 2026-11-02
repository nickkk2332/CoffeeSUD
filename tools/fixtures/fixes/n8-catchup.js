// N8: an area that wakes (PASSIVE to ACTIVE) catches up: Schedule in place, Sleeper by the hour, Mobile a few
// steps, Patroller the steps it missed, Prop_ClosedDayNight home or at work, FUNCTION_PROG CATCHUP, a JavaScript
// catchUp(); the tag NOCATCHUP and CATCHUP=OFF; and StdArea's own wake.
FIXFILE = "n8-catchup";
var ACU = CM.core.AreaCatchUp;
var PS = CM.Common.interfaces.PhyStats;
var State = CM.Areas.interfaces.Area.State;
function s(x) { return "" + x; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixn8Village");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
var rooms = [];
for (var i = 0; i < 4; i++) {
	var Q = CMClass.getLocale("StdRoom");
	Q.setRoomID("Fixn8Village#" + i);
	Q.setArea(X);
	Q.setDisplayText("Fixn8 room " + i);
	rooms.push(Q);
}
// a lane, west to east: 0 - 1 - 2 - 3
var EAST = CMLib.directions().getGoodDirectionCode("east"), WEST = CMLib.directions().getGoodDirectionCode("west");
function link(a, b) {
	a.rawDoors()[EAST] = b; a.setRawExit(EAST, CMClass.getExit("StdOpenDoorway"));
	b.rawDoors()[WEST] = a; b.setRawExit(WEST, CMClass.getExit("StdOpenDoorway"));
}
link(rooms[0], rooms[1]); link(rooms[1], rooms[2]); link(rooms[2], rooms[3]);
function where(m) { var R = m.location(); return R == null ? "nowhere" : s(R.roomID()); }
function villager(name, at) {
	var m = newMob("GenMob", rooms[at]);
	m.setName(name);
	m.setStartRoom(rooms[at]);
	return m;
}
var C = X.getTimeObj();
var wasHour = C.getHourOfDay();
var night = -1, day = -1;
for (var h = 0; h < C.getHoursInDay(); h++) { C.setHourOfDay(h); var t = s(C.getTODCode()); if (t == "NIGHT" && night < 0) night = h; if (t == "DAY" && day < 0) day = h; }
C.setHourOfDay(night);

// the miller's day is at the mill, his night at home
var miller = villager("Fixn8miller", 0);
var S = CMClass.getBehavior("Schedule");
S.setParms("DAY: Fixn8Village#3; NIGHT: HOME");
miller.addBehavior(S);
// a sleeper by night, up by day
var watch = villager("Fixn8watch", 1);
var SL = CMClass.getBehavior("Sleeper");
SL.setParms("SLEEPS=NIGHT");
watch.addBehavior(SL);
// a wanderer at the dead end
var dog = villager("Fixn8dog", 3);
var MO = CMClass.getBehavior("Mobile");
MO.setParms("min=10 max=10 chance=100");
dog.addBehavior(MO);
// a patrol, a step every 5 ticks
var pat = villager("Fixn8patrol", 0);
var PA = CMClass.getBehavior("Patroller");
PA.setParms("min=5 max=5 chance=100;east;east;east");
pat.addBehavior(PA);
// a keeper who goes home when closed (at night)
var keeper = villager("Fixn8keeper", 2);
var CDN = CMClass.getAbility("Prop_ClosedDayNight");
CDN.setMiscText("HOME=Fixn8Village#0");
keeper.addNonUninvokableEffect(CDN);
// a script and a JavaScript property
var scripted = villager("Fixn8clerk", 1);
scriptOn(scripted, "FUNCTION_PROG CATCHUP\nmpsetvar FIXTEST FIXN8CU $g\n~\n");
var dir = new java.io.File(WorldFiles.worldDir(), "js/props");
dir.mkdirs();
var JF = new java.io.File(dir, "fixn8catch.js");
var w = new java.io.FileWriter(JF); w.write("function catchUp(host, ticks) { parms.CAUGHT = '' + ticks; }\n"); w.close();
var JP = CMClass.getAbility("Prop_JScript");
JP.setMiscText("SCRIPT=world:js/props/fixn8catch.js");
scripted.addNonUninvokableEffect(JP);
clearVar("FIXN8CU");

// by day, a wake 15 ticks long
C.setHourOfDay(day);
var n = ACU.wake(X, 15);
check("told", n >= 7, "told " + n);
check("schedule", where(miller) == "Fixn8Village#3", "the miller is at " + where(miller));
check("sleeper-up", !CMLib.flags().isSleeping(watch), "the watch sleeps by day");
check("mobile", where(dog) == "Fixn8Village#2", "the dog is at " + where(dog));
check("patroller", where(pat) == "Fixn8Village#3", "the patrol is at " + where(pat));
check("closed-open", where(keeper) == "Fixn8Village#2", "the keeper is at " + where(keeper));
check("script", gvar("FIXN8CU") == "15", "CATCHUP got '" + gvar("FIXN8CU") + "'");
check("js", has(JP.text(), "CAUGHT=15"), s(JP.text()));
var lw = ACU.lastWake(X);
check("last", lw != null && lw[0] == 15 && lw[1] == n, lw == null ? "none" : (lw[0] + "/" + lw[1]));
function has(text, part) { return s(text).indexOf(part) >= 0; }

// by night: home and asleep
C.setHourOfDay(night);
ACU.wake(X, 15);
check("schedule-night", where(miller) == "Fixn8Village#0", "the miller is at " + where(miller));
check("sleeper-night", CMLib.flags().isSleeping(watch), "the watch is up at night");
check("closed-home", where(keeper) == "Fixn8Village#0", "the keeper is at " + where(keeper));

// a patrol that ticked lately has nothing to catch up
var at = where(pat);
PA.tick(pat, Tickable.TICKID_MOB);
var after = where(pat);
ACU.wake(X, 15);
check("patroller-ticking", where(pat) == after, "it jumped from " + after + " to " + where(pat));

// NOCATCHUP, and CATCHUP=OFF
X.addTag("NOCATCHUP");
check("tag", ACU.wake(X, 15) == 0, "it caught up with NOCATCHUP");
X.delTag("NOCATCHUP");
var old = CMProps.instance().getProperty("CATCHUP");
CMProps.instance().setProperty("CATCHUP", "OFF");
check("off", ACU.wake(X, 15) == 0, "it caught up with CATCHUP=OFF");
CMProps.instance().setProperty("CATCHUP", old == null ? "" : old);

// the area's own wake: passive, then a player comes in
C.setHourOfDay(day);
clearVar("FIXN8CU");
X.setAreaState(State.PASSIVE);
var f = null;
for (var c = X.getClass(); c != null && f == null; c = c.getSuperclass()) { try { f = c.getDeclaredField("passiveSince"); } catch (e) { } }
f.setAccessible(true);
check("passive", X.getAreaState() == State.PASSIVE && f.getLong(X) > 0, "since " + f.getLong(X));
f.setLong(X, f.getLong(X) - 20 * CMProps.getTickMillis());
var enter = CMClass.getMsg(M, rooms[0], null, CMMsg.MSG_ENTER, null);
X.okMessage(M, enter);
var lw2 = ACU.lastWake(X);
check("wakes", X.getAreaState() == State.ACTIVE && lw2 != null && lw2[0] >= 20 && gvar("FIXN8CU") == s(lw2[0]) && where(miller) == "Fixn8Village#3",
	s(X.getAreaState()) + " " + (lw2 == null ? "none" : lw2[0]) + " " + gvar("FIXN8CU") + " " + where(miller));

C.setHourOfDay(wasHour);
JF["delete"]();
[miller, watch, dog, pat, keeper, scripted].forEach(function (m) { m.destroy(); });
CMLib.map().obliterateMapArea(X);
done();
