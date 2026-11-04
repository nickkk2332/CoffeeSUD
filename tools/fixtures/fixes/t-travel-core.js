// Shared routing, costs, access, vessel copies and script commands.
FIXFILE="t-travel-core";
var T=CM.core.Travel, D=CM.core.Directions, GT=CM.core.GameTime;
var ta=CMClass.getAreaType("StdArea");ta.setName("FixTravelCore");CMLib.map().addArea(ta);
function tr(i,kind) { var r=CMClass.getLocale(kind||"Plains");r.setRoomID(ta.Name()+"#"+i);r.setArea(ta);ta.addProperRoom(r);return r; }
function tp(p,id,text) { var a=CMClass.getAbility(id);a.setMiscText(text);p.addNonUninvokableEffect(a);return a; }
function tl(a,b) { a.setRawDoor(D.EAST,b);a.setRawExit(D.EAST,CMClass.getExit("Open"));b.setRawDoor(D.WEST,a);b.setRawExit(D.WEST,CMClass.getExit("Open")); }
function tpc(name,r) { var p=CMClass.getMOB("GenMob");p.setName(name);p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));p.bringToLife(r,true);var session=CMClass.getCommon("PartySession");session.initialize(null,"0");session.setMob(p);p.setSession(session);CMLib.threads().deleteTick(p,-1);return p; }
var r0=tr(0),r1=tr(1,"Woods"),water0=tr(2,"WaterSurface"),water1=tr(3,"WaterSurface");tl(r0,r1);tl(water0,water1);
var p=tpc("Fixtraveller",r0),comp=tpc("Fixtravelcompanion",r0),ship=CMClass.getItem("GenSailingShip"),copies=[],crew=null;
try {
 check("travel help topics",CMLib.help().getHelpFile().getProperty("TRAVEL")!=null&&CMLib.help().getArcHelpFile().getProperty("PROP_JOURNEY")!=null,"missing player or builder help");
 check("forest not road",!T.drivable(r1,null),"forest is drivable without a road");
 var road=tp(r1,"Prop_Road",'TIME=2 NOTFOR="heavy caravan"');
 check("road keeps terrain",T.drivable(r1,null)&&r1.domainType()!=CM.Locales.interfaces.Room.DOMAIN_OUTDOORS_PLAINS,"road changed terrain or failed");
 var wagon=CMClass.getItem("GenCaravan");wagon.setName("a heavy caravan");check("road vehicle exclusion",!T.drivable(r1,wagon),"excluded vehicle passed");wagon.destroy();
 tp(r1,"Prop_TravelTime","TIME=60 MOUNTED=0.5 VEHICLE=0.7 NIGHT=1");
 check("travel time multiplier",T.stepMillis(p,r0,r1,r0.getExitInDir(D.EAST))==2*CMProps.getMillisPerMudHour(),"road multiplier ignored");
 var horse=newMob("Horse",r0);p.setRiding(horse);
 check("mounted time multiplier",T.stepMillis(p,r0,r1,r0.getExitInDir(D.EAST))==CMProps.getMillisPerMudHour(),"mounted multiplier ignored");p.setRiding(null);horse.destroy();
 var exitTime=tp(r0.getExitInDir(D.EAST),"Prop_TravelTime","TIME=15 NIGHT=1");
 check("exit time takes priority",T.stepMillis(p,r0,r1,r0.getExitInDir(D.EAST))==CMProps.getMillisPerMudHour()/2,"room overrode exit time");r0.getExitInDir(D.EAST).delEffect(exitTime);
 comp.setFollowing(p); GT.cancelAdvance();
 check("party walks",CMLib.tracking().walk(p,D.EAST,false,true)&&p.location()==r1&&comp.location()==r1,"party separated");
 var pending=java.lang.Class.forName("com.planet_ink.coffee_mud.core.GameTime").getDeclaredField("pending");pending.setAccessible(true);
 check("party time once",pending.getLong(null)==2*CMProps.getMillisPerMudHour(),"charged "+pending.getLong(null));GT.cancelAdvance();
 tp(water1,"Prop_Port",'NAME="Secret Cove" HIDDEN=true KNOWNMASK="-TATTOO +FIXCOVE"');
 check("hidden port",T.destination("Secret Cove",p)==null,"hidden name leaked");p.addTattoo("FIXCOVE");
 check("known port",T.destination("Secret Cove",p)==water1,"known port absent");
 ship.setName("the Fix Travel Gull");water0.addItem(ship);ship.dockHere(water0);CMLib.map().registerWorldObjectLoaded(ta,water0,ship);
 var path=T.route(water0,water1,ship);check("water route",path!=null&&path.size()==1&&path.get(0)==D.EAST,"bad water route");
 water0.getExitInDir(D.EAST).setDoorsNLocks(true,false,false,false,false,false);
 check("closed exit refused",T.route(water0,water1,ship)==null,"routed through closed door");water0.getExitInDir(D.EAST).setDoorsNLocks(false,true,false,false,false,false);
 ship.setStat("TICKSPERMOVE","7");ship.setStat("TICKSPERTURN","3");ship.setStat("WEATHERSPEED","false");
 var round=CMClass.getItem("GenSailingShip");round.setMiscText(ship.text());copies.push(round);
 check("pacing persists",String(round.getStat("TICKSPERMOVE"))=="7"&&String(round.getStat("TICKSPERTURN"))=="3"&&String(round.getStat("WEATHERSPEED"))=="false","pacing lost");
 var c1=ship.copyOf(),c2=ship.copyOf();copies.push(c1,c2);
 check("unique copied interiors",String(c1.getArea().Name())!=String(c2.getArea().Name())&&String(c1.getArea().Name())!=String(ship.getArea().Name()),"copy kept original interior IDs");
 ship.setStat("FIXEDINTERIOR","true");var fixed=ship.copyOf();copies.push(fixed);check("fixed interior opt in",String(fixed.getArea().Name())==String(ship.getArea().Name()),"fixed name changed");ship.setStat("FIXEDINTERIOR","false");
 CMLib.catalog().addNewBuilderTemplateObject(M.Name(),"FIXTRAVELSHIP",ship);
 var t1=CMLib.catalog().getBuilderTemplateObject(M.Name(),"FIXTRAVELSHIP"),t2=CMLib.catalog().getBuilderTemplateObject(M.Name(),"FIXTRAVELSHIP");copies.push(t1,t2);
 check("unique template interiors",String(t1.getArea().Name())!=String(t2.getArea().Name())&&String(t1.getArea().Name())!=String(ship.getArea().Name()),"template retained interior IDs");CMLib.catalog().deleteBuilderTemplateObject(M.Name(),"FIXTRAVELSHIP");
 var helm=tp(ship,"Prop_HelmRestrict",'NAVMASK="-PLAYERS" MESSAGE="Hands off the wheel."');
 check("passenger cannot steer",!ship.securityCheck(p),"player took helm");
 var deck=ship.getArea().getProperMap().nextElement();crew=newMob("GenMob",deck);crew.addBehavior(CMClass.getBehavior("Sailor"));
 check("crew can steer",ship.securityCheck(crew),"Sailor refused");
 var script=scriptedMob("FixTravelOrders","ONCE_PROG\nmpsail 'the Fix Travel Gull' TO 'Secret Cove'\nmpsetvar FIXTEST FIXTCOURSE $%COURSE('the Fix Travel Gull')%\nmpsetvar FIXTEST FIXTANCHOR $%ISANCHORED('the Fix Travel Gull')%\nmpanchor 'the Fix Travel Gull' DOWN\nmpsetvar FIXTEST FIXTROOM $%VESSELROOM('the Fix Travel Gull')%\n~\n");tickMob(script);
 check("navigation scripts",gvar("FIXTCOURSE").toUpperCase().indexOf("EAST")>=0&&gvar("FIXTANCHOR")=="false"&&ship.isAnchorDown()&&gvar("FIXTROOM")==String(water0.roomID()),gvar("FIXTCOURSE")+":"+gvar("FIXTROOM"));script.destroy();
 var functions=scriptedMob("FixTravelJS","ONCE_PROG\n<SCRIPT>\nsetVar('FIXTEST','FIXTJSROOM',VESSELROOM$('the Fix Travel Gull'));\nsetVar('FIXTEST','FIXTJSHULL',HULL$('the Fix Travel Gull'));\n</SCRIPT>\n~\n");tickMob(functions);
 check("JavaScript vessel functions",gvar("FIXTJSROOM")==String(water0.roomID())&&gvar("FIXTJSHULL")=="100",gvar("FIXTJSROOM")+":"+gvar("FIXTJSHULL"));functions.destroy();
 var cart=CMClass.getItem("GenRideable"),ox=newMob("Horse",r1);r1.addItem(cart);cart.setName("a fixhitch cart");ox.setName("a fixhitch ox");
 var hitcher=scriptedMob("FixHitchOrders","ONCE_PROG\nmphitch 'a fixhitch cart' 'a fixhitch ox'\n~\n");r1.bringMobHere(hitcher,false);tickMob(hitcher);
 check("script hitches cart",cart.riding()==ox,"cart not hitched");check("hitch loop refused",T.hitch(ox,cart)!=null,"riding cycle allowed");
 hitcher.destroy();cart.destroy();ox.destroy();
 CMLib.beanCounter().setMoney(p,100);CMLib.beanCounter().setMoney(comp,0);
 var group=new java.util.ArrayList();group.add(p);group.add(comp);
 check("leader pays group",T.fare(p,group,10,true,false,0,0,true)&&CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==80,"wrong party fare");
 check("each insufficient no partial charge",!T.fare(p,group,10,true,true,0,0,true)&&CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==80,"partial debit");
 var boarder=scriptedMob("FixBoardOrders","ONCE_PROG\nmpboard Fixtraveller 'the Fix Travel Gull'\n~\n");r1.bringMobHere(boarder,false);tickMob(boarder);
 check("script boards whole party",p.location()==deck&&comp.location()==deck,"boarding separated party");boarder.destroy();r1.bringMobHere(p,false);r1.bringMobHere(comp,false);
 var blocked=tr(4);tp(blocked,"Prop_ReqEntry","-TATTOO +FIXENTRY");var commits=0;
 var commit=new java.util.function.BooleanSupplier({getAsBoolean:function(){commits++;return true;}});
 check("blocked transfer keeps party and fare",!T.transfer(p,group,blocked,false,commit)&&p.location()==r1&&comp.location()==r1&&commits==0,"transfer bypassed entry gate or charged");
} finally { GT.cancelAdvance();if(crew!=null)crew.destroy();for(var i=0;i<copies.length;i++)copies[i].destroy();ship.destroy();p.setSession(null);comp.setSession(null);p.setPlayerStats(null);comp.setPlayerStats(null);p.destroy();comp.destroy();CMLib.map().delArea(ta);ta.destroy(); }
done();
