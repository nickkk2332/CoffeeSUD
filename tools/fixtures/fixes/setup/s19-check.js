// S19, after the restore: in the same instance room, the lieutenant still
// dead, the instance's timer still running
FIXFILE = "s19-check";
var R = M.location();
var A = R.getArea();
var id = String(CMLib.map().getExtendedRoomID(R));
check("same-room", /^[0-9]+_Crypt#1$/.test(id), "in " + id);
var lichs = 0;
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a lich lieutenant") lichs++;
check("still-dead", lichs == 0, lichs + " lieutenants");
var timer = A.fetchEffect("InstanceArea");
var left = (timer == null) ? 0 : CMath.s_int(timer.getStat("TICKDOWN"));
check("timer", (left > 0) && (left < 300), "instance ticks left " + left);
var act = (timer == null) ? "" : String(timer.getStat("ACTIVITY"));
check("leader", act.indexOf("<LEADER>Raider</LEADER>") >= 0, "the instance's activity is " + act);
M.tell("FIXINFO crypt room " + id);
done();
