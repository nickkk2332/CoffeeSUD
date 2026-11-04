FIXFILE='pr-narrate';
var room=CMLib.map().getRoom('FixPresentationSave#0');room.bringMobHere(M,false);VARS.setVar('FIXTEST','FIXPAGEAT','='+CM.core.GameTime.now());
var time=CM.core.GameTime;var before=time.now();CM.core.ScriptEvents.callFunction(room,'STORY','',M);
check('nonblocking pages',CM.core.Narration.waiting(M.session()),'no queued paragraphs');check('paging clock',time.isTurnBased()||time.isPaused(),'real-time clock not held');done();
