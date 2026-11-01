// S25, in builder mode: a room's script that compares with <, which the
// world files are to keep as CDATA, as it is
FIXFILE = "s25-script";
var R = otherRoom();
var B = CMClass.getBehavior("Scriptable");
B.setParms("GREET_PROG 100\nif var($n FIXCDATA < 5)\n  mpecho FIXCDATA small\nendif\n~\n");
R.addBehavior(B);
// as builder commands do: the database cache, then the world files at quit
CMLib.database().DBUpdateRoom(R);
check("scripted", R.fetchBehavior("Scriptable") != null, "no script");
done();
