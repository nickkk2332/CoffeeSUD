// Scene pauses, saved progress, duplicate mount names, stage hooks and cancellation.
FIXFILE="t-journeys";
var J=CM.Abilities.Properties.Prop_JourneyState, JT=CM.core.GameTime;
var ja=CMClass.getAreaType("StdArea");ja.setName("FixJourneys");CMLib.map().addArea(ja);
function jr(i) { var r=CMClass.getLocale("Plains");r.setRoomID(ja.Name()+"#"+i);r.setArea(ja);ja.addProperRoom(r);return r; }
var j0=jr(0),j1=jr(1),j2=jr(2),p=newMob("GenMob",j0),h1=newMob("Horse",j0),h2=newMob("Horse",j0),enemy=null;
p.setName("Fixjourneyleader");p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));var session=CMClass.getCommon("PartySession");session.initialize(null,"0");session.setMob(p);p.setSession(session);h1.setName("a matching journey horse");h2.setName("a matching journey horse");h1.setFollowing(p);h2.setFollowing(p);
try {
 var hooks=scriptOn(j0,"FUNCTION_PROG HOLDTRIP\nreturn PAUSE\n~\nJOURNEY_PROG ALL\nmpsetvar FIXTEST FIXJOURNEYLAST $g\n~\n");
 var error=J.begin(p,j0,'DEST='+j2.roomID()+' VIA='+j1.roomID()+' DURATION=2 EVENTS="100%:HOLDTRIP"');
 check("party departs",error==null&&p.location()==j1&&h1.location()==j1&&h2.location()==j1,"departure: "+error);
 runQueued(hooks);
 var state=p.fetchEffect("Prop_JourneyState");JT.cancelAdvance();
 var passengers=call(state,"passengers",[]);
 check("duplicate names keep identities",passengers.size()==3&&passengers.contains(h1)&&passengers.contains(h2),"passengers "+passengers.size());
 state.hour();runQueued(hooks);check("event pauses progress",String(state.status()).indexOf("1.0 game hours")>=0&&String(state.status()).indexOf("paused")>=0&&p.location()==j1,String(state.status()));
 var saved=String(state.text()),again=CMClass.getAbility("Prop_JourneyState");again.setMiscText(saved);p.delEffect(state);again.startTickDown(p,p,2147483647);
 check("saved journey progress",String(again.status()).indexOf("1.0 game hours")>=0&&String(again.status()).indexOf("paused")>=0&&String(again.text()).indexOf("HOLDTRIP")>=0,String(again.status()));
 again.resumeJourney();JT.cancelAdvance();again.hour();runQueued(hooks);
 check("final scene before arrival",p.location()==j1&&String(again.status()).indexOf("0.0 game hours")>=0,"arrived before final scene completed");
 again.resumeJourney();JT.cancelAdvance();
 check("whole party arrives",p.location()==j2&&h1.location()==j2&&h2.location()==j2&&p.fetchEffect("Prop_JourneyState")==null,"arrival separated companions");
 check("passenger markers cleared",p.fetchEffect("Prop_JourneyPassenger")==null&&h1.fetchEffect("Prop_JourneyPassenger")==null&&h2.fetchEffect("Prop_JourneyPassenger")==null,"stale passenger state");
 runQueued(hooks);check("journey stage trigger",gvar("FIXJOURNEYLAST").toUpperCase()=="ARRIVE",gvar("FIXJOURNEYLAST"));
 error=J.begin(p,j2,'DEST='+j0.roomID()+' VIA='+j1.roomID()+' DURATION=1');JT.cancelAdvance();state=p.fetchEffect("Prop_JourneyState");
 enemy=newMob("GenMob",j1);p.setVictim(enemy);state.tick(p,Tickable.TICKID_MOB);
 check("combat pauses journey",String(state.status()).indexOf("paused")>=0&&!JT.isAdvancing(),String(state.status()));p.makePeace(true);enemy.makePeace(true);
 state.resumeJourney();JT.cancelAdvance();state.cancelJourney();
 check("cancel stays at transit",p.location()==j1&&p.fetchEffect("Prop_JourneyState")==null&&h1.fetchEffect("Prop_JourneyPassenger")==null,"cancel warped or left active state");
 var all=new java.util.ArrayList();all.add(p);all.add(h1);all.add(h2);CM.core.Travel.transfer(p,all,j0,false);
 var exit=CMClass.getExit("GenExit");j0.setRawDoor(CM.core.Directions.EAST,j2);j0.setRawExit(CM.core.Directions.EAST,exit);scriptOn(exit,"FUNCTION_PROG EXITHOLD\nreturn PAUSE\n~\n");
 error=J.begin(p,exit,'DEST='+j2.roomID()+' VIA='+j1.roomID()+' DURATION=1 EVENTS="100%:EXITHOLD"');JT.cancelAdvance();state=p.fetchEffect("Prop_JourneyState");
 var exitSave=String(state.text()),exitRestored=CMClass.getAbility("Prop_JourneyState");exitRestored.setMiscText(exitSave);p.delEffect(state);exitRestored.startTickDown(p,p,2147483647);exitRestored.hour();
 check("exit script host survives restore",String(exitRestored.status()).indexOf("paused")>=0&&String(exitRestored.text()).indexOf("<SOURCETYPE>EXIT</SOURCETYPE>")>=0,String(exitRestored.status()));exitRestored.cancelJourney();
} finally { JT.cancelAdvance();if(enemy!=null)enemy.destroy();p.setSession(null);p.setPlayerStats(null);p.destroy();h1.destroy();h2.destroy();CMLib.map().delArea(ja);ja.destroy(); }
done();
