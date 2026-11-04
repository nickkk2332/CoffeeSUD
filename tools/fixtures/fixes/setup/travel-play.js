// Start a real passage and let the scheduler reach its first scene.
FIXFILE="travel-play";
var origin=CMLib.map().getRoom("FixTravelSave#0"),dest=CMLib.map().getRoom("FixTravelSave#2"),W=CM.core.Waypoints;
origin.bringMobHere(M,false);CMLib.beanCounter().setMoney(M,100);
W.use(M,origin);dest.bringMobHere(M,false);W.use(M,dest);origin.bringMobHere(M,false);
var h=newMob("Horse",origin);h.setName("a saved stable horse");h.setFollowing(M);
var keeper=origin.fetchInhabitant("Azure keeper"),nanny=keeper.fetchBehavior("Nanny");
check("stable before voyage",String(nanny.stable(keeper,M,"stable saved stable horse")).indexOf("in our care")>=0,"custody refused");
for(var i=0;i<2;i++) {var h=newMob("Horse",origin);h.setName("a matching saved travel horse");h.setFollowing(M);}
check("known destinations",W.known(M,origin.fetchEffect("Prop_Waypoint"))&&W.known(M,dest.fetchEffect("Prop_Waypoint")),"discovery missing");
var result=CM.core.Travel.beginJourney(M,origin,'DEST=FixTravelSave#2 VIA=FixTravelSave#1 DURATION=2 EVENTS="100%:HOLDTRIP"');
check("scheduled departure",result==null&&M.location().roomID()=="FixTravelSave#1",String(result));
done();
