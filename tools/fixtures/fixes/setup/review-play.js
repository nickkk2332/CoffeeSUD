// Save in the middle of a fight and a dialogue choice; clear live prompts only after the snapshot.
FIXFILE='review-play';
var R=M.location(),II=CM.core.InstanceIds;
M.baseState().setHitPoints(10000);M.recoverMaxState();M.resetToMaxState();
var boss=newMob('GenMob',R),add=newMob('GenMob',R);boss.setName('a review guard');add.setName('a review guard');
[boss,add].forEach(function(m){m.setStartRoom(R);m.basePhyStats().setDamage(0);m.basePhyStats().setAttackAdjustment(-10000);m.baseState().setHitPoints(10000);m.recoverPhyStats();m.recoverMaxState();m.resetToMaxState();});
boss.addTag('REVIEW_BOSS');add.addTag('REVIEW_ADD');
var phases=CMClass.getBehavior('BossPhases');phases.setParms('AT=ROUND 100: IMMUNE 3');boss.addBehavior(phases);
var rotation=CMClass.getBehavior('CombatAbilities');rotation.setParms('proficient OPENER=Skill_Trip +Spell_MagicMissile(ONCE COOLDOWN=20)');boss.addBehavior(rotation);
function sf(o,n,v){for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(e){}throw n;}
sf(phases,'round',new java.lang.Integer(7));sf(phases,'engaged',java.lang.Boolean.TRUE);sf(phases,'immuneFor',new java.lang.Integer(4));field(phases,'summoned').add(add);sf(phases,'everSummoned',java.lang.Boolean.TRUE);
add.setFollowing(boss);
sf(rotation,'round',new java.lang.Integer(7));field(rotation,'lastUsedRound').put('Spell_MagicMissile',new java.lang.Integer(6));field(rotation,'rotationLog').add('Spell_MagicMissile@6');
cm.store(boss).role='boss';cm.store(add).role='add';
var token=CMClass.getItem('GenItem');token.setName('a review portable token');M.addItem(token);cm.store(token).portable=42;
var stock=newMob('StdMOB',R);stock.setName('a review stock mob');cm.store(stock).role='stock';
var stockItem=CMClass.getItem('StdItem');M.addItem(stockItem);cm.store(stockItem).value=84;
cm.store('review').stockMob=String(II.reference(stock));cm.store('review').stockItem=String(II.reference(stockItem));
var engine=scriptOn(boss,'FUNCTION_PROG ARM\nmpalarm 30 mpsetvar @$n QUEUED yes\n~\nFUNCTION_PROG PICK\nmpsetvar FIXTEST REVIEW_PICKED $0\n~\n');
var ctx=new CM.Common.interfaces.ScriptingEngine.MPContext(boss,boss,add,boss,null,null,'',null);engine.callFunc('ARM','',ctx);
boss.setVictim(M);M.setVictim(boss);
var SM=CM.core.ScriptMenus,menu=SM.parse('"REVIEW_SAVED_CHOICE" east:"East" west:"West"');menu.who=M;menu.engine=engine;menu.ctx=ctx;menu.func='PICK';SM.open(menu,null);
check('save succeeds with pending choice and combat',CM.core.save.SaveGame.save(M,'review',false)==null,'snapshot failed');
SM.cancel(M);M.session()['prompt(com.planet_ink.coffee_mud.Common.interfaces.Session$InputCallback)'](null);boss.makePeace(true);M.makePeace(true);
done();
