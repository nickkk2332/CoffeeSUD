FIXFILE='review-reload';
var file=new java.io.File(CM.core.save.WorldFiles.worldDir(),'js/behaviors/ReviewStateful.js'),holder=newMob('GenMob',M.location());
var effectFile=new java.io.File(CM.core.save.WorldFiles.worldDir(),'js/abilities/ReviewReloadEffect.js');
var item=CMClass.getItem('GenItem');M.location().addItem(item);
var cleanupFile=new java.io.File(CM.core.save.WorldFiles.worldDir(),'js/abilities/ReviewCleanupEffect.js');
var source="//extends com.planet_ink.coffee_mud.Behaviors.StdBehavior\nvar states={};\nfunction ID(){return 'ReviewStateful';}\n"
 +'function getStat(code){return String(code)==="STATE"?(states[java.lang.System.identityHashCode(this)]||""):"";}\n'
 +'function setStat(code,value){if(String(code)==="STATE")states[java.lang.System.identityHashCode(this)]=String(value);}\n';
function write(text){file.getParentFile().mkdirs();java.nio.file.Files.write(file.toPath(),new java.lang.String(text).getBytes('UTF-8'));}
try {
 write(source);check('load stateful compiled behavior',CMClass.loadClass(CMClass.CMObjectType.BEHAVIOR,'world/js/behaviors/ReviewStateful.js',false),'load failed');
 var old=CMClass.getBehavior('ReviewStateful');holder.addBehavior(old);old.setStat('STATE','progress-42');
 write(source+'function accountForYourself(){return "reloaded";}\n');var result=String(CM.core.JSReload.reload('behaviors/ReviewStateful.js')),current=holder.fetchBehavior('ReviewStateful');
 check('compiled replacement preserves runtime state',current!=old&&String(current.getStat('STATE'))=='progress-42'&&String(current.accountForYourself())=='reloaded',result);
 write("//extends com.planet_ink.coffee_mud.Behaviors.StdBehavior\nfunction ID(){return 'ReviewStateful';}\n");result=String(CM.core.JSReload.reload('behaviors/ReviewStateful.js'));
 check('incompatible replacement keeps live instance',holder.fetchBehavior('ReviewStateful')==current&&String(current.getStat('STATE'))=='progress-42'&&result.indexOf('1 incompatible instances kept')>=0,result);
 function effectSource(version){return "//extends com.planet_ink.coffee_mud.Abilities.StdAbility\nfunction ID(){return 'ReviewReloadEffect';}\nfunction accountForYourself(){return '"+version+"';}\n";}
 function writeEffect(version){effectFile.getParentFile().mkdirs();java.nio.file.Files.write(effectFile.toPath(),new java.lang.String(effectSource(version)).getBytes('UTF-8'));}
 writeEffect('one');check('load compiled effect',CMClass.loadClass(CMClass.CMObjectType.ABILITY,'world/js/abilities/ReviewReloadEffect.js',false),'effect load failed');
 var effect=CMClass.getAbility('ReviewReloadEffect');effect.setMiscText('kept text');effect.startTickDown(M,holder,11);effect.setProficiency(67);effect.setSavable(false);
 writeEffect('two');result=String(CM.core.JSReload.reload('abilities/ReviewReloadEffect.js'));var next=holder.fetchEffect('ReviewReloadEffect');
 check('compiled effect preserves lifetime and flags',next!=effect&&String(next.getStat('TICKDOWN'))=='11'&&next.proficiency()==67&&next.invoker()==M&&!next.isSavable()&&next.canBeUninvoked()&&String(next.text())=='kept text',result);
 next.tick(holder,Tickable.TICKID_MOB);check('replacement timed effect continues ticking',String(next.getStat('TICKDOWN'))=='10'&&effect.affecting()==null,next.getStat('TICKDOWN'));
 holder.delEffect(next);next.setAffectedOne(null);effect=CMClass.getAbility('ReviewReloadEffect');holder.addNonUninvokableEffect(effect);effect.setSavable(false);
 writeEffect('three');result=String(CM.core.JSReload.reload('abilities/ReviewReloadEffect.js'));next=holder.fetchEffect('ReviewReloadEffect');
 check('compiled permanent effect preserves flags',next!=effect&&!next.canBeUninvoked()&&!next.isSavable(),result);
 var itemEffect=CMClass.getAbility('ReviewReloadEffect');itemEffect.startTickDown(M,item,7);
 writeEffect('four');result=String(CM.core.JSReload.reload('abilities/ReviewReloadEffect.js'));var itemNext=item.fetchEffect('ReviewReloadEffect');
 check('item effect reload preserves duration',itemNext!=itemEffect&&String(itemNext.getStat('TICKDOWN'))=='7'&&itemNext.invoker()==M,result);
 check('item effect reload replaces tick registration',CMLib.threads().isTicking(itemNext,Tickable.TICKID_MOB)&&!CMLib.threads().isTicking(itemEffect,Tickable.TICKID_MOB),result);
 var cleanupSource="//extends com.planet_ink.coffee_mud.Abilities.StdAbility\nfunction ID(){return 'ReviewCleanupEffect';}\n"
  +"function setAffectedOne(p){this.setMiscText('');this.super$setAffectedOne(p);}\n"
  +"function getStat(code){if(String(code)==='STATE')return this.text();if(String(code)==='TICKDOWN')return String(this.getTickDownRemaining());return '';}\n"
  +"function setStat(code,value){if(String(code)==='STATE')this.setMiscText(String(value));if(String(code)==='TICKDOWN')this.setTickDownRemaining(parseInt(value));}\n";
 java.nio.file.Files.write(cleanupFile.toPath(),new java.lang.String(cleanupSource).getBytes('UTF-8'));
 check('load cleanup-sensitive compiled effect',CMClass.loadClass(CMClass.CMObjectType.ABILITY,'world/js/abilities/ReviewCleanupEffect.js',false),'cleanup class did not load');
 var cleanup=CMClass.getAbility('ReviewCleanupEffect');cleanup.startTickDown(M,holder,5);cleanup.setStat('STATE','progress-42');
 java.nio.file.Files.write(cleanupFile.toPath(),new java.lang.String(cleanupSource+'\nfunction accountForYourself(){return "new";}\n').getBytes('UTF-8'));
 result=String(CM.core.JSReload.reload('abilities/ReviewCleanupEffect.js'));var cleanupNext=holder.fetchEffect('ReviewCleanupEffect');
 check('old effect cleanup actually ran',String(cleanup.text())=='',cleanup.text());
 check('state captured before cleanup and applied after attach',cleanupNext!=cleanup&&String(cleanupNext.getStat('STATE'))=='progress-42'&&String(cleanupNext.getStat('TICKDOWN'))=='5',result);
} finally {holder.destroy();item.destroy();file['delete()']();effectFile['delete()']();cleanupFile['delete()']();}
done();
