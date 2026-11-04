FIXFILE="f-conquest";
var A=CMClass.getAreaType("StdArea"), R=CMClass.getLocale("StdRoom"), C=CMClass.getCommon("DefaultClan");
A.setName("Fix Conquest County");CMLib.map().addArea(A);R.setArea(A);R.setRoomID("Fix Conquest County#1");A.addProperRoom(R);
C.setName("Fix Conquerors");C.setGovernmentID(0);C.setMinClanMembers(0);CMLib.clans().addClan(C);
var legal=CMClass.getBehavior("Conquerable");A.addBehavior(legal);
var guard=newMob("GenMob",R);guard.setStartRoom(R);guard.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_INTELLIGENCE,12);guard.recoverCharStats();
try {
 legal.recalculateControlPoints(A);
 legal.scriptControl(C.clanID(),"=1");
 var initial=legal.getControlPoints(C.clanID());legal.scriptControl(C.clanID(),"+1");
 check("control points",legal.getControlPoints(C.clanID())==initial+1,"points not adjusted");
 legal.scriptControl(C.clanID(),"RULE");
 check("scripted ruler",String(legal.rulingOrganization())==String(C.clanID()),"no ruler");
 check("ruler room mask",CMLib.masking().maskCheck('-RULER "+Fix Conquerors"',R,true)&&CMLib.masking().maskCheck('-RULER +*',guard,true),"ruler mask fails");
 var host=newMob("GenMob",R), S=scriptOn(host,"ONCE_PROG\nmpsetvar FIXTEST FIXAREACLAN $%AREACLAN('Fix Conquest County')%\nif areaclan('Fix Conquest County' == 'Fix Conquerors')\nmpsetvar FIXTEST FIXAREATEST yes\nendif\nmpconquer 'Fix Conquest County' 'Fix Conquerors' FREE\n~\n");tickMob(host);
 check("area functions",gvar("FIXAREACLAN")==String(C.clanID())&&gvar("FIXAREATEST")=="yes",gvar("FIXAREACLAN")+":"+gvar("FIXAREATEST"));
 check("freed",String(legal.rulingOrganization())==""&&!CMLib.masking().maskCheck('-RULER +*',R,true),"rule remains");host.destroy();
} finally {guard.destroy();R.destroy();CMLib.map().delArea(A);A.destroy();CMLib.clans().removeClan(C);}
done();
