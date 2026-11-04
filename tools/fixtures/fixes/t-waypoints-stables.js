// Discovery, profile sharing, atomic waypoint cost, and cross-stable custody.
FIXFILE="t-waypoints-stables";
var W=CM.core.Waypoints, G=CM.core.GameTime, beforeTime=G.now();
var wa=CMClass.getAreaType("StdArea");wa.setName("FixWaypoints");CMLib.map().addArea(wa);
function wr(i) { var r=CMClass.getLocale("Plains");r.setRoomID(wa.Name()+"#"+i);r.setArea(wa);wa.addProperRoom(r);return r; }
function wp(host,text) { var a=CMClass.getAbility("Prop_Waypoint");a.setMiscText(text);host.addNonUninvokableEffect(a);return a; }
var w0=wr(0),w1=wr(1),w2=wr(2),wa1=CMClass.getCommon("DefaultPlayerAccount"),wa2=CMClass.getCommon("DefaultPlayerAccount");wa1.setAccountName("FixWayProfile");wa2.setAccountName("FixOtherWayProfile");
function wpc(name,r,account) { var p=newMob("GenMob",r);p.setName(name);p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));p.playerStats().setAccount(account);var session=CMClass.getCommon("PartySession");session.initialize(null,"0");session.setMob(p);p.setSession(session);CMLib.threads().deleteTick(p,-1);return p; }
var p=wpc("Fixwayleader",w0,wa1),c=wpc("Fixwaycompanion",w0,wa1),outsider=wpc("Fixwayoutsider",w1,wa2),keepers=[],horse=null;
try {
 var first=wp(w0,'NETWORK=ELVEN NAME="Low Stone" DISCOVER=ENTER SCOPE=PROFILE COST="mana 7" TIME=0');
 var second=wp(w1,'NETWORK=ELVEN NAME="High Stone" DISCOVER=TOUCH SCOPE=PROFILE COST="moves 2"');
 var secret=wp(w2,'NETWORK=ELVEN NAME="Forgotten Stone" DISCOVER=QUEST MASK="-TATTOO +FIXWAYQUEST"');
 var enter=CMClass.getMsg(p,w0,null,CMMsg.MSG_ENTER,null);first.executeMsg(w0,enter);
 check("entry discovery",W.known(p,first),"entry did not attune");
 check("profile sharing",W.known(c,first)&&!W.known(outsider,first),"discovery escaped its profile");
 check("unknown destinations hidden",String(W.list(p,first)).indexOf("High Stone")<0&&String(W.list(p,first)).indexOf("Forgotten Stone")<0,"undiscovered name leaked");
 w1.bringMobHere(p,false);W.use(p,w1);check("touch discovery",W.known(p,second),"touch did not attune");
 check("quest discovery gate",!W.discover(p,secret,true),"mask ignored");p.addTattoo("FIXWAYQUEST");check("quest award",W.discover(p,secret,true),"scripted award failed");
 w0.bringMobHere(p,false);c.setFollowing(p);p.maxState().setMana(100);p.curState().setMana(100);
 var result=String(W.travel(p,first,"High Stone"));
 check("party waypoint travel",result.indexOf("You travel to")==0&&p.location()==w1&&c.location()==w1,"party did not travel: "+result);
 check("one waypoint cost",p.curState().getMana()==93,"mana "+p.curState().getMana());
 w0.bringMobHere(p,false);w0.bringMobHere(c,false);p.curState().setMana(1);
 check("insufficient cost stays put",String(W.travel(p,first,"High Stone")).indexOf("cannot pay")>=0&&p.location()==w0&&c.location()==w0,"travelled without mana");
 var ritual=wp(wr(3),'NETWORK=ELVEN NAME="Ritual Stone" DISCOVER=RITUAL RITUAL=FIXMISSINGRITUAL');
 check("missing ritual function refuses attunement",String(W.attune(p,ritual)).indexOf("ritual is incomplete")>=0&&!W.known(p,ritual),"undefined ritual passed");
 var commandRoom=wr(4),commandStone=wp(commandRoom,'NETWORK=COMMAND NAME="Command Stone" DISCOVER=TOUCH');commandRoom.bringMobHere(p,false);
 CMClass.getCommand("Touch").execute(p,CM.core.CMParms.parse("TOUCH Command Stone"),0);
 check("touch room waypoint by name",W.known(p,commandStone),"TOUCH did not resolve the waypoint name");
 var useRoom=wr(5),useStone=wp(useRoom,'NETWORK=COMMAND NAME="Use Stone" DISCOVER=TOUCH');useRoom.bringMobHere(p,false);
 CMClass.getCommand("Use").execute(p,CM.core.CMParms.parse("USE Use Stone"),0);
 check("use room waypoint by name",W.known(p,useStone),"USE did not resolve the waypoint name");w0.bringMobHere(p,false);
 var master0=newMob("GenMob",w0),master1=newMob("GenMob",w1);keepers.push(master0,master1);
 var n0=CMClass.getBehavior("Nanny"),n1=CMClass.getBehavior("Nanny");n0.setParms('RATE=2 NAME="Low Livery" WATCHES="mounts" NETWORK=SISTER SENDFEE=5');n1.setParms('RATE=2 NAME="High Livery" WATCHES="mounts" NETWORK=SISTER SENDFEE=5');master0.addBehavior(n0);master1.addBehavior(n1);
 horse=newMob("Horse",w0);horse.setName("a fixway horse");horse.setFollowing(p);CMLib.beanCounter().setMoney(p,100);
 result=String(n0.stable(master0,p,"stable fixway horse"));
 check("explicit stable custody",result.indexOf("in our care")>=0&&horse.amFollowing()==null&&String(n0.getStat("STATE")).indexOf("fixway horse")>=0,result);
 check("network persists in parameters",String(n0.getParms()).indexOf('NETWORK="SISTER"')>=0,"network lost");
 var cloned=n0.copyOf();cloned.addPayment(p,7);check("custody copied independently",cloned.getPaidBy(p)==7&&n0.getPaidBy(p)==0,"stable copies share payments");
 G.advance(2*CMProps.getMillisPerMudHour());w1.bringMobHere(p,false);
 check("sister lists ownership",String(n1.stable(master1,p,"list")).indexOf("fixway horse")>=0,"network listing missing mount");
 check("other owner refused",String(n1.stable(master1,outsider,"retrieve fixway horse")).indexOf("No mount of yours")>=0&&horse.location()==w0,"another profile retrieved mount");
 result=String(n1.stable(master1,p,"retrieve fixway horse"));
 check("remote retrieval",horse.location()==w1&&horse.amFollowing()==p&&!n0.isDroppedOff(horse),result);
 check("keep plus send fee",CMLib.beanCounter().getTotalAbsoluteNativeValue(p)==91,"money "+CMLib.beanCounter().getTotalAbsoluteNativeValue(p));
} finally { G.cancelAdvance();G.set(beforeTime);if(horse!=null)horse.destroy();for(var i=0;i<keepers.length;i++)keepers[i].destroy();[p,c,outsider].forEach(function(m){m.setSession(null);m.setPlayerStats(null);m.destroy();});CMLib.map().delArea(wa);wa.destroy(); }
done();
