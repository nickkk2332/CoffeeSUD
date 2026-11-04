// Vessel pacing, return routes, saved line progress, group boarding and delayed portals.
FIXFILE="t-transport";
var TT=CM.core.GameTime, TD=CM.core.Directions, tn=TT.now(), area=CMClass.getAreaType("StdArea");area.setName("FixTransport");CMLib.map().addArea(area);
function troom(i) {var r=CMClass.getLocale("WaterSurface");r.setRoomID(area.Name()+"#"+i);r.setArea(area);r.setDisplayText("Water Stop "+i);area.addProperRoom(r);return r;}
var stops=[troom(0),troom(1),troom(2)],s=CMClass.getItem("GenSailingShip"),p=null,c=null;
function tperson(name) {var m=newMob("GenMob",stops[0]);m.setName(name);m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));var ss=CMClass.getCommon("PartySession");ss.initialize(null,"0");ss.setMob(m);m.setSession(ss);CMLib.threads().deleteTick(m,-1);return m;}
try {
 for(var i=0;i<2;i++){stops[i].setRawDoor(TD.EAST,stops[i+1]);stops[i].setRawExit(TD.EAST,CMClass.getExit("Open"));stops[i+1].setRawDoor(TD.WEST,stops[i]);stops[i+1].setRawExit(TD.WEST,CMClass.getExit("Open"));}
 s.setName("the Fixtransport ferry");stops[0].addItem(s);s.dockHere(stops[0]);CMLib.map().registerWorldObjectLoaded(area,stops[0],s);s.basePhyStats().setAbility(1);s.recoverPhyStats();s.setStat("WEATHERSPEED","false");s.setStat("TICKSPERMOVE","3");
 CM.core.Travel.sail(s,"EAST",null);var nav=Tickable.TICKID_AREA;s.tick(s,nav);s.tick(s,nav);
 check("vessel waits for pace",s.owner()==stops[0],"moved before three ticks");s.tick(s,nav);check("vessel moves at pace",s.owner()==stops[1],"did not move on third tick");
 stops[2].moveItemTo(s);s.dockHere(stops[2]);s.setCurrentCourse(new java.util.ArrayList());
 var line=CMClass.getBehavior("TransportLine"),parms='STOPS="'+stops[0].roomID()+';'+stops[1].roomID()+';'+stops[2].roomID()+'" WAIT=1 ROUND=BACK FARE=2 PARTY=LEADER PASSENGERONLY=true';line.setParms(parms);s.addBehavior(line);line.tick(s,Tickable.TICKID_MOB);TT.advance(CMProps.getTickMillis());line.tick(s,Tickable.TICKID_MOB);
 check("back route from endpoint",line.isMoving()&&String(line.timetable()).indexOf(String(stops[1].displayText()))>=0,"skipped middle stop");s.navigate(TD.WEST);line.tick(s,Tickable.TICKID_MOB);
 check("line arrival",s.owner()==stops[1]&&!line.isMoving()&&line.arrivals()==1&&s.isAnchorDown(),"arrival not recognised");
 var state=String(line.getStat("STATE")),restored=CMClass.getBehavior("TransportLine");restored.setParms(parms);restored.setStat("STATE",state);s.delBehavior(line);s.addBehavior(restored);line=restored;
 TT.advance(CMProps.getTickMillis());line.tick(s,Tickable.TICKID_MOB);s.navigate(TD.WEST);line.tick(s,Tickable.TICKID_MOB);line.tick(s,Tickable.TICKID_MOB);
 check("restored line continues once",s.owner()==stops[0]&&line.arrivals()==2&&!line.isMoving(),"route restarted or repeated arrival");
 p=tperson("Fixferrybuyer");c=tperson("Fixferrycompanion");c.setFollowing(p);CMLib.beanCounter().setMoney(p,100);CMLib.beanCounter().setMoney(c,0);
 var ticket=s.fetchEffect("Prop_TicketTaker"),board=CMClass.getMsg(p,s,null,CMMsg.MSG_MOUNT,null);
 check("party boarding permitted",ticket.okMessage(s,board),"leader fare refused");ticket.executeMsg(s,board);
 var follow=CMClass.getMsg(c,s,null,CMMsg.MSG_MOUNT,null);check("companion prepaid",ticket.okMessage(s,follow),"prepaid companion needs own money");ticket.executeMsg(s,follow);
 check("boarding charged once",CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==96,"money "+CMLib.beanCounter().getTotalAbsoluteNativeValue(p));
 var deck=s.getArea().getProperMap().nextElement();deck.bringMobHere(p,false);deck.bringMobHere(c,false);
 CM.core.Travel.disembarked(p,s);CM.core.Travel.disembarked(c,s);stops[0].bringMobHere(p,false);stops[0].bringMobHere(c,false);
 check("reboarding requires new fare",ticket.okMessage(s,board),"return fare refused");ticket.executeMsg(s,board);
 check("return fare charged",CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==92,"stale boarding receipt");
 s.delBehavior(line);check("removing line restores helm",s.fetchEffect("Prop_HelmRestrict")==null&&s.fetchEffect("Prop_TicketTaker")==null,"generated services remained");
 for(var q=0;q<stops.length;q++){var land=CMClass.getLocale("Plains");land.setRoomID(area.Name()+"#"+(q+10));land.setArea(area);land.setDisplayText("Portal Stop "+q);area.addProperRoom(land);if(q==0){land.bringMobHere(p,false);land.bringMobHere(c,false);}stops[q]=land;}
 var portal=CMClass.getBehavior("DelayedTransporter");portal.setParms('min=0 max=0 chance=100 PARTY=LEADER PERHEAD=true FARE=3 DEST=ORDER LOOK=false;'+stops[1].roomID()+';'+stops[2].roomID());stops[0].addBehavior(portal);CMLib.beanCounter().setMoney(p,100);
 portal.tick(stops[0],Tickable.TICKID_ROOM_BEHAVIOR);portal.tick(stops[0],Tickable.TICKID_ROOM_BEHAVIOR);
 check("delayed whole party",p.location()==stops[1]&&c.location()==stops[1],"follower left alone");check("delayed leader fare",CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==94,"wrong portal fare");
 stops[0].bringMobHere(p,false);stops[0].bringMobHere(c,false);portal.tick(stops[0],Tickable.TICKID_ROOM_BEHAVIOR);portal.tick(stops[0],Tickable.TICKID_ROOM_BEHAVIOR);
 check("ordered destinations",p.location()==stops[2]&&c.location()==stops[2]&&CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==88,"destination order or payment wrong");
 var coach=CMClass.getItem("GenRideable");coach.setName("a fixtaxi coach");coach.setStat("RIDEBASIS","LAND_BASED");coach.setRiderCapacity(2);stops[2].addItem(coach);
 var taxi=CMClass.getBehavior("TaxiBehavior");taxi.setParms('PARTY=LEADER;PETS=5;'+stops[1].roomID()+'=7');coach.addBehavior(taxi);
 var quote=Number(call(taxi,"getPrice",[p,stops[2],stops[1]]));
 check("taxi party quote",quote==14,"quote "+quote);
 var flags=CMLib.tracking().newFlags();call(taxi,"giveMerchandise",[p,stops[1],coach,stops[2],flags]);
 check("taxi boards party",p.riding()==coach&&c.riding()==coach,"taxi left a companion");
 call(taxi,"endTheRide",[coach]);check("taxi dismounts party",p.riding()==null&&c.riding()==null,"taxi retained a passenger");coach.destroy();
 var time=CMClass.getAbility("Prop_TravelTime");time.setMiscText("TIME=60 VEHICLE=1 NIGHT=1");stops[1].addNonUninvokableEffect(time);
 // A line must spend its step time before announcing arrival or starting a return leg.
 stops[0].moveItemTo(s);s.dockHere(stops[0]);deck.bringMobHere(p,false);s.setStat("TICKSPERMOVE","1");
 // These portal rooms are land; use an airship for this timing test.
 var timed=CMClass.getItem("GenAirShip");timed.setName("the Fixtimed ferry");stops[0].addItem(timed);timed.dockHere(stops[0]);CMLib.map().registerWorldObjectLoaded(area,stops[0],timed);timed.basePhyStats().setAbility(1);timed.recoverPhyStats();timed.setStat("WEATHERSPEED","false");
 stops[0].setRawDoor(TD.EAST,stops[1]);stops[0].setRawExit(TD.EAST,CMClass.getExit("Open"));stops[1].setRawDoor(TD.WEST,stops[0]);stops[1].setRawExit(TD.WEST,CMClass.getExit("Open"));
 // Air routes use outdoor terrain; portals above use Plains rooms.
 timed.getArea().getProperMap().nextElement().bringMobHere(p,false);
 var timedLine=CMClass.getBehavior("TransportLine");timedLine.setParms('STOPS="'+stops[0].roomID()+';'+stops[1].roomID()+'" WAIT=1 ROUND=BACK');timed.addBehavior(timedLine);timedLine.tick(timed,Tickable.TICKID_MOB);TT.advance(CMProps.getTickMillis());timedLine.tick(timed,Tickable.TICKID_MOB);timed.tick(timed,Tickable.TICKID_AREA);timedLine.tick(timed,Tickable.TICKID_MOB);
 check("line arrival waits for travel time",timed.owner()==stops[1]&&timedLine.isMoving()&&timedLine.arrivals()==0,"arrival announced before step time");
 var travelState=String(timed.getTravelStateXML());check("vessel saves remaining step time",travelState.indexOf('WAIT="'+CMProps.getMillisPerMudHour()+'"')>=0,travelState);
 timed.navigate(TD.WEST);check("vessel cannot move during step time",timed.owner()==stops[1],"moved again before time elapsed");
 TT.cancelAdvance();TT.advance(CMProps.getMillisPerMudHour());timedLine.tick(timed,Tickable.TICKID_MOB);
 check("line arrives after step time",!timedLine.isMoving()&&timedLine.arrivals()==1&&timed.isAnchorDown(),"line did not finish leg");stops[0].bringMobHere(p,false);timed.destroy();
 var portal2=CMClass.getBehavior("DelayedTransporter");portal2.setParms(portal.getParms());portal2.setStat("STATE",portal.getStat("STATE"));check("portal state round trip",String(portal2.getStat("STATE"))==String(portal.getStat("STATE")),"portal progress not saved");
} finally {TT.cancelAdvance();TT.set(tn);if(p!=null){p.setSession(null);p.setPlayerStats(null);p.destroy();}if(c!=null){c.setSession(null);c.setPlayerStats(null);c.destroy();}s.destroy();CMLib.map().delArea(area);area.destroy();}
done();
