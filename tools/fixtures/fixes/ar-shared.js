// Authoring reliability: quoted options, durable source identity, old keys and schema checks.
FIXFILE="ar-shared";
var AO=CM.core.AuthoringOptions,SC=CM.core.Scenery,AI=CM.core.AuthorIds,SR=CM.core.StoryRecords;
function ap(h,id,s){var a=CMClass.getAbility(id);a.setMiscText(s);h.addNonUninvokableEffect(a);return a;}
function has(x,s){return String(x).indexOf(s)>=0;}
var r=scratchRoom(),r2=scratchRoom(),p=newMob("GenMob",r),i=CMClass.getItem("GenItem");r.addItem(i);p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
try {
 check("quoted settings stay prose",String(SC.option('SOUND="A label says CHANCE=0" CHANCE=100',"CHANCE","bad"))=="100","quoted key escaped its value");
 check("escaped quotes and separators",String(SC.option('SOUND="He says \\"wait; then go\\"." CHANCE=100',"SOUND",""))=='He says "wait; then go".',"escape parsing");
 check("quoted flags stay prose",!SC.flag('READ="HIGHLIGHT EXAMINEONLY"',"HIGHLIGHT"),"quoted flag leaked");
 check("escaped statement quote",SC.statements('one="say \\"hi; friend\\"";two=ok').size()==2,"split quoted semicolon");
 check("clean preserves quoted metadata",has(SC.clean('description READ="TATTOO=WRONG" TATTOO=RIGHT'),'description')&&!has(SC.clean('description READ="TATTOO=WRONG" TATTOO=RIGHT'),'WRONG'),"metadata remained");
 var show=ap(i,"Prop_ShowWhen",'ID=RELIC MASK="-TATTOO +ARKEY" HIDEMODE=HIDDEN');
 CM.core.ViewRules.discover(p,i);i.setName("renamed relic");r.delItem(i);r2.addItem(i);show.setMiscText('ID=RELIC MASK="-TATTOO +OTHERKEY" HIDEMODE=HIDDEN');
 check("explicit discovery survives all edits",CM.core.ViewRules.allowed(i,p),"rename, move or prose changed discovery");
 show.setMiscText('ID=RELIC_V2 ALIASES=RELIC MASK="-TATTOO +OTHERKEY" HIDEMODE=HIDDEN');
 check("discovery ID alias migrates",CM.core.ViewRules.allowed(i,p),"old ID lost");
 i.delEffect(show);var clue=ap(i,"Prop_Senses",'TOUCH="old text" SETVAR=ARCLUE=first');SC.clue(p,i,clue.text(),"TOUCH");
 var uid=String(i.fetchEffect("Prop_AuthorIdentity").text());clue.setMiscText('TOUCH="new text" SETVAR=ARCLUE=second');i.setName("moved clue");r2.delItem(i);r.addItem(i);SC.clue(p,i,clue.text(),"TOUCH");
 check("implicit clue survives prose and movement",String(CM.core.StoryText.variable(p.Name(),"ARCLUE"))=="first",String(CM.core.StoryText.variable(p.Name(),"ARCLUE")));
 var copy=i.copyOf();check("identity serialized with authored object",copy.fetchEffect("Prop_AuthorIdentity")!=null&&String(copy.fetchEffect("Prop_AuthorIdentity").text())==uid,"copy lost saved metadata");copy.destroy();
 var legacy=String(SR.token("SCENERY_",CMLib.map().getExtendedRoomID(r)+":"+i.ID()+":"+i.Name()+":TASTE:"+'TASTE="salt" SETVAR=ARLEGACY=bad'));
 p.addTattoo(legacy);SC.clue(p,i,'TASTE="salt" SETVAR=ARLEGACY=bad',"TASTE");
 check("legacy clue marker migrates",String(CM.core.StoryText.variable(p.Name(),"ARLEGACY"))=="","legacy clue replayed");
 var invalid=CM.core.AuthoringSchema.check("Prop_LanguageImmersion",'LANGUAGE=MissingLanguage HEAR=-1 CHANCE=101 START=80 MAX=60');
 check("typed settings and references",has(invalid,"unknown language")&&has(invalid,"HEAR must")&&has(invalid,"CHANCE must")&&has(invalid,"START exceeds"),invalid);
 check("quoted keys not unknown",CM.core.PropCheck.unknownKeys("Prop_Senses",'TOUCH="UNKNOWNKEY=0" CHANCE=100').isEmpty(),"quoted prose linted as settings");
 check("valid LANG in VARIES",CM.core.TextCheck.checkText('<VARIES>^<LANG Elvish NOTE="old runes"^>hello^</LANG^></VARIES>').isEmpty(),CM.core.TextCheck.checkText('<VARIES>^<LANG Elvish^>hello^</LANG^></VARIES>'));
 check("invalid LANG diagnostics",has(CM.core.TextCheck.checkText('^<LANG BadLanguage^>hello'),"unknown LANG")&&has(CM.core.TextCheck.checkText('^<LANG Elvish^>hello'),"not closed"),"LANG validation missing");
 p.addTattoo(SR.token('FIRSTENTRY_','OLD_FIRST'));
 check('first-entry alias accepts old save format',AI.recorded(p,'FIRSTENTRY_',r,'FIRSTENTRY','ID=NEW_FIRST ALIASES=OLD_FIRST','',null),'legacy explicit ID lost');
 p.addTattoo(SR.token('WAYPOINT_','NET\nOLD_POINT'));
 check('waypoint alias accepts old save format',AI.recorded(p,'WAYPOINT_',r,'WAYPOINT','ID=NEW_POINT ALIASES=OLD_POINT','NET',null),'legacy waypoint lost');
 check('quoted numeric settings stay prose',Number(AO.number('LEAVEMSG="DURATION=999" DURATION=2','DURATION',0))==2,'numeric parser read message content');
 var immerse=ap(i,'Prop_LanguageImmersion','LANGUAGE=Dwarven HEAR=1 CHANCE=0 MAX=60 GRANTAFTER=2 START=5');CM.core.Languages.expose(p,'Dwarven','HEAR',i);
 i.setName('renamed teacher');immerse.setMiscText('LANGUAGE=Dwarven HEAR=2 CHANCE=100 MAX=60 GRANTAFTER=2 START=5');CM.core.Languages.expose(p,'Dwarven','HEAR',i);
 check('immersion progress survives edits',p.fetchAbility('Dwarven')!=null&&p.fetchAbility('Dwarven').proficiency()==5,'exposure counter reset');
} finally {p.setPlayerStats(null);p.destroy();i.destroy();r.destroy();r2.destroy();}
done();
