// Restoring a paused journey must retain each passenger, discovered stone and stable bill.
FIXFILE="travel-check";
var state=M.fetchEffect("Prop_JourneyState"),origin=CMLib.map().getRoom("FixTravelSave#0"),dest=CMLib.map().getRoom("FixTravelSave#2"),cabin=CMLib.map().getRoom("FixTravelSave#1"),car=CMLib.map().getRoom("FixTravelSave#3");
check("journey restored paused",state!=null&&String(state.status()).indexOf("1.0 game hours")>=0&&String(state.status()).indexOf("paused")>=0&&M.location()==cabin,state==null?"missing journey":String(state.status()));
var matches=0;for(var e=cabin.inhabitants();e.hasMoreElements();) {var h=e.nextElement();if(h.Name()=="a matching saved travel horse"&&h.amFollowing()==M&&h.fetchEffect("Prop_JourneyPassenger")!=null)matches++;}
check("same-name passengers restored",matches==2,"horses "+matches);
check("discovery restored",CM.core.Waypoints.known(M,origin.fetchEffect("Prop_Waypoint"))&&CM.core.Waypoints.known(M,dest.fetchEffect("Prop_Waypoint")),"waypoint tattoos lost");
var keeper=origin.fetchInhabitant("Azure keeper"),nanny=keeper.fetchBehavior("Nanny");
check("stable custody restored",String(nanny.stable(keeper,M,"list")).indexOf("Visit the stable")>=0&&String(nanny.getStat("STATE")).indexOf("saved stable horse")>=0,"custody lost");
var lift=car.fetchBehavior("MovingRoom");check("mid-shaft lift restored",String(lift.getStat("STATE")).indexOf("<STATUS>1</STATUS>")>=0&&car.getRoomInDir(CM.core.Directions.UP)==null,"lift restarted docked");
check("route file loaded",String(lift.timetable()).indexOf("Fixtravel Mine Line")>=0,"route unavailable");
var coach=origin.findItem(null,"Fixtravel coach"),line=coach==null?null:coach.fetchBehavior("TransportLine");
check("coach state restored",line!=null&&String(line.getStat("STATE")).indexOf('INIT="true"')>=0&&line.arrivals()==0,"line restarted or lost");
done();
