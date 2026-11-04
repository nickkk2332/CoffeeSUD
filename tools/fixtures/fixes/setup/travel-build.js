// A small authored world with sister stables, waypoints, a coach and a lift route file.
FIXFILE="travel-build";
var a=CMClass.getAreaType("StdArea");a.setName("FixTravelSave");CMLib.map().addArea(a);CMLib.database().DBCreateArea(a);
var rooms=[];
for(var i=0;i<4;i++) {var r=CMClass.getLocale("StoneRoom");r.setRoomID(a.Name()+"#"+i);r.setArea(a);r.setDisplayText(["Azure Livery","A ferry cabin","Gullrock Livery","The mine lift"][i]);r.setDescription("FIXTRAVEL authored room "+i);a.addProperRoom(r);rooms.push(r);CMLib.database().DBCreateRoom(r);}
var d=CM.core.Directions;rooms[0].setRawDoor(d.EAST,rooms[2]);rooms[0].setRawExit(d.EAST,CMClass.getExit("Open"));rooms[2].setRawDoor(d.WEST,rooms[0]);rooms[2].setRawExit(d.WEST,CMClass.getExit("Open"));
for(var i=0;i<2;i++) {
 var r=rooms[i*2],keeper=newMob("GenMob",r);keeper.setName(i==0?"the Azure keeper":"the Gullrock keeper");keeper.setStartRoom(r);
 var nanny=CMClass.getBehavior("Nanny");nanny.setParms('RATE=2 NAME="'+r.displayText()+'" WATCHES="mounts" NETWORK=FIXTRAVELSAVE SENDFEE=5');keeper.addBehavior(nanny);
 var stone=CMClass.getAbility("Prop_Waypoint");stone.setMiscText('ID=FIXTRAVEL'+i+' NETWORK=FIXTRAVELSAVE NAME="'+(i==0?'Azure Stone':'Gullrock Stone')+'" DISCOVER=TOUCH COST="gold 1"');r.addNonUninvokableEffect(stone);
}
scriptOn(rooms[0],"FUNCTION_PROG HOLDTRIP\nmpechoat $n FIXTRAVEL_PAUSED\nreturn PAUSE\n~\n");
var coach=CMClass.getItem("GenRideable");coach.setName("the Fixtravel coach");coach.setStat("RIDEBASIS","LAND_BASED");rooms[0].addItem(coach);
var line=CMClass.getBehavior("TransportLine");line.setParms('STOPS="'+rooms[0].roomID()+';'+rooms[2].roomID()+'" WAIT=100000 ROUND=BACK FARE=2 PARTY=LEADER');coach.addBehavior(line);
var xml='<SAVELIFT><STOPS><STOP1>'+rooms[0].roomID()+'</STOP1><STOP2>'+rooms[2].roomID()+'</STOP2></STOPS>';
for(var i=0;i<2;i++) {var direction=i==0?'NORMALDIRECTION':'REVERSEDIRECTION';xml+='<'+direction+'><TRAVELDIRECTION>up</TRAVELDIRECTION><DOORSDIRECTION>up</DOORSDIRECTION><INSIDE><ARRIVALINFO>FIXLIFT arrives.</ARRIVALINFO><DEPARTINFO>FIXLIFT departs.</DEPARTINFO></INSIDE><OUTSIDE><ARRIVALINFO>FIXLIFT opens.</ARRIVALINFO><DEPARTINFO>FIXLIFT closes.</DEPARTINFO></OUTSIDE></'+direction+'>';}
xml+='<ROOMDESCRIPTIONS>';
for(var i=0;i<2;i++) {var direction=i==0?'NORMALDIRECTION':'REVERSEDIRECTION';xml+='<'+direction+'><INSIDE><DOOROPENED>FIXLIFT_OPEN</DOOROPENED><DOORCLOSED>FIXLIFT_IN_SHAFT</DOORCLOSED></INSIDE><OUTSIDE><DOOROPENED>FIXLIFT_BOARD</DOOROPENED><DOORCLOSED>FIXLIFT_AWAY</DOORCLOSED></OUTSIDE></'+direction+'>';}
xml+='</ROOMDESCRIPTIONS><LINEPRINTNAME>Fixtravel Mine Line</LINEPRINTNAME><ROOMPRINTNAME>Mine car</ROOMPRINTNAME><DISPLOC>true</DISPLOC></SAVELIFT>';
var dir=WorldFiles.worldDir().toPath().resolve("routes");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("fixtravel-lift.xml"),new java.lang.String(xml).getBytes(java.nio.charset.StandardCharsets.UTF_8));
var lift=CMClass.getBehavior("MovingRoom");lift.setParms("ROUTE=fixtravel-lift.xml WAIT=1 TRAVEL=100000");rooms[3].addBehavior(lift);CM.core.Travel.restoreRoutes();
for(var i=0;i<rooms.length;i++) {CMLib.database().DBUpdateRoom(rooms[i]);CMLib.database().DBUpdateExits(rooms[i]);CMLib.database().DBUpdateMOBs(rooms[i]);CMLib.database().DBUpdateItems(rooms[i]);}
check("authored route file",java.nio.file.Files.exists(dir.resolve("fixtravel-lift.xml")),"route missing");
check("lift initially docked",rooms[3].getRoomInDir(d.UP)==rooms[0],"boarding overlay missing");
done();
