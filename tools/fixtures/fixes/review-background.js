// Restored background-party interactions use the shared terminal and queue choices.
FIXFILE='review-background';
var SM=CM.core.ScriptMenus,N=CM.core.Narration,GT=CM.core.GameTime,R=M.location(),terminal=M.session();
var member=newMob('GenMob',R),host=newMob('GenMob',R),partySession=CMClass.getCommon('PartySession');
member.setName('ReviewBackground');member.setPlayerStats(CMClass.getCommon('DefaultPlayerStats'));member.playerStats().setAccount(CM.core.Party.profileOf(M));
partySession.initialize(null,'0');partySession.setMob(member);member.setSession(partySession);CMLib.sessions().add(partySession);
var engine=scriptOn(host,'FUNCTION_PROG CHOOSE\nmpsetvar FIXTEST REVIEW_BACKGROUND $0\n~\n'),turns=GT.isTurnBased(),paused=GT.isPaused();
function parsed(text){return CMLib.xml().parseAllXML(String(text)).get(0);}
function pendingCount(){var f=java.lang.Class.forName('com.planet_ink.coffee_mud.core.ScriptMenus').getDeclaredField('restored');f.setAccessible(true);return f.get(null).size();}
try {
 var menu=SM.parse('"[ReviewBackground] Saved choice" yes:"Yes" no:"No"');menu.who=member;menu.engine=engine;menu.func='CHOOSE';
 menu.ctx=new CM.Common.interfaces.ScriptingEngine.MPContext(host,host,member,host,null,null,'',null);SM.open(menu,null);
 var xml=String(SM.toXML());SM.fromXML(parsed(xml));SM.resumeSaved();
 check('background choice reaches terminal',terminal.isWaitingForInput()&&!partySession.isWaitingForInput()&&SM.waiting(member),'prompt lost in invisible session');
 var callback=field(terminal,'inputCallback');callback.setInput('1');callback.callBack();
 check('background choice callback clears prompt',gvar('REVIEW_BACKGROUND')=='yes'&&!terminal.isWaitingForInput()&&!SM.waiting(member),gvar('REVIEW_BACKGROUND'));
 var own=SM.parse('"Saved terminal choice" yes:"Yes" no:"No"');own.who=M;own.engine=engine;own.func='CHOOSE';own.ctx=menu.ctx;
 SM.open(menu,null);SM.open(own,null);xml=String(SM.toXML());SM.fromXML(parsed(xml));SM.resumeSaved();
 check('shared terminal displays one saved choice at a time',pendingCount()==1&&terminal.isWaitingForInput(),SM.toXML());
 callback=field(terminal,'inputCallback');callback.setInput('1');callback.callBack();
 check('next saved choice follows first answer',pendingCount()==0&&terminal.isWaitingForInput(),SM.toXML());
 callback=field(terminal,'inputCallback');callback.setInput('1');callback.callBack();
 check('all shared choices finish cleanly',!terminal.isWaitingForInput()&&!SM.waiting(member)&&!SM.waiting(M),SM.toXML());
 GT.setTurnBased(false);GT.pause();
 xml='<NARRATION RESUME="true"><QUEUE><WHO>'+CM.core.InstanceIds.reference(member)+'</WHO><PAGE>REVIEW_BACKGROUND_NARRATION</PAGE></QUEUE></NARRATION>';
 N.fromXML(parsed(xml));N.resumeSaved();
 check('background narration cannot strand a real-time pause',!GT.isPaused()&&!partySession.isWaitingForInput()&&!N.waiting(partySession),'invisible narration held world');
} finally {
 SM.cancel(member);SM.cancel(M);SM.fromXML(null);terminal['prompt(com.planet_ink.coffee_mud.Common.interfaces.Session$InputCallback)'](null);N.clear();
 GT.setTurnBased(turns);if(paused)GT.pause();else GT.resume();CMLib.sessions().remove(partySession);member.setPlayerStats(null);member.destroy();host.destroy();
}
done();
