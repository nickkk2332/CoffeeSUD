// S7, in builder mode: a bell-ringer whose script runs once, sets an alarm
// and a variable of its own, and tolls on a delay; and a room that wakes once
FIXFILE = "s7-build";
var R = M.location();
var ringer = newMob("GenMob", R);
ringer.setName("a bell-ringer");
ringer.setDisplayText("A bell-ringer waits here.");
var B = CMClass.getBehavior("Scriptable");
B.setParms("ONCE_PROG\nmpsetinternal SCOPE INDIVIDUAL\nmpsetvar $i TOLLS 1\nmpecho FIXS7ONCE the crypt doors slam shut!\nmpalarm 30 mpecho FIXS7ALARM the alarm sounds.\n~\nDELAY_PROG 40 40\nmpecho FIXS7DELAY the bell tolls.\n~\n");
ringer.addBehavior(B);
var RB = CMClass.getBehavior("Scriptable");
RB.setParms("ONCE_PROG\nmpecho FIXS7ROOM the room wakes.\n~\n");
R.addBehavior(RB);
CMLib.database().DBUpdateMOBs(R);
CMLib.database().DBUpdateRoom(R);
check("placed", ringer.fetchBehavior("Scriptable") != null, "no script");
done();
