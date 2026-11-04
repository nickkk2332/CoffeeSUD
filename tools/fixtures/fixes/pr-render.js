// Full room output, templates, gated exits, ambient tick selection and narration sections.
FIXFILE='pr-render';
function s(x){return String(x);}function has(x,p){return s(x).indexOf(p)>=0;}
var a=CMClass.getAreaType('StdArea');a.setName('FixRenderArea');CMLib.map().addArea(a);var r=CMClass.getLocale('StoneRoom');r.setRoomID(a.Name()+'#0');r.setArea(a);a.addProperRoom(r);r.setDisplayText('Visible chamber');r.setDescription('Visible carvings.');
var who=newMob('GenMob',r);who.setName('Fixrenderreader');who.setPlayerStats(CMClass.getCommon('DefaultPlayerStats'));CMLib.threads().deleteTick(who,-1);var session=new JavaAdapter(CM.Common.FakeSession,{isFake:function(){return false;}});session.initialize(null,'MEMORY');session.setMob(who);who.setSession(session);CMLib.players().changePlayersLocation(who,r);
var look=java.lang.Class.forName('com.planet_ink.coffee_mud.Libraries.interfaces.CommonCommands$LookView').getEnumConstants()[1];
function view(){return s(CMLib.commands().getFullRoomView(who,r,look,false));}
function prop(host,id,text){var p=CMClass.getAbility(id);p.setMiscText(text);host.addNonUninvokableEffect(p);return p;}
var oldH=CMProps.instance().getStr('ROOMHEADER'),oldF=CMProps.instance().getStr('ROOMFOOTER'),oldTalk=CMProps.instance().getStr('JOURNALTALK');
var scoreFile=new java.io.File(WorldFiles.worldDir(),'score.txt'),scoreOld=scoreFile.isFile()?java.nio.file.Files.readAllBytes(scoreFile.toPath()):null;
var textDir=new java.io.File(WorldFiles.worldDir(),'text');textDir.mkdirs();var narrative=new java.io.File(textDir,'fix-render.txt');
try {
 CMProps.instance().setProperty('ROOMHEADER','[%z] %r');CMProps.instance().setProperty('ROOMFOOTER','Mode %{TIMEMODE}');
 var text=view();check('header footer',has(text,'[FixRenderArea] Visible chamber')&&has(text,'Mode TURN'),text);
 who.basePhyStats().setSensesMask(0);who.phyStats().setSensesMask(0);r.basePhyStats().setDisposition(CM.Common.interfaces.PhyStats.IS_DARK);r.recoverPhyStats();
 var dark=prop(r,'Prop_DarkDescription','TITLE="Somewhere dark" DESC="Cold water laps at your knees."');r.recoverPhyStats();text=view();check('dark fallback',has(text,'Somewhere dark')&&has(text,'Cold water laps')&&!has(text,'Visible chamber')&&!has(text,'[FixRenderArea]'),text);
 r.delEffect(dark);r.setDescription('<VARIES><DARK>You feel the cold walls.</DARK><LIT>Visible carvings.</LIT></VARIES>');text=view();check('dark conditional fallback',has(text,'You feel the cold walls.')&&!has(text,'Visible carvings.'),text);
 prop(r,'Prop_Senses','SOUND="A drip echoes.";TOUCH="Rough walls."');check('senses in dark',CM.core.Scenery.sense(who,r,'SOUND')&&CM.core.Scenery.sense(who,r,'TOUCH'),'dark sense failed');
 r.basePhyStats().setDisposition(0);r.recoverPhyStats();
 VARS.setVar('FIXTEST','FIXSCORE','chapter4');java.nio.file.Files.write(scoreFile.toPath(),new java.lang.String('Chapter %{VAR FIXSCORE}\n<VARIES><IFVAR FIXSCORE=absent>HIDDEN_SCORE</IFVAR></VARIES>\n').getBytes('UTF-8'));
 check('score lines',has(CM.core.Presentation.score(who),'Chapter chapter4')&&!has(CM.core.Presentation.score(who),'HIDDEN_SCORE'),'score text wrong');
 check('score command',has(CMClass.getCommand('Score').getScore(who,''),'Chapter chapter4'),'SCORE missed file');
 check('new tags validate',CM.core.TextCheck.checkText('<VARIES><IFVAR FIXSCORE=chapter4><CALL DEPTH></IFVAR></VARIES>').isEmpty(),'CHECKTEXT rejects new tags');
 var mark=0;function out(){var all=s(field(session,'bout').toString()),o=all.substring(mark);mark=all.length;return o;}
 var emote=CMClass.getBehavior('Emoter');emote.setParms('min=1 max=1 chance=100 RETRY NOREPEAT=1;IFVAR FIXSCORE=absent SOUND WRONG_AMBIENT;IFVAR FIXSCORE=chapter4 SOUND AMBIENT_A;IFVAR FIXSCORE=chapter4 SOUND AMBIENT_B');r.addBehavior(emote);
 out();emote.tick(r,Tickable.TICKID_ROOM_BEHAVIOR);var one=out();emote.tick(r,Tickable.TICKID_ROOM_BEHAVIOR);var two=out();check('ambient retry',!has(one+two,'WRONG_AMBIENT')&&(has(one,'AMBIENT_A')||has(one,'AMBIENT_B')),one+two);check('ambient no repeat',has(one,'AMBIENT_A')&&has(two,'AMBIENT_B')||has(one,'AMBIENT_B')&&has(two,'AMBIENT_A'),one+two);
 var e=CM.core.Barks.parse('IDLE: IFVAR FIXSCORE=absent SOUND wrong | WEIGHT=3 IFVAR FIXSCORE=chapter4 SOUND right');check('sounder prefixes',s(CM.core.Barks.pick(e,who).text)=='right','Sounder ignored condition');
 var sounder=CMClass.getBehavior('Sounder');sounder.setParms('min=1 max=1;IDLE: ONCE SOUND ONCE_IDLE_SOUND');r.addBehavior(sounder);out();sounder.tick(r,Tickable.TICKID_ROOM_BEHAVIOR);var firstOnce=out();sounder.tick(r,Tickable.TICKID_ROOM_BEHAVIOR);check('sounder once per reader',has(firstOnce,'ONCE_IDLE_SOUND')&&!has(out(),'ONCE_IDLE_SOUND'),'idle ONCE did not play exactly once');
 CMProps.instance().setProperty('JOURNALTALK','true');var topics=new java.util.ArrayList();topics.add('vault');CM.core.NpcTopics.teach(null,r,topics,'The vault opens at midnight.');check('dialogue chronicle',has(CM.core.Chronicle.show(who,'People',false),'The vault opens at midnight.'),'reply lost');
 var scene=CMClass.getBehavior('Scene');scene.setParms('NAME=FIXJOURNALNOTE;NOTE "The old vault opens at dawn."');for(var n=0;n<4;n++)scene.tick(r,Tickable.TICKID_ROOM_BEHAVIOR);check('scene chronicle',has(CM.core.Chronicle.show(who,'Rumors',false),'The old vault opens at dawn.'),'scene NOTE missing');
 java.nio.file.Files.write(narrative.toPath(),new java.lang.String('[first]\nThe first story.\n\nThe second paragraph.\n[last]\nA different ending.\n').getBytes('UTF-8'));
 check('narration sections',has(CM.core.Narration.section('fix-render.txt#first'),'second paragraph')&&!has(CM.core.Narration.section('fix-render.txt#first'),'different ending'),'section boundaries wrong');
 check('narration path stays in world',s(CM.core.Narration.section('../lore/fix-saved.ini'))=='','path escaped');
 var background=CMClass.getCommon('PartySession');background.initialize(null,'0');var companion=CMClass.getMOB('GenMob');companion.setPlayerStats(CMClass.getCommon('DefaultPlayerStats'));companion.setSession(background);background.setMob(companion);CM.core.Narration.show(companion,r,'First paragraph.\n\nSecond paragraph.',true,'');check('background narration cannot hold input',!CM.core.Narration.waiting(background),'background companion holds world indefinitely');companion.setSession(null);background.setMob(null);companion.destroy();
 out();CM.core.Narration.show(who,r,CM.core.Narration.section('fix-render.txt#first'),false,'');check('narration no pause',has(out(),'second paragraph')&&!CM.core.Narration.waiting(session),'NOPAUSE queued input');
} finally {CMLib.players().changePlayersLocation(who,null);CMProps.instance().setProperty('ROOMHEADER',oldH);CMProps.instance().setProperty('ROOMFOOTER',oldF);CMProps.instance().setProperty('JOURNALTALK',oldTalk);if(scoreOld==null)scoreFile['delete()']();else java.nio.file.Files.write(scoreFile.toPath(),scoreOld);narrative['delete()']();who.setSession(null);who.setPlayerStats(null);who.destroy();CMLib.map().delArea(a);a.destroy();}
done();
