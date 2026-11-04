FIXFILE="travel-paused";
var state=M.fetchEffect("Prop_JourneyState");
check("scheduler reached first scene",state!=null&&String(state.status()).indexOf("1.0 game hours")>=0&&String(state.status()).indexOf("paused")>=0,state==null?"no journey":String(state.status()));
var lift=CMLib.map().getRoom("FixTravelSave#3").fetchBehavior("MovingRoom");
check("lift in shaft before save",String(lift.getStat("STATE")).indexOf("<STATUS>1</STATUS>")>=0,String(lift.getStat("STATE")));
done();
