FIXFILE="travel-arrived";
var dest=CMLib.map().getRoom("FixTravelSave#2"),origin=CMLib.map().getRoom("FixTravelSave#0");
check("restored journey arrived",M.location()==dest&&M.fetchEffect("Prop_JourneyState")==null,"journey did not finish");
var horses=0;for(var e=dest.inhabitants();e.hasMoreElements();) {var h=e.nextElement();if(h.Name()=="a matching saved travel horse"&&h.amFollowing()==M&&h.fetchEffect("Prop_JourneyPassenger")==null)horses++;}
check("restored party arrived together",horses==2,"horses "+horses);
var keeper=dest.fetchInhabitant("Gullrock keeper"),nanny=keeper.fetchBehavior("Nanny");
var result=String(nanny.stable(keeper,M,"retrieve saved stable horse"));
check("remote stable retrieval after restore",result.indexOf("You retrieve")>=0&&dest.fetchInhabitant("saved stable horse").amFollowing()==M,result);
var before=CMLib.beanCounter().getTotalAbsoluteNativeValue(M);result=String(CM.core.Waypoints.travel(M,dest.fetchEffect("Prop_Waypoint"),"Azure Stone"));
check("saved waypoint travel",M.location()==origin&&result.indexOf("You travel to")==0&&CMLib.beanCounter().getTotalAbsoluteNativeValue(M)==before-1,result);
check("export kept authored description",String(CMLib.map().getRoom("FixTravelSave#3").description())=="FIXTRAVEL authored room 3","runtime lift text exported");
done();
