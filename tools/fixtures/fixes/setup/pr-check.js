FIXFILE='pr-check';var room=CMLib.map().getRoom('FixPresentationSave#0'),remote=CMLib.map().getRoom('FixPresentationSave#2');
check('saved journal',String(CM.core.Chronicle.show(M,'Clues',false)).indexOf('FIX_SAVED_JOURNAL')>=0,'journal lost');check('saved lore',CM.core.LoreBook.known(M,'SAVEDENTRY')&&!CM.core.LoreBook.unread(M,'SAVEDENTRY'),'unlocked/read state lost');
check('saved profile lore',CM.core.Party.profileOf(M).findTattoo('LORE_SAVEDPROFILE')!=null,'profile lore lost');
check('saved map',CM.core.PlayerMaps.known(M,remote)&&!M.playerStats().hasVisited(remote)&&String(CM.core.PlayerMaps.render(M,remote,2)).indexOf('FIX_SAVED_MARK')>=0,'knowledge or mark lost');
check('saved preferences',CM.core.PlayerMaps.size(M)==4&&String(CM.core.ColorAliases.expand('^{LORE}text',M)).indexOf('^gtext')>=0,'preferences lost');check('saved first entry',M.findTattoo('FIXFIRSTENTRY')!=null,'once marker lost');
var relic=M.findItem(null,'a saved identity relic');check('source identity restored',relic!=null&&relic.fetchEffect('Prop_AuthorIdentity')!=null,'source metadata lost');
if(relic!=null){relic.setName('a renamed saved relic');check('restored discovery survives rename',CM.core.ViewRules.allowed(relic,M),'discovery key changed after restore');var senses=relic.fetchEffect('Prop_Senses');senses.setMiscText('TOUCH="Changed inscription" SETVAR=AR_SAVED_CLUE=wrong');CM.core.Scenery.clue(M,relic,senses.text(),'TOUCH');check('restored clue remains once only',String(CM.core.StoryText.variable(M.Name(),'AR_SAVED_CLUE'))=='first','clue replayed after save');}
done();
