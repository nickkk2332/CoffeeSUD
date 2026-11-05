FIXFILE='remaining-assist-save';
var R=M.location(),H=newMob('GenMob',R);H.setName('a remaining crafting helper');H.addTag('REMAINING_HELPER');
function set(o,n,v){for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(e){}throw Error(n);}
var craft=CMClass.getAbility('Construction');craft.startTickDown(M,M,60);craft=M.fetchEffect('Construction');set(craft,'activityRoom',R);
var help=CMClass.getAbility('Construction');var words=new java.util.ArrayList();words.add('HELP');words.add(M.Name());
help.invoke(H,words,null,true,0);help=H.fetchEffect('Construction');
check('live assistance established',help!=null && help.helpingAbility==craft,'no producer link');
check('save',CM.core.save.SaveGame.save(M,'assistance',false)==null,'save failed');
done();
