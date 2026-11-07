// D-SP-1 and D-SP-2 (documentation audit): the save edited by tools/saveinspect.py is
// restored whole, with the edit, and a room added to the world after the save keeps
// what the world puts in it
FIXFILE = "audit-restore";
check("sp1-edit-kept", M.curState().getHitPoints() < M.maxState().getHitPoints(),
	"hit points " + M.curState().getHitPoints() + " of " + M.maxState().getHitPoints() + ": the edit made outside the game is gone");
var NR = CMLib.map().getRoom("New Area#7");
check("sp2-room", NR != null, "the room added to the world after the save is not there");
if (NR != null)
	check("sp2-kept", NR.numItems() >= 1, "the room added after the save was emptied by the restore");
done();
