// Return resets, in-place scaling state, and MovingRoom overlays that never export.
FIXFILE="t-instances";
var IT=CM.core.GameTime, now=IT.now(), ia=CMClass.getAreaType("StdArea");ia.setName("FixTravelInstances");CMLib.map().addArea(ia);CMLib.database().DBCreateArea(ia);
function ir(i,title) { var r=CMClass.getLocale(i==3?"StoneRoom":"StdRoom");r.setRoomID(ia.Name()+"#"+i);r.setArea(ia);r.setDisplayText(title);r.setDescription("Authored "+title+".");ia.addProperRoom(r);CMLib.database().DBCreateRoom(r);return r; }
var i1=ir(1,"Reset hall"),i2=ir(2,"Do not reset"),car=ir(3,"Mine lift"),pip=newMob("GenMob",M.location());pip.setName("Fixinstanceleader");pip.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));pip.basePhyStats().setLevel(20);pip.recoverPhyStats();
var reset=CMClass.getBehavior("ResetWhole"),scaling=CMClass.getAbility("InstanceArea"),moving=null;
try {
 var sentinel=newMob("GenMob",i1);sentinel.setName("a fix instance sentinel");sentinel.basePhyStats().setLevel(2);sentinel.recoverPhyStats();sentinel.setStartRoom(i1);CMLib.database().DBUpdateMOBs(i1);
 var skipped=CMClass.getItem("GenItem");skipped.setName("a skipped marker");i2.addItem(skipped);
 var junk=CMClass.getItem("GenItem");junk.setName("a reset marker");i1.addItem(junk);
 scaling.setMiscText("ID=FIXTRAVEL INPLACE=true LEVELADJ=@x1 PREFIX=seasoned");ia.addNonUninvokableEffect(scaling);
 reset.setParms('ONREENTRY=true MINAWAY=2 SKIPROOMS="-LOCATION \'+ROOMSTR(Do not reset)\'" RESETMSG="Fresh tracks return."');ia.addBehavior(reset);
 reset.setStat("STATE",'<RESETWHOLE AGO="'+CMProps.getMillisPerMudHour()+'" AWAY="TRUE" />');
 var selection=call(reset,"rooms",[ia,false]);check("reset room selection",selection.contains(i1)&&!selection.contains(i2),"selected "+selection.size()+" rooms; hall="+selection.contains(i1)+" skip="+selection.contains(i2));
 var entry=CMClass.getMsg(pip,i1,null,CMMsg.MSG_ENTER,null);CM.core.Travel.entering(pip,pip.location(),i1);
 check("minimum absence",i1.isContent(junk),"reset too early");
 reset.setStat("STATE",'<RESETWHOLE AGO="'+3*CMProps.getMillisPerMudHour()+'" AWAY="TRUE" />');
 var gate=CMClass.getAbility("Prop_ReqEntry");gate.setMiscText("-TATTOO +FIXRETURNENTRY");i1.addNonUninvokableEffect(gate);
 var party=new java.util.ArrayList();party.add(pip);
 check("refused entry does not reset",!CM.core.Travel.transfer(pip,party,i1,false)&&i1.isContent(junk),"blocked entry changed the world");i1.delEffect(gate);
 check("committed entry",CM.core.Travel.transfer(pip,party,i1,false),"return refused");
 var scaled=i1.fetchInhabitant(0);
 check("reset on return",junk.amDestroyed()&&scaled!=null&&scaled!=sentinel,"return did not restore content");
 check("skip rooms",i2.isContent(skipped)&&!skipped.amDestroyed(),"skipped room reset");
 check("in-place scaling",scaled!=null&&scaled.phyStats().level()==20&&scaled.location().getArea()==ia,"level "+(scaled==null?"none":scaled.phyStats().level()));
 var ws=java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldState");
 var state=String(callTyped(null,ws,"statesXML",["PhysicalAgent"],[ia]));
 check("ability runtime state separate",state.indexOf("<ASTATE")>=0&&state.indexOf('LEVEL="20"')>=0&&String(scaling.text()).indexOf("READY=")<0,state);
 var restored=CMClass.getAbility("InstanceArea");restored.setMiscText(scaling.text());restored.restoreTravelState(scaling.travelState());
 check("in-place state restored",String(restored.travelState()).indexOf('READY="true"')>=0&&String(restored.travelState()).indexOf('LEVEL="20"')>=0,String(restored.travelState()));
 check("absence state retained",String(reset.getStat("STATE")).indexOf("AGO=")>=0&&String(reset.getStat("STATE")).indexOf("AWAY=")<0,"absence state lost");
 var xml='<LIFT><STOPS><STOP1>'+i1.roomID()+'</STOP1><STOP2>'+i2.roomID()+'</STOP2></STOPS>';
 function block(direction) {return '<'+direction+'><TRAVELDIRECTION>up</TRAVELDIRECTION><DOORSDIRECTION>up</DOORSDIRECTION><INSIDE><ARRIVALINFO>The lift arrives.</ARRIVALINFO><DEPARTINFO>The lift leaves.</DEPARTINFO></INSIDE><OUTSIDE><ARRIVALINFO>The lift opens.</ARRIVALINFO><DEPARTINFO>The lift closes.</DEPARTINFO></OUTSIDE></'+direction+'>';}
 xml+=block('NORMALDIRECTION')+block('REVERSEDIRECTION')+'<ROOMDESCRIPTIONS>';
 function desc(direction) {return '<'+direction+'><INSIDE><DOOROPENED>FIXLIFT_OPEN</DOOROPENED><DOORCLOSED>FIXLIFT_MOVING</DOORCLOSED></INSIDE><OUTSIDE><DOOROPENED>FIXLIFT_BOARD</DOOROPENED><DOORCLOSED>FIXLIFT_AWAY</DOORCLOSED></OUTSIDE></'+direction+'>';}
 xml+=desc('NORMALDIRECTION')+desc('REVERSEDIRECTION')+'</ROOMDESCRIPTIONS><LINEPRINTNAME>Mine Line</LINEPRINTNAME><DISPLOC>true</DISPLOC></LIFT>';
 var parms='WAIT=2 TRAVEL=3 ROUTE="'+xml+'"';moving=CMClass.getBehavior("MovingRoom");moving.setParms(parms);car.addBehavior(moving);CM.Behaviors.MovingRoom.restoreAll();
 check("temporary boarding links",car.getRoomInDir(CM.core.Directions.UP)==i1&&i1.getRoomInDir(CM.core.Directions.DOWN)==car&&car.rawDoors()[CM.core.Directions.UP]==null,"boarding link missing or authored; stops="+field(moving,"listOfRooms")+" info="+field(moving,"roomInfos")+" messages="+field(moving,"messageInfo")+" attached="+moving.isAttached()+" state="+moving.getStat("STATE")+" raw="+car.rawDoors()[CM.core.Directions.UP]+" temporary="+CM.core.Travel.temporaryDoor(car,CM.core.Directions.UP));
 var exported=String(CMLib.coffeeMaker().getRoomShapeXML(car));
 check("overlay does not export",exported.indexOf('Prop_TransitView')<0&&exported.indexOf('<XDOOR>'+i1.roomID())<0&&String(car.description())=="Authored Mine lift.","runtime view or link exported");
 moving.copyOf();check("copy leaves live overlay",car.getRoomInDir(CM.core.Directions.UP)==i1,"copy unlinked original");
 IT.advance(2*CMProps.getTickMillis());moving.tick(car,Tickable.TICKID_ROOM_BEHAVIOR);
 check("departure removes links",car.getRoomInDir(CM.core.Directions.UP)==null&&i1.getRoomInDir(CM.core.Directions.DOWN)==null,"departure left links");
 IT.advance(CMProps.getTickMillis());var saved=String(moving.getStat("STATE")),again=CMClass.getBehavior("MovingRoom");again.setParms(parms);again.setStat("STATE",saved);car.delBehavior(moving);moving=again;car.addBehavior(moving);CM.Behaviors.MovingRoom.restoreAll();
 check("mid-route restored",car.getRoomInDir(CM.core.Directions.UP)==null&&String(moving.getStat("STATE")).indexOf('<STATUS>1</STATUS>')>=0,"restore docked early");
 IT.advance(2*CMProps.getTickMillis());moving.tick(car,Tickable.TICKID_ROOM_BEHAVIOR);
 check("arrival after remaining time",car.getRoomInDir(CM.core.Directions.UP)==i2&&String(moving.timetable()).indexOf(String(i2.displayText()))>=0,"arrival or timetable wrong");
 car.delBehavior(moving);moving=null;check("removal clears links",car.getRoomInDir(CM.core.Directions.UP)==null&&i2.getRoomInDir(CM.core.Directions.DOWN)==null,"removed route left links");
} finally { IT.cancelAdvance();IT.set(now);if(moving!=null)car.delBehavior(moving);pip.setPlayerStats(null);pip.destroy();CMLib.database().DBDeleteAreaAndRooms(ia);CMLib.map().delArea(ia);ia.destroy(); }
done();
