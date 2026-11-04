FIXFILE='pr-narrate-real';
CM.core.GameTime.setTurnBased(false);var room=CMLib.map().getRoom('FixPresentationSave#0');room.bringMobHere(M,false);
CM.core.ScriptEvents.callFunction(room,'STORY','',M);
check('real-time story returns',CM.core.Narration.waiting(M.session())&&CM.core.GameTime.isPaused(),'narration blocked or clock ran');
// Inspect the clock in the first Enter callback, after the harness has waited two seconds,
// before the final page resumes real time. This avoids counting prompt/command latency.
var frozen=CM.core.GameTime.now(),session=M.session(),original=field(session,'inputCallback');
var wrapper=new JavaAdapter(CM.Common.interfaces.Session.InputCallback,{
 showPrompt:function(){},timedOut:function(){original.timedOut();},
 callBack:function(){
  check('real-time story held clock',CM.core.GameTime.isPaused()&&CM.core.GameTime.now()==frozen,'clock advanced while Enter was pending');
  original.callBack();
 }
},CM.Common.interfaces.Session.InputCallback.Type.PROMPT,'',0);
for(var klass=session.getClass();klass!=null;klass=klass.getSuperclass()){
 try {var f=klass.getDeclaredField('inputCallback');f.setAccessible(true);f.set(session,wrapper);break;}catch(e){}
}
done();
