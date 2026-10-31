// ST1: scripts that react to world-wide events hear them as soon as they are
// put on their host, without having heard anything else first.
FIXFILE = "st1-global-triggers";
var R2 = otherRoom();
check("other-room", R2 != null && R2 != M.location(), "no other room in the area");
var S = CMClass.getCommon("DefaultScriptingEngine");
S.setScript("LOGIN_PROG 100\nmpsetvar FIXTEST FIXLOGIN $n\n~\nLOGOFF_PROG 100\nmpsetvar FIXTEST FIXLOGOFF $n\n~\nLEVEL_PROG 100\nmpsetvar FIXTEST FIXLEVEL $n\n~\n");
R2.addScript(S);
clearVar("FIXLOGIN"); clearVar("FIXLOGOFF"); clearVar("FIXLEVEL");
// the room has heard nothing: a login, a quit and a level anywhere reach it
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LOGIN, CMClass.getMsg(M, null, null, CMMsg.MSG_LOGIN, null));
runQueued(S);
check("login-heard", gvar("FIXLOGIN") == M.Name(), "FIXLOGIN='" + gvar("FIXLOGIN") + "'");
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_QUIT, CMClass.getMsg(M, null, null, CMMsg.MSG_QUIT, null));
runQueued(S);
check("logoff-heard", gvar("FIXLOGOFF") == M.Name(), "FIXLOGOFF='" + gvar("FIXLOGOFF") + "'");
var lv = CMClass.getMsg(M, null, null, CMMsg.MSG_LEVEL, null);
lv.setValue(M.basePhyStats().level() + 1);
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LEVEL, lv);
runQueued(S);
check("level-heard", gvar("FIXLEVEL") == M.Name(), "FIXLEVEL='" + gvar("FIXLEVEL") + "'");
// a script put on as a behaviour registers too: on a mob in another room
var R3 = R2;
var host = CMClass.getMOB("GenMob");
host.setName("a listener");
host.bringToLife(R3, true);
var B = CMClass.getBehavior("Scriptable");
B.setParms("LOGIN_PROG 100\nmpsetvar FIXTEST FIXLOGINB $n\n~\n");
host.addBehavior(B);
clearVar("FIXLOGINB");
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LOGIN, CMClass.getMsg(M, null, null, CMMsg.MSG_LOGIN, null));
for (var i = 0; i < 4; i++) B.dequeResponses(null);
check("behaviour-login-heard", gvar("FIXLOGINB") == M.Name(), "FIXLOGINB='" + gvar("FIXLOGINB") + "'");
R2.delScript(S);
host.destroy();
done();
