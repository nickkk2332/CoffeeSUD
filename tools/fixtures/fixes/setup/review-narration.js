FIXFILE='review-narration';
var GT=CM.core.GameTime,N=CM.core.Narration;GT.setTurnBased(false);GT.resume();
N.show(M,M,'REVIEW_READ_PAGE\n\nREVIEW_UNREAD_PAGE',true,'');
check('narration save succeeds',CM.core.save.SaveGame.save(M,'narration',false)==null,'save failed');N.cancel(M.session());M.session()['prompt(com.planet_ink.coffee_mud.Common.interfaces.Session$InputCallback)'](null);GT.setTurnBased(true);
done();
