FIXFILE='pr-after-real';
check('real-time story resumed',!CM.core.Narration.waiting(M.session())&&!CM.core.GameTime.isPaused(),'narration hold not released');CM.core.GameTime.setTurnBased(true);done();
