// Completion regressions for the project status review's remaining authoring gaps.
FIXFILE = 'remaining-authoring';
function s(v) { return ''+v; }
function has(v,t) { return s(v).indexOf(t)>=0; }
function command(who,id,words) { var a=new java.util.ArrayList(); words.forEach(function(x){a.add(x);});CMClass.getCommand(id).execute(who,a,0); }
var R=scratchRoom(), V=newMob('GenMob',R), session=CMClass.getCommon('FakeSession');
V.setName('RemainingTester');session.initialize(null,'MEMORY');V.setSession(session);session.setMob(V);
var W=CM.Items.interfaces.Wearable, items=[];
function item(name) {var I=CMClass.getItem('GenItem');I.setName(name);V.addItem(I);items.push(I);return I;}
try {
 var bad=CM.core.ScriptCheck.checkText('remaining','ONCE_PROG\nMPSETVAR $i\nMPECHO $<$i NEVERSET>\n~',null);
 check('command signature',has(bad,'MPSETVAR needs at least'),bad);
 check('unset variable',has(bad,'NEVERSET read without a known assignment'),bad);
 var good=CM.core.ScriptCheck.checkText('remaining','ONCE_PROG\nMPSETVAR $i SEEN yes\nMPECHO $<$i SEEN>\n~',null);
 check('assigned variable',good.isEmpty(),good);
 var detail=item('remaining plaque'); detail.setDescription('<VARIES><NIGHT>unfinished');
 var P=CMClass.getAbility('Prop_Details');P.setMiscText('rune,rune=letters');detail.addNonUninvokableEffect(P);
 var text=CM.core.TextCheck.checkRoom(R);
 check('item text checked',has(text,'NIGHT> is not closed'),text);
 check('detail keywords checked',has(text,'duplicate keyword rune'),text);
 var book=CMClass.getItem('GenReadable');V.addItem(book);items.push(book);book.setReadableText('FILE=remaining-missing-file.txt');
 check('readable link checked',has(CM.core.TextCheck.checkRoom(R),'missing FILE=remaining-missing-file.txt'),'missing link not reported');
 var mobile=CMClass.getBehavior('Mobile');V.addBehavior(mobile);
 check('live behavior state',has(mobile.getStat('DEBUG'),'tickDown='),mobile.getStat('DEBUG'));
 var S=CMClass.getAbility('Prop_Switch');S.setMiscText('STATES=off,on START=off ACTION_on="setvar FIXTEST REMAINING_SWITCH yes"');
 var lever=item('remaining lever');lever.addNonUninvokableEffect(S);clearVar('REMAINING_SWITCH');
 command(V,'PuzzleCmd',['PUZZLE','SET','remaining lever','on']);
 check('puzzle set and action',s(S.state())=='on' && gvar('REMAINING_SWITCH')=='yes',S.text());
 command(V,'PuzzleCmd',['PUZZLE','RESET','remaining lever']);
 check('puzzle reset',s(S.state())=='off',S.text());
 var chat=CMClass.getBehavior('MudChat');
 var lint=CM.Behaviors.MudChat.lintChat('>tester\n(hello)\n1say hi\n(hello friend)\n1say welcome\n');
 check('hidden chat pattern',has(lint,'hidden by the earlier pattern on line 2'),lint);
 var pieces=[];
 ['Prop_WearSpellCast','Prop_WearEnabler','Prop_WearResister','Prop_AddDamage'].forEach(function(id,n){
  var I=item('remaining set '+n);I.setRawProperLocationBitmap(W.WORN_HELD);I.setRawLogicalAnd(false);I.wearAt(W.WORN_HELD);
  var a=CMClass.getAbility(id);a.setMiscText((id=='Prop_AddDamage'?'5':id=='Prop_WearResister'?'fire 50%':'Skill_Dodge')+' ALLSET="remaining-3"');I.addNonUninvokableEffect(a);pieces.push(I);
  check(id+' set metadata',s(a.getStat('ALLSET'))=='remaining-3' && has(a.text(),'ALLSET='),a.text());
 });
 pieces[3].wearAt(W.WORN_HEAD);
 check('improperly worn set piece excluded',!CM.core.ItemSets.legacyEnabled(pieces[0],'remaining-4'),'invalid slot counted');pieces[3].wearAt(W.WORN_HELD);
 check('mixed legacy set enabled',CM.core.ItemSets.legacyEnabled(pieces[0],'remaining-3'),'not counted');
 pieces[2].unWear();pieces[3].unWear();
 check('mixed legacy set disabled',!CM.core.ItemSets.legacyEnabled(pieces[0],'remaining-3'),'still counted');
 var craft=CMClass.getAbility('Carpentry'), helper=CMClass.getAbility('Carpentry');
 var H=newMob('GenMob',R);H.setName('RemainingHelper');V.addNonUninvokableEffect(craft);H.addNonUninvokableEffect(helper);helper.helpingAbility=craft;
 var activity=s(helper.getStat('ACTIVITY'));
 check('activity encodes ability reference',has(activity,'T="ABILITY"') && !has(activity,'<SKIPPED>CommonSkill.helpingAbility'),activity);
 var restored=CMClass.getAbility('Carpentry');H.delEffect(helper);H.addNonUninvokableEffect(restored);restored.setStat('ACTIVITY',activity);restored.resolveActivityLinks();
 check('activity restores same live effect',restored.helpingAbility==craft,restored.helpingAbility);
 H.destroy();V.delEffect(craft);
 command(V,'PlaytestCmd',['PLAYTEST','ON','DIFFICULTY=EASY']);
 check('playtest difficulty preset',s(CM.core.GameDifficulty.current())=='EASY' && CMSecurity.isPlaytesting(V),CM.core.GameDifficulty.current());
 command(V,'PlaytestCmd',['PLAYTEST','OFF']);
 check('playtest off',!CMSecurity.isPlaytesting(V),'powers not restored');
 var curse=CMClass.getAbility('Prop_Cursed');curse.setMiscText('HIDDEN=true LEVEL=20');
 V.basePhyStats().setLevel(1);V.recoverPhyStats();check('curse hidden below level',!curse.detectedBy(V),'detected too early');
 V.basePhyStats().setLevel(20);V.recoverPhyStats();check('curse detection level',curse.detectedBy(V),'not detected');
 var gate=CMClass.getExit('Open');R.setRawExit(CM.core.Directions.NORTH,gate);
 var blocked=CMClass.getAbility('Prop_BlockedExit');blocked.setMiscText('NAME=blockade HP=10000');gate.addNonUninvokableEffect(blocked);
 V.basePhyStats().setDamage(100);V.recoverPhyStats();
 // Exercise the normal damage-message contract. Hooks can consume dice and
 // change the roll; the blockage must use the final value accepted by the room.
 clearVar('REMAINING_BLOCK_DAMAGE');
 var damageHook=scriptOn(R,'CNCLMSG_PROG DAMAGE ALL\nmpmsg value 13\nmpsetvar FIXTEST REMAINING_BLOCK_DAMAGE changed\n~\n');
 blocked.takeVerb(V,gate,'attack','north');
 check('blockade weapon damage',gvar('REMAINING_BLOCK_DAMAGE')=='changed' && CMath.s_int(blocked.getStat('LEFT'))==9987,blocked.getStat('LEFT')+' / '+gvar('REMAINING_BLOCK_DAMAGE'));
 R.delScript(damageHook);clearVar('REMAINING_BLOCK_DAMAGE');
 var A=R.getArea(), arrest=CMClass.getBehavior('Arrest');A.addBehavior(arrest);
 try {
  var L=arrest.legalInfo(A), count=L.warrants().size(), rolls=CMath.getRandState();
  var diagnosis=arrest.testCrime(A,V,null,'ASSAULT');
  check('law test leaves docket and dice',count==L.warrants().size() && rolls.equals(CMath.getRandState()) && has(diagnosis,'warrant'),diagnosis);
 } finally { A.delBehavior(arrest); }
} finally { items.forEach(function(I){I.destroy();});V.setSession(null);V.destroy();R.destroy();CM.core.GameDifficulty.set('NORMAL',M.location());clearVar('REMAINING_SWITCH'); }
done();
