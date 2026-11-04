// Presentation records, authoring properties and their command integrations.
FIXFILE="pr-presentation";
var SR=CM.core.StoryRecords, LB=CM.core.LoreBook, CH=CM.core.Chronicle, SC=CM.core.Scenery, VR=CM.core.ViewRules, PM=CM.core.PlayerMaps, AM=CM.core.Ambience;
function s(x){return String(x);} function has(x,y){return s(x).indexOf(y)>=0;}
function words(text){var l=new java.util.ArrayList();text.split(' ').forEach(function(w){l.add(w);});return l;}
var area=CMClass.getAreaType('StdArea');area.setName('FixPresentation');CMLib.map().addArea(area);
function room(n){var r=CMClass.getLocale('StoneRoom');r.setRoomID(area.Name()+'#'+n);r.setArea(area);area.addProperRoom(r);return r;}
var a=room(0),b=room(1),c=room(2);a.setRawDoor(CM.core.Directions.EAST,b);b.setRawDoor(CM.core.Directions.WEST,a);b.setRawDoor(CM.core.Directions.EAST,c);
var ex=CMClass.getExit('Open');a.setRawExit(CM.core.Directions.EAST,ex);b.setRawExit(CM.core.Directions.WEST,CMClass.getExit('Open'));b.setRawExit(CM.core.Directions.EAST,CMClass.getExit('Open'));
var who=newMob('GenMob',a);who.setName('Fixpresentreader');who.setPlayerStats(CMClass.getCommon('DefaultPlayerStats'));CMLib.threads().deleteTick(who,-1);
var session=CMClass.getCommon('FakeSession');session.initialize(null,'MEMORY');session.setMob(who);who.setSession(session);
var mark=0;function out(){var all=s(field(session,'bout').toString()),t=all.substring(mark);mark=all.length;return t;}
var item=CMClass.getItem('GenItem');item.setName('presentation tablet');a.addItem(item);
function prop(host,id,text){var p=CMClass.getAbility(id);p.setMiscText(text);host.addNonUninvokableEffect(p);return p;}
var loreDir=new java.io.File(WorldFiles.worldDir(),'lore');loreDir.mkdirs();var loreFile=new java.io.File(loreDir,'fix-presentation.ini');
java.nio.file.Files.write(loreFile.toPath(),new java.lang.String('FIXSUN=The Sundering|History|A lost kingdom.\nFIXBEAST=The Beast|Bestiary|It lives beneath the ice.\n').getBytes('UTF-8'));
var oldAuto=CMProps.instance().getStr('JOURNALAUTO'),oldFog=CMProps.instance().getStr('MAPFOG'),oldColors=CMProps.instance().getStr('COLORALIASES');
try {
 check('lore locked',!has(LB.show(who,''),'History')&&!has(LB.show(who,'FIXSUN'),'lost kingdom'),'locked entry leaked');
 CMProps.instance().setProperty('JOURNALAUTO','LORE');
 check('unlock',LB.unlock(who,'FIXSUN',false,item)&&who.findTattoo('LORE_FIXSUN')!=null&&!LB.unlock(who,'FIXSUN',false,item),'unlock not idempotent');
 check('lore categories',has(LB.show(who,''),'History (1)'),'missing category');
 check('lore unread',has(LB.show(who,'NEW'),'FIXSUN'),'new entry absent');
 check('lore read',has(LB.show(who,'The Sundering'),'lost kingdom')&&!has(LB.show(who,'NEW'),'FIXSUN'),'read state absent');
 check('lore journal',has(CH.show(who,'LORE',false),'Learned The Sundering'),'automatic lore missing');
 CH.add(who,'Clues','The vault opens to ash.',item);CH.add(who,'Notes','Remember the hermit.',who);
 check('journal search',has(CH.show(who,'vault',true),'The vault opens')&&!has(CH.show(who,'vault',true),'Remember the hermit'),'search wrong');
 check('journal source',has(CH.show(who,'Clues',false),'presentation tablet'),'source missing');
 check('journal delete',CH.delete(who,'2')&&!has(CH.show(who,'vault',true),'opens')&&!CH.delete(who,'2'),'delete wrong');
 var saved=s(who.fetchEffect('Prop_StoryRecords').text()),copy=CMClass.getMOB('GenMob');copy.setName('Fixpresentcopy');prop(copy,'Prop_StoryRecords',saved);
 check('records roundtrip',has(CH.show(copy,'Notes',false),'hermit')&&!LB.unread(copy,'FIXSUN'),'serialized state lost');copy.destroy();
 var script=scriptOn(item,'FUNCTION_PROG PRESENT\nMPLORE $n ADD FIXBEAST\nMPJOURNAL $n Rumors "Beware the beast"\n~\n');script.callFunction(item,'PRESENT','',who);
 check('script lore',LB.known(who,'FIXBEAST'),'MPLORE failed');check('script journal',has(CH.show(who,'Rumors',false),'Beware the beast'),'MPJOURNAL failed');
 prop(a,'Prop_Details','runes,carving=HIGHLIGHT Ancient ash runes. READ="Nine bells." TATTOO=FIXRUNE SETVAR=FIXREAD=1;secret=EXAMINEONLY The circled word.');
 out();check('detail lookup',SC.detail(who,'carving','LOOK')&&has(out(),'Ancient ash runes'),'look failed');
 check('detail clue',who.findTattoo('FIXRUNE')!=null&&s(CM.core.StoryText.variable(who.Name(),'FIXREAD'))=='1','first clue missing');
 out();check('detail read',SC.detail(who,'runes','READ')&&has(out(),'Nine bells.'),'read failed');
 check('examine only',!SC.detail(who,'secret','LOOK')&&SC.detail(who,'secret','EXAMINE'),'examine restriction wrong');
 check('detail highlight',has(SC.highlight(who,a,'The runes glow.'),'^Hrunes^N'),'highlight absent');
 out();CMClass.getCommand('Look').execute(who,words('LOOK runes'),0);check('look command',has(out(),'Ancient ash runes'),'command did not dispatch details');
 prop(ex,'Prop_Senses','SOUND="Water drips.";TOUCH="Nine studs; one loose.";TASTE="Salt." TATTOO=FIXSENSE');
 out();check('sense exit',SC.sense(who,ex,'TOUCH')&&has(out(),'Nine studs; one loose.')&&who.findTattoo('FIXSENSE')!=null,'sense/first clue failed');
 out();CMClass.getCommand('Listen').execute(who,words('LISTEN east'),0);check('listen exit',has(out(),'Water drips.'),'listen command failed');
 out();CMClass.getCommand('Touch').execute(who,words('FEEL east'),0);check('feel exit',has(out(),'Nine studs'),'feel command failed');
 var hidden=prop(item,'Prop_ShowWhen','MASK="-TATTOO +FIXSEE"');
 check('viewer visibility',!VR.allowed(item,who)&&!CMLib.flags().canBeSeenBy(item,who),'hidden item visible');
 var get=CMClass.getMsg(who,item,null,CMMsg.MSG_GET,null);check('interaction blocked',!hidden.okMessage(item,get),'GET bypassed mask');
 who.addTattoo('FIXSEE');check('viewer unlock',VR.allowed(item,who),'tattoo not honored');who.delTattoo('FIXSEE');
 hidden.setMiscText('MASK="-TATTOO +FIXSEE" HIDEMODE=HIDDEN');VR.discover(who,item);check('search discovery',VR.allowed(item,who),'hidden discovery failed');
 hidden.setMiscText('MASK="-TATTOO +FIXNEVER" HIDEMODE=UNSEEN');VR.discover(who,item);check('unseen cannot search',!VR.allowed(item,who),'UNSEEN discovered');
 item.delEffect(hidden);
 CMProps.instance().setProperty('MAPFOG','KNOWN');who.playerStats().addRoomVisit(a);
 check('map fog',!PM.known(who,b),'unvisited room known');PM.reveal(who,b,'X','Buried chest','r');
 check('map reveal',PM.known(who,b)&&!who.playerStats().hasVisited(b),'reveal counted as visit');
 var map=s(PM.render(who,a,3));check('map mark',has(map,'*')&&has(map,'X')&&has(map,'Buried chest')&&!has(map,'It lives'),'mark absent '+map);
 var glyph=prop(b,'Prop_MapGlyph','HIDE');check('map hide',!PM.known(who,b)&&!has(PM.render(who,a,3),'Buried chest'),'hidden room leaked');b.delEffect(glyph);
 var unmappable=prop(b,'Prop_RoomUnmappable','');b.recoverPhyStats();check('map unmappable',!PM.known(who,b),'unmappable revealed');b.delEffect(unmappable);b.recoverPhyStats();
 CMProps.instance().setProperty('MAPFOG','VISITED');check('visited fog',!PM.known(who,b),'revealed room under VISITED');CMProps.instance().setProperty('MAPFOG','ALL');check('all fog',PM.known(who,c),'ALL failed');
 CMClass.getCommand('AutoMap').execute(who,words('AUTOMAP SIZE 4'),0);check('map size',PM.size(who)==4,'size not saved');
 var reveal=prop(item,'Prop_MapReveal','ROOMS=FixPresentation#1-2 MARK=FixPresentation#2:Z:"Forgotten shrine"');item.executeMsg(item,CMClass.getMsg(who,item,null,CMMsg.MSG_READ,null));
 check('map item',has(PM.render(who,a,3),'Forgotten shrine'),'READ did not reveal mark');
 VARS.setVar(who.Name(),'FIXPROMPT','personal');VARS.setVar('FIXTEST','FIXGLOBAL','chapter3');
 var questVars=CMClass.getCommon('DefaultScriptingEngine');questVars.setVarScope('FIXPRQUEST');questVars.setVar('FIXPRHOST','FIXQUESTSTATUS','quest-stage');
 var prompt=s(CMLib.utensils().buildPrompt(who,'%{PVAR FIXPROMPT}/%{VAR FIXGLOBAL}/%{TIMEMODE}/%{LEADER}'));
 check('quest prompt',has(CMLib.utensils().buildPrompt(who,'%{QVAR FIXPRQUEST FIXQUESTSTATUS}'),'quest-stage'),'quest scope missed');
 check('prompt tokens',has(prompt,'personal/chapter3/TURN/')&&has(prompt,who.Name()),prompt);
 CMProps.instance().setProperty('COLORALIASES','LORE=##C8A060,DANGER=LIGHTRED+BOLD');
 check('alias expansion',s(CM.core.ColorAliases.expand('^{LORE}Ash^N',who))=='^##C8A060Ash^N','hex expansion');
 check('alias stripping',s(CM.core.CMStrings.removeColors('^{DANGER}Run!^N'))=='Run!'&&CM.core.CMStrings.lengthMinusColors('^{LORE}Ash^N')==3,'alias length');
 CM.core.ColorAliases.command(who,words('COLORSET LORE LIGHTGREEN'));check('alias override',has(CM.core.ColorAliases.expand('^{LORE}Ash',who),'^gAsh'),'override absent');
 check('ambient predicates',AM.fits(AM.parse('IFVAR FIXGLOBAL=chapter3 SOUND a bell'),who,a,a)&&!AM.fits(AM.parse('IFVAR FIXGLOBAL=absent SOUND a bell'),who,a,a),'predicate wrong');
 var line=AM.parse('ONCE WEIGHT=3 MASK="-NAME +Fixpresentreader" SOUND a bell');check('ambient parse',line.weight==3&&line.once&&s(line.kind)=='SOUND'&&s(line.text)=='a bell','parse wrong');
 check('ambient once',AM.fits(line,who,a,a),'first excluded');AM.heard(line,who);check('ambient repeat excluded',!AM.fits(line,who,a,a),'ONCE repeated');
 var emoter=CMClass.getBehavior('Emoter');emoter.setParms('min=1 max=1 chance=100 RETRY NOREPEAT=1;IFVAR FIXGLOBAL=absent SOUND wrong bell;IFVAR FIXGLOBAL=chapter3 SOUND right bell');a.addBehavior(emoter);
 out();check('listen emoter fallback',SC.sense(who,a,'SOUND')&&has(out(),'right bell'),'fallback missing');
 check('gated help',LB.help(who,'<MASK=-TATTOO +FIXHELP><LOCKED=Find the key><CATEGORY=History>Secret')=='Find the key','teaser not gated');who.addTattoo('FIXHELP');check('help unlock',LB.help(who,'<MASK=-TATTOO +FIXHELP><CATEGORY=History>Secret')=='Secret','metadata not removed');
 var account=CMClass.getCommon('DefaultPlayerAccount');who.playerStats().setAccount(account);LB.unlock(who,'FIXPROFILE',true,item);
 check('profile tattoo tests',who.findTattoo('LORE_FIXPROFILE')!=null&&CMLib.masking().maskCheck('-TATTOO +LORE_FIXPROFILE',who,true),'profile lore invisible to scripts or masks');
 var member=CMClass.getMOB('GenMob');member.setPlayerStats(CMClass.getCommon('DefaultPlayerStats'));member.playerStats().setAccount(account);check('profile sibling lore',member.findTattoo('LORE_FIXPROFILE')!=null,'sibling cannot test profile lore');member.playerStats().setAccount(null);member.destroy();who.playerStats().setAccount(null);
 var chest=CMClass.getItem('GenContainer'),book=CMClass.getItem('GenItem');chest.setName('a closed chest');book.setName('a stowed book');a.addItem(chest);a.addItem(book);book.setContainer(chest);chest.setDoorsNLocks(true,false,true,false,false,false);prop(book,'Prop_Details','buriedmargin=A secret margin.');
 check('closed container details',!SC.detail(who,'buriedmargin','LOOK'),'detail leaked through closed chest');chest.setDoorsNLocks(true,true,false,false,false,false);check('open container details',SC.detail(who,'buriedmargin','LOOK'),'open contents inaccessible');book.destroy();chest.destroy();
 a.delBehavior(emoter);var areaEmoter=CMClass.getBehavior('Emoter');areaEmoter.setParms('min=1000 max=1000;SOUND AREA_SOUNDS');area.addBehavior(areaEmoter);out();check('listen area fallback',SC.sense(who,a,'SOUND')&&has(out(),'AREA_SOUNDS'),'area ambience inaccessible');
 areaEmoter.setParms('min=1000 max=1000 MASK="-NAME +Nobody";SOUND MASKED_SOUND');check('listen honors mask',!SC.sense(who,a,'SOUND'),'masked sound leaked');
 var senses=who.phyStats().sensesMask();who.phyStats().setSensesMask(senses|CM.Common.interfaces.PhyStats.CAN_NOT_HEAR);check('listen respects hearing',!SC.sense(who,ex,'SOUND'),'deaf character heard sensory text');who.phyStats().setSensesMask(senses);
 check('varies help',has(CMLib.help().getHelpText('VARIES',M,true),'FIRSTVISIT')&&has(CMLib.help().getHelpText('VARIES',M,true),'FROMUNKOWN'),'builder help missing');
} finally {CMProps.instance().setProperty('JOURNALAUTO',oldAuto);CMProps.instance().setProperty('MAPFOG',oldFog);CMProps.instance().setProperty('COLORALIASES',oldColors);loreFile['delete()']();who.setSession(null);who.setPlayerStats(null);who.destroy();item.destroy();CMLib.map().delArea(area);area.destroy();}
done();
