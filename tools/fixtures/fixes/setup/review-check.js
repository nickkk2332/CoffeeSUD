FIXFILE='review-check';
var boss=null,add=null,R=M.location();for(var i=0;i<R.numInhabitants();i++){var m=R.fetchInhabitant(i);if(m.hasTag('REVIEW_BOSS'))boss=m;if(m.hasTag('REVIEW_ADD'))add=m;}
check('restored both same-name instances',boss!=null&&add!=null,'missing actors');
check('restored choice calls rebound source',gvar('REVIEW_PICKED')=='east',gvar('REVIEW_PICKED'));
check('restored stores remain distinct',String(cm.store(boss).role)=='boss'&&String(cm.store(add).role)=='add','store collision');
var token=M.findItem(null,'a review portable token');check('carried item store survives process restore',token!=null&&cm.store(token).portable==42,'missing item data');
var stock=CM.core.InstanceIds.find(String(cm.store('review').stockMob)),stockItem=CM.core.InstanceIds.find(String(cm.store('review').stockItem));
check('non-generic instance identities survive',stock!=null&&stockItem!=null&&String(cm.store(stock).role)=='stock'&&cm.store(stockItem).value==84,'stock class identity lost');
var phases=boss.fetchBehavior('BossPhases'),rotation=boss.fetchBehavior('CombatAbilities');
check('early phase round survives played restore',field(phases,'round')==7&&field(phases,'engaged')==true&&field(phases,'immuneFor')==4,phases.getStat('STATE'));
check('rotation ONCE cooldown survives played restore',field(rotation,'round')==7&&field(rotation,'lastUsedRound').get('Spell_MagicMissile')==6&&field(rotation,'rotationLog').contains('Spell_MagicMissile@6'),rotation.getStat('STATE'));
check('combat target links survive',boss.getVictim()==M&&M.getVictim()==boss,'fight reset');
add.setName('a renamed review summon');var engine=boss.scripts().nextElement();engine.registerHost(boss);for(var i=0;i<35;i++)engine.dequeResponses(null);
check('queued source follows instance across rename',String(CM.core.ObjectVars.vars(add).get('QUEUED'))=='yes'&&CM.core.ObjectVars.vars(boss).get('QUEUED')==null,'queued actor changed');
boss.makePeace(true);M.makePeace(true);phases.tick(boss,Tickable.TICKID_MOB);
check('restored summon remains owned by boss',field(phases,'summoned').contains(add),'summon ownership lost');
check('restored summon still follows boss',add.amFollowing()==boss,'summon follow link lost');
done();
