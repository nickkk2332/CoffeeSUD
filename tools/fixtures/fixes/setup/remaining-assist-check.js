FIXFILE='remaining-assist-check';
var R=M.location(),H=null;for(var e=R.inhabitants();e.hasMoreElements();){var m=e.nextElement();if(m.hasTag('REMAINING_HELPER'))H=m;}
var craft=M.fetchEffect('Construction'),help=H==null?null:H.fetchEffect('Construction');
check('both activities restored',craft!=null && help!=null,'missing activity');
if(craft!=null && help!=null){var before=CMath.s_int(craft.getStat('TICKDOWN'));help.tick(H,Tickable.TICKID_MOB);check('helper rebound before doing work',help.helpingAbility==craft,'reference was lost');check('assistance advances the actual craft',CMath.s_int(craft.getStat('TICKDOWN'))==before-1,before+' -> '+craft.getStat('TICKDOWN'));}
done();
