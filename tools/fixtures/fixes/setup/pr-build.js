FIXFILE='pr-build';
var area=CMClass.getAreaType('StdArea');area.setName('FixPresentationSave');var areaFirst=CMClass.getAbility('Prop_FirstEntry');areaFirst.setMiscText('TEXT="<VARIES><FIRSTVISIT>FIX_AREA_FIRST</FIRSTVISIT><ELSE>WRONG_AREA_FIRST</ELSE></VARIES>"');area.addNonUninvokableEffect(areaFirst);CMLib.map().addArea(area);CMLib.database().DBCreateArea(area);
var rooms=[];for(var i=0;i<3;i++){var r=CMClass.getLocale('StoneRoom');r.setRoomID(area.Name()+'#'+i);r.setArea(area);r.setDisplayText(['The archive','The throne room','The buried shrine'][i]);r.setDescription(i==1?'<VARIES><FIRSTVISIT>FIX_FIRST_VISIT</FIRSTVISIT><ELSE>FIX_RETURN_VISIT</ELSE></VARIES>':'A quiet hall.');area.addProperRoom(r);CMLib.database().DBCreateRoom(r);rooms.push(r);}
var d=CM.core.Directions;rooms[0].setRawDoor(d.EAST,rooms[1]);rooms[0].setRawExit(d.EAST,CMClass.getExit('Open'));rooms[1].setRawDoor(d.WEST,rooms[0]);rooms[1].setRawExit(d.WEST,CMClass.getExit('Open'));
var first=CMClass.getAbility('Prop_FirstEntry');first.setMiscText('TEXT="FIX_ONCE_ENTRY" TATTOO=FIXFIRSTENTRY');rooms[1].addNonUninvokableEffect(first);
scriptOn(rooms[0],'FUNCTION_PROG STORY\nMPNARRATE $n fix-presentation.txt#awakening PAUSE\n~\n');
for(var i=0;i<rooms.length;i++){CMLib.database().DBUpdateRoom(rooms[i]);CMLib.database().DBUpdateExits(rooms[i]);}
var dir=WorldFiles.worldDir().toPath();java.nio.file.Files.createDirectories(dir.resolve('lore'));java.nio.file.Files.createDirectories(dir.resolve('text'));
java.nio.file.Files.write(dir.resolve('lore/fix-saved.ini'),new java.lang.String('SAVEDENTRY=The Lost Crown|History|FIX_SAVED_LORE\nSAVEDPROFILE=Family History|People|FIX_PROFILE_LORE\n').getBytes('UTF-8'));
java.nio.file.Files.write(dir.resolve('text/fix-presentation.txt'),new java.lang.String('[awakening]\nFIX_NARRATE_ONE\n\nFIX_NARRATE_TWO\n\nFIX_NARRATE_THREE\n').getBytes('UTF-8'));
check('authored presentation',rooms[1].fetchEffect('Prop_FirstEntry')!=null,'property missing');done();
