// ST18: RIDE_PROG fires when anyone mounts the scripted rideable,
// DISMOUNT_PROG when anyone gets off it, and REMOVING_PROG when the scripted
// mob removes something; RIDING_PROG stays the host's own mounting.
FIXFILE = "st18-ride-triggers";
var R = M.location();
M.setRiding(null);
var raft = CMClass.getItem("GenRideable");
raft.setName("a raft"); raft.setDisplayText("A raft floats here.");
R.addItem(raft);
var S = scriptOn(raft, "RIDE_PROG all\nmpsetvar FIXTEST FIXRIDE $n\n~\nDISMOUNT_PROG all\nmpsetvar FIXTEST FIXDISMOUNT $n\n~\nRIDING_PROG all\nmpsetvar FIXTEST FIXRIDING $n\n~\n");
clearVar("FIXRIDE"); clearVar("FIXDISMOUNT"); clearVar("FIXRIDING");
var mount = CMClass.getMsg(M, raft, null, CMMsg.MSG_MOUNT, "<S-NAME> board(s) <T-NAMESELF>.");
if (R.okMessage(M, mount)) R.send(M, mount);
runQueued(S);
check("ride-prog", gvar("FIXRIDE") == M.Name(), "FIXRIDE='" + gvar("FIXRIDE") + "'");
check("riding-prog-host-only", gvar("FIXRIDING") == "", "RIDING_PROG fired for someone else's mounting");
var off = CMClass.getMsg(M, raft, null, CMMsg.MSG_DISMOUNT, "<S-NAME> get(s) off <T-NAMESELF>.");
if (R.okMessage(M, off)) R.send(M, off);
runQueued(S);
check("dismount-prog", gvar("FIXDISMOUNT") == M.Name(), "FIXDISMOUNT='" + gvar("FIXDISMOUNT") + "'");
M.setRiding(null);
raft.destroy();
// a mob that removes its hat
var guard = newMob("GenMob", R);
guard.setName("a test guard");
var hat = CMClass.getArmor("GenArmor");
hat.setName("a felt hat");
hat.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_HEAD);
guard.addItem(hat);
hat.wearAt(CM.Items.interfaces.Wearable.WORN_HEAD);
var GS = scriptOn(guard, "REMOVING_PROG all\nmpsetvar FIXTEST FIXREMOVING $o\n~\n");
clearVar("FIXREMOVING");
var rem = CMClass.getMsg(guard, hat, null, CMMsg.MSG_REMOVE, "<S-NAME> remove(s) <T-NAMESELF>.");
if (R.okMessage(guard, rem)) R.send(guard, rem);
runQueued(GS);
check("removing-prog", gvar("FIXREMOVING") == "a felt hat", "FIXREMOVING='" + gvar("FIXREMOVING") + "'");
guard.destroy();
done();
