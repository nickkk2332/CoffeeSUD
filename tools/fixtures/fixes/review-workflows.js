// Saved interaction lifecycle, compiled reload state, and disposable playtest preparation.
FIXFILE='review-workflows';
var SM=CM.core.ScriptMenus,N=CM.core.Narration,GT=CM.core.GameTime;
var R=M.location(),who=newMob('GenMob',R),host=newMob('GenMob',R);
who.setName('ReviewChoice');host.setName('a review narrator');
var session=new JavaAdapter(CM.Common.FakeSession,{isFake:function(){return false;},prompt:function(callback){if(callback!=null)callback.showPrompt();}});session.initialize(null,'MEMORY');session.setMob(who);who.setSession(session);
var wasTurns=GT.isTurnBased(),wasPaused=GT.isPaused(),prepared=null;
var engine=scriptOn(host,'FUNCTION_PROG REVIEW_PICK\nmpsetvar FIXTEST REVIEW_CHOICE $0\n~\n');
function tag(xml){return CMLib.xml().parseAllXML(String(xml)).get(0);}
function text(){return String(field(session,'bout').toString());}
try {
 var menu=SM.parse('"Choose a path" east:"East" west:"West" west');menu.who=who;menu.engine=engine;menu.func='REVIEW_PICK';
 menu.ctx=new CM.Common.interfaces.ScriptingEngine.MPContext(host,host,who,host,null,null,'',null);SM.open(menu,null);
 var saved=String(SM.toXML());SM.fromXML(tag(saved));SM.resumeSaved();
 check('menu is rebound after restore',SM.waiting(who)&&text().indexOf('Choose a path')>=0,saved);
 SM.answer(who,'1');check('restored menu runs original callback',gvar('REVIEW_CHOICE')=='east'&&!SM.waiting(who),gvar('REVIEW_CHOICE'));
 SM.open(menu,null);saved=String(SM.toXML());host.delScript(engine);SM.fromXML(tag(saved));SM.resumeSaved();
 check('changed source cancels visibly',!SM.waiting(who)&&text().indexOf('source script is unavailable or changed')>=0,text());
 GT.setTurnBased(false);GT.resume();N.show(who,host,'First page\n\nSecond page\n\nThird page',true,'');
 saved=String(N.toXML());check('narration pauses real-time world',GT.isPaused()&&N.waiting(session),saved);
 N.fromXML(tag(saved));N.resumeSaved();N.next(session);
 check('narration resumes at next unread page',text().indexOf('Second page')>=0&&N.waiting(session)&&GT.isPaused(),text());
 N.next(session);check('final page releases owned pause',text().indexOf('Third page')>=0&&!N.waiting(session)&&!GT.isPaused(),text());
 GT.pause();N.show(who,host,'Manual one\n\nManual two',true,'');saved=String(N.toXML());N.fromXML(tag(saved));N.resumeSaved();N.next(session);
 check('narration preserves manual pause',GT.isPaused()&&!N.waiting(session),'manual pause lost');GT.resume();
 var ability=CMClass.getAbility('Spell_Blur'),target=CMClass.getAbility('Spell_Blur');ability.setMiscText('review parms');ability.setProficiency(73);ability.setInvoker(who);ability.setStat('TICKDOWN','9');ability.setSavable(false);
 CM.core.JSReload.migrateAbility(ability,target);
 check('effect reload retains duration proficiency and invoker',String(target.getStat('TICKDOWN'))=='9'&&target.proficiency()==73&&target.invoker()==who&&!target.isSavable(),target.getStat('TICKDOWN'));
 var V=CM.core.Vocabulary,original=String(V.read('origins.ini')),sum=String(CM.core.save.WorldFiles.checksum());
 prepared=CM.core.IsolatedPlaytest.prepare(M);
 check('disposable playtest has complete campaign',new java.io.File(prepared.directory,'saves/playtest/.complete').isFile()&&new java.io.File(prepared.directory,'world/areas').isDirectory(),prepared.directory);
 java.nio.file.Files.write(new java.io.File(prepared.directory,'world/origins.ini').toPath(),new java.lang.String('scratch only').getBytes('UTF-8'));
 check('playtest mutations do not touch builder files',String(V.read('origins.ini'))==original&&String(CM.core.save.WorldFiles.checksum())==sum,'builder world changed');
 check('playtest uses independent database and save folder',String(prepared.command).indexOf('BUILDER=0')>=0&&String(prepared.command).indexOf('-Dsud.isolated.playtest=true')>=0,String(prepared.command));
 var job=cm.background(function(data){return {answer:data.value+1,isolated:typeof cm=='undefined'};},{value:41},1000,function(error,result){cm.var('FIXTEST','REVIEW_JOB',error?'error:'+error:''+result.answer);});
 for(var n=0;n<100&&!job.done();n++)java.lang.Thread.sleep(5);
 check('background job transfers JSON through isolated scope',job.done()&&String(job.error())==''&&String(job.resultJSON()).indexOf('42')>=0&&String(job.resultJSON()).indexOf('true')>=0,job.resultJSON());
 var slow=cm.background(function(data){java.lang.Thread.sleep(1000);return data;},{},50,function(error,result){cm.var('FIXTEST','REVIEW_TIMEOUT',error);});
 for(var n=0;n<100&&!slow.done();n++)java.lang.Thread.sleep(5);
 check('background native call has bounded completion',slow.done()&&String(slow.error()).indexOf('timed out')>=0,slow.error());
} finally {
 SM.cancel(who);N.clear();GT.setTurnBased(wasTurns);if(wasPaused)GT.pause();else GT.resume();
 who.destroy();host.destroy();if(prepared!=null)CM.core.IsolatedPlaytest.deleteTree(prepared.directory.toPath());
}
done();
