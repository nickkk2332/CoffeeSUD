// Conditional descriptions are viewer-specific, nested, and never change authored text.
FIXFILE="pr-text";
var ST=CM.core.StoryText, area=CMClass.getAreaType("StdArea");area.setName("FixPresentationText");CMLib.map().addArea(area);
function room(n) {var r=CMClass.getLocale("StoneRoom");r.setRoomID(area.Name()+"#"+n);r.setArea(area);area.addProperRoom(r);return r;}
var r0=room(0),r1=room(1),who=newMob("GenMob",r0);who.setName("Fixtextreader");who.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));var ps=CMClass.getCommon("PartySession");ps.initialize(null,"0");ps.setMob(who);who.setSession(ps);CMLib.threads().deleteTick(who,-1);
var item=CMClass.getItem("GenItem"),exit=CMClass.getExit("GenExit"),objects=[item,exit,area],other=newMob("GenMob",r0);objects.push(other);r0.addItem(item);r0.setRawDoor(CM.core.Directions.EAST,r1);r0.setRawExit(CM.core.Directions.EAST,exit);
function text(s) {return String(ST.render(who,r0,s));}
try {
 VARS.setVar("FIXTEST","FIXPRBRIDGE","1");VARS.setVar("FIXTEST","FIXPRNUMBER","3");
 var authored='<VARIES><IFVAR FIXPRBRIDGE=1>open</IFVAR><ELSE>closed</ELSE></VARIES>';
 for(var i=0;i<objects.length;i++) {objects[i].setDescription(authored);check("description "+objects[i].ID(),String(objects[i].description(who))=="open"&&String(objects[i].description())==authored,"viewer text or authored text changed");}
 item.setDisplayText(authored);check("item display",String(item.displayText(who))=="open","raw display returned");
 check("numeric variable comparison",text('<VARIES><IFVAR FIXPRNUMBER GE 3>yes</IFVAR><ELSE>no</ELSE></VARIES>')=="yes","GE failed");
 check("nested identical tags",text('<VARIES><IFVAR FIXPRBRIDGE><IFVAR FIXPRNUMBER=3>nested</IFVAR></IFVAR></VARIES>')=="nested","nesting failed");
 check("unknown markup preserved",text('<VARIES><CUSTOM>literal</CUSTOM><IFVAR FIXPRBRIDGE>yes</IFVAR></VARIES>')=='<CUSTOM>literal</CUSTOM>yes',"literal markup lost");
 check("mask and else",text('<VARIES><MASK1 -NAME +Fixtextreader>named</MASK1><ELSE>other</ELSE></VARIES>')=="named","legacy MASK changed");
 var preview=new CM.core.TextPreview();preview.timeOfDay=CM.Common.interfaces.TimeClock.TimeOfDay.NIGHT.ordinal();var seen="";
 preview.run(new java.lang.Runnable({run:function(){seen=text('<VARIES><NIGHT>night</NIGHT><ELSE>day</ELSE></VARIES>');}}));check("legacy time preview",seen=="night",seen);
 ST.arriving(who,r0,true);check("first entry",text('<VARIES><FIRSTVISIT>first</FIRSTVISIT><ELSE>later</ELSE></VARIES>')=="first","first flag missing");ST.arriving(who,r0,false);check("later entry",text('<VARIES><FIRSTVISIT>first</FIRSTVISIT><ELSE>later</ELSE></VARIES>')=="later","first flag stale");
 who.addTattoo("FIXPRPARTY");check("party tattoo",text('<VARIES><PARTYTATTOO FIXPRPARTY>yes</PARTYTATTOO></VARIES>')=="yes","party tattoo absent");
 var engine=scriptOn(r0,"FUNCTION_PROG WATERDEPTH\n<SCRIPT>\nsetVar('FIXTEST','FIXPRCALLS',''+(Number(getVar('FIXTEST','FIXPRCALLS')||0)+1));\n</SCRIPT>\nreturn 42\n~\n");clearVar("FIXPRCALLS");
 var rendered=ST.look(new java.util.function.Supplier({get:function(){return text('Depth <CALL WATERDEPTH>, again <CALL WATERDEPTH>.');}}));
 check("computed description and cache",String(rendered)=="Depth 42, again 42."&&gvar("FIXPRCALLS")=="1",rendered+" calls="+gvar("FIXPRCALLS"));
 clearVar("FIXPRCALLS");ST.look(new java.util.function.Supplier({get:function(){text('<CALL WATERDEPTH>');return ST.look(new java.util.function.Supplier({get:function(){return text('<CALL WATERDEPTH>');}}));}}));check("nested look shares call budget",gvar("FIXPRCALLS")=="1","nested look restarted computed calls");
 scriptOn(r0,"FUNCTION_PROG WHOSAW\nreturn $n\n~\n");other.setName("Fixotherreader");var distinct=ST.look(new java.util.function.Supplier({get:function(){return ST.render(who,r0,'<CALL WHOSAW>')+'|'+ST.render(other,r0,'<CALL WHOSAW>');}}));check("computed text separate per viewer",String(distinct)=="Fixtextreader|Fixotherreader",distinct);
 clearVar("FIXPRCALLS");var many="";for(var n=0;n<40;n++)many+="<CALL WATERDEPTH "+n+">";
 ST.look(new java.util.function.Supplier({get:function(){return text(many);}}));check("computed calls bounded",gvar("FIXPRCALLS")=="32","CALL count "+gvar("FIXPRCALLS"));
 clearVar("FIXPRCALLS");text('<VARIES><IFVAR FIXPRBRIDGE=0><CALL WATERDEPTH></IFVAR></VARIES>');check("hidden branch does not call",gvar("FIXPRCALLS")=="","hidden CALL ran");
 item.setReadableText(authored);CMLib.flags().setReadable(item,true);var read=CMClass.getMsg(who,item,null,CMMsg.MSG_READ,null);CMLib.commands().handleBeingRead(read);var trailers=read.trailerMsgs();
 check("readable varies",trailers!=null&&trailers.size()>0&&String(trailers.get(0).targetMessage())=="open"&&String(item.readableText())==authored,"read did not render independently");
 var tome=CMClass.getItem("GenTome");tome.setName("FixPresentationTome");who.addItem(tome);tome.setReadableText('<PAGE TITLE="Bridge">'+authored+'</PAGE>');check("tome varies",String(tome.show(who,1)).indexOf("open")>=0&&String(tome.readableText()).indexOf("<VARIES>")>=0,"tome text not independent");
 scriptOn(tome,"FUNCTION_PROG PAGEDEPTH\nreturn 8\n~\n");tome.setReadableText('<PAGE TITLE="Depth">Depth <CALL PAGEDEPTH>.</PAGE>');check("tome computed host",String(tome.show(who,1)).indexOf("Depth 8.")>=0,"tome called room instead of itself");tome.destroy();
 var book=CMClass.getItem("GenBook");book.setName("FixPresentationBook");who.addItem(book);book.addRawContent("Author","::Bridge::"+authored);var chapter=book.DBRead(who,0,0,false,false);check("book readable varies",String(chapter.third).indexOf("open")>=0&&String(chapter.third).indexOf("<IFVAR")<0&&String(book.getRawContent(1)).indexOf("<IFVAR")>=0,"chapter not rendered independently");book.destroy();
 var journal=CMClass.getItem("GenJournal");journal.setName("FixPresentationJournal");who.addItem(journal);CMLib.database().DBWriteJournal(journal.Name(),"Author","ALL","Bridge",authored);var entry=journal.DBRead(who,journal.Name(),0,0,false,false);check("journal readable varies",String(entry.derivedBuildMessage()).indexOf("open")>=0&&String(entry.derivedBuildMessage()).indexOf("<IFVAR")<0,"journal body not rendered");journal.destroy();

} finally {CMLib.database().DBDeleteJournal("FixPresentationBook",null);CMLib.database().DBDeleteJournal("FixPresentationJournal",null);who.setSession(null);who.setPlayerStats(null);who.destroy();other.destroy();item.destroy();CMLib.map().delArea(area);area.destroy();VARS.setVar("FIXTEST","FIXPRBRIDGE","");VARS.setVar("FIXTEST","FIXPRNUMBER","");}
done();
