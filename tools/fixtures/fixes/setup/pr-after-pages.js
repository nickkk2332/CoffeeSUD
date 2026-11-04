FIXFILE='pr-after-pages';
check('all pages finished',!CM.core.Narration.waiting(M.session()),'still waiting');check('pages took no time',String(CM.core.GameTime.now())==gvar('FIXPAGEAT'),'clock advanced during paging');done();
