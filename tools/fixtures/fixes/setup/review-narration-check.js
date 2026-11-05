FIXFILE='review-narration-check';
check('narration continuation releases temporary pause',!CM.core.Narration.waiting(M.session())&&!CM.core.GameTime.isPaused(),'clock remained paused');
CM.core.GameTime.setTurnBased(true);done();
