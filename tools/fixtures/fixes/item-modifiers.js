FIXFILE='item-modifiers';
var IM=CM.core.ItemModifiers,IC=CM.core.ItemCrafting,IR=CM.core.ItemModifierRules,OM=CM.core.OptionalModules,MD=CM.core.ModuleDefinitions,CP=CM.core.CombatProfiles;
var Files=java.nio.file.Files,JString=java.lang.String,seed=CM.core.GenSeed.begin(19),root=Files.createTempDirectory('sud-item-live-').toFile();
var originalWorld=WorldFiles.worldDir(),current=java.lang.Class.forName('com.planet_ink.coffee_mud.core.OptionalModules').getDeclaredField('current');current.setAccessible(true);var oldCampaign=current.get(null);
var known=java.lang.Class.forName('com.planet_ink.coffee_mud.core.save.WorldFiles').getDeclaredField('knownSums');known.setAccessible(true);var oldKnown=known.get(null);
var R=scratchRoom(),actors=[],items=[];
function refuse(name,fn){var bad=false;try{fn();}catch(e){bad=true;}check(name,bad,'expected refusal');}
function copy(from,to){if(from.isDirectory()){to.mkdirs();var list=from.listFiles();for(var i=0;i<list.length;i++)copy(list[i],new java.io.File(to,String(list[i].getName())));}else Files.copy(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
function read(f){return String(new JString(Files.readAllBytes(f.toPath()),'UTF-8'));}
function write(f,text){Files.write(f.toPath(),new JString(text).getBytes('UTF-8'));}
function data(item){return JSON.parse(String(MD.json(IM.data(item))));}
function state(a){return String(MD.json(OM.get('items.modifiers',OM.character(a))));}
function actor(name){var a=newMob('GenMob',R);a.setName(name);actors.push(a);return a;}
function ore(a,weight){var i=CMClass.getItem('GenResource');i.setName('some test iron');i.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_IRON);i.basePhyStats().setWeight(weight);i.setBaseValue(weight);i.recoverPhyStats();a.addItem(i);items.push(i);return i;}
function make(a){var list=CM.core.GenCalls.items('randareas/embertrial-loot.xml','embertrial_mantle',null,a,R);var item=list.get(0);items.push(item);a.addItem(item);return item;}
function prepare(a,op,item,index){return String(IC.prepare(a,'embertrial.'+op,item,String(index)));}
function commit(a,id){return String(IC.commit(a,id||0));}
try{
 var examples=new java.io.File('../SUDDocs/creator/examples');
 copy(new java.io.File(examples,'ability-composition/modules'),new java.io.File(root,'modules'));
 copy(new java.io.File(examples,'combat-profiles/modules'),new java.io.File(root,'modules'));
 copy(new java.io.File(examples,'combat-profiles/combat'),new java.io.File(root,'combat'));
 copy(new java.io.File(examples,'item-modifiers'),root);
 var affixFile=new java.io.File(root,'affixes.xml'),affixText=read(affixFile);
 var legacy='<AFFIX ID="test.legacy" NAME="Old" POS="prefix" KINDS="armor" LEVEL="1-99" PICKWEIGHT="1" POWER="1"><AFFECT CLASS="Prop_WearAdjuster" PARMS="armor-1"/></AFFIX>';
 write(affixFile,affixText.replace('</AFFIXES>',legacy+'</AFFIXES>'));
 WorldFiles.configure(root);CP.reset();IM.reset();CM.core.Affixes.reload();
 var plan=MD.load(root,'items.modifiers','');check('native-contract-and-dependencies-valid',plan.errors.isEmpty(),String(plan.problems()));current.set(null,OM.prepare(plan,null,false));
 var manifest=new java.io.File(root,'modules/items.modifiers.json'),manifestText=read(manifest),badInitial=JSON.parse(manifestText);badInitial.state.serial.default=1;write(manifest,JSON.stringify(badInitial));check('native-contract-rejects-prefilled-craft-counter',!MD.load(root,'items.modifiers','').errors.isEmpty(),'prefilled ledger accepted');write(manifest,manifestText);
 check('all-family-definitions-valid',IM.check().errors.isEmpty(),String(IM.check().report()));
 check('native-save-marker',String(OM.snapshot()).indexOf('"requires_handler":"items.modifiers"')>=0,'missing native reader requirement');
 var A=actor('Forge owner'),B=actor('Other owner');var forge=CMClass.getItem('GenItem'),station=CMClass.getAbility('Prop_Workstation');station.setMiscText('TAG=ember-forge');forge.addNonUninvokableEffect(station);R.addItem(forge);items.push(forge);
 var material=ore(A,8),item=make(A),first=data(item);
 check('existing-generator-produces-two-tracked-modifiers',first.modifiers.length==2,String(IM.inspect(item)));
 check('exclusive-groups-prevent-two-fire-prefixes',first.modifiers[0].group!==first.modifiers[1].group,'group duplicate');
 var originalRecord=String(item.fetchEffect('Prop_ItemModifiers').text()),badRecord=JSON.parse(originalRecord),statKey=Object.keys(badRecord.modifiers[0].values)[0];badRecord.modifiers[0].values[statKey]=99;item.fetchEffect('Prop_ItemModifiers').setMiscText(JSON.stringify(badRecord));check('world-check-diagnoses-corrupt-source-values',!IM.checkItems(java.util.Arrays.asList(R),IM.check()).isEmpty(),'out-of-range roll was not diagnosed');item.fetchEffect('Prop_ItemModifiers').setMiscText(originalRecord);
 check('room-display-includes-rendered-modifiers',String(item.displayText()).toLowerCase().indexOf(String(item.Name()).toLowerCase())>=0,'room display stale');
 var template=new java.io.File(root,'resources/randareas/embertrial-loot.xml'),seeded=new java.io.File(root,'resources/randareas/item-seeded.xml');write(seeded,read(template).replace('ID="embertrial_mantle"','ID="seeded_mantle" SEED="732"'));
 var savedDice=CMath.getRandState(),seededA=CM.core.GenCalls.items('randareas/item-seeded.xml','seeded_mantle',null,A,R).get(0),seededB=CM.core.GenCalls.items('randareas/item-seeded.xml','seeded_mantle',null,A,R).get(0);items.push(seededA);items.push(seededB);
 var aMods=data(seededA).modifiers,bMods=data(seededB).modifiers;for(var si=0;si<aMods.length;si++){delete aMods[si].uid;delete bMods[si].uid;}
 check('template-seed-covers-affix-selection-and-rolls',JSON.stringify(aMods)===JSON.stringify(bMods),'seeded rolls differ');
 check('template-seed-preserves-campaign-dice-stream',savedDice.equals(CMath.getRandState()),'seed leaked into campaign rolls');
 var varTemplate=new java.io.File(root,'resources/randareas/item-vars.xml');write(varTemplate,read(template).replace('ID="embertrial_mantle"','ID="var_mantle"').replace('MODBASE="embertrial.mantle"','MODBASE="$mantle_base"'));
 var mantleVars=new java.util.HashMap();mantleVars.put('MANTLE_BASE','embertrial.mantle');var varItem=CM.core.GenCalls.items('randareas/item-vars.xml','var_mantle',mantleVars,A,R).get(0);items.push(varItem);
 check('modbase-expands-generator-variables',IM.isManaged(varItem)&&data(varItem).base.id==='embertrial.mantle'&&data(varItem).modifiers.length==2,String(IM.inspect(varItem)));
 var dice=CMath.getRandState(),snapshot=state(A),raw=String(item.text()),weight=material.basePhyStats().weight();
 IC.preview(A,'embertrial.reroll',item,'1');IM.inspect(item);IC.operations();IC.pool(item);CMClass.getCommand('ItemMods').execute(A,CM.core.CMParms.parse('ITEMMODS "trial mantle"'),0);
 check('preview-inspection-no-dice-state-or-item-mutation',dice.equals(CMath.getRandState())&&snapshot===state(A)&&raw===String(item.text())&&weight==material.basePhyStats().weight(),'inspection mutated');
 check('inspection-and-craft-command-costs-zero',CMClass.getCommand('ModCraft').checkedActionsCost(A,CM.core.CMParms.parse('MODCRAFT STATUS'))==0&&CMClass.getCommand('ItemMods').checkedActionsCost(A,CM.core.CMParms.parse('ITEMMODS mantle'))==0,'action cost');
 refuse('other-character-cannot-prepare-owner-item',function(){prepare(B,'reroll',item,1);});
 OM.configure('items.modifiers','allow_crafting','false');refuse('campaign-crafting-switch-also-blocks-preview',function(){IC.preview(A,'embertrial.reroll',item,'1');});OM.configure('items.modifiers','allow_crafting','true');
 CMClass.getCommand('Wear').execute(A,CM.core.CMParms.parse('WEAR "trial mantle"'),0);check('sample-mantle-uses-normal-wear-command',item.rawWornCode()==CM.Items.interfaces.Wearable.WORN_ABOUT_BODY,'sample not wearable');refuse('worn-item-cannot-be-crafted',function(){prepare(A,'reroll',item,1);});
 var merged=CP.stats(A,CP.current()).stats,fire=0,pen=0;for(var m=0;m<first.modifiers.length;m++){fire+=first.modifiers[m].values['resistance.fire']||0;pen+=first.modifiers[m].values['penetration.fire']||0;}
 check('rolled-stats-feed-native-profile-collector',merged.get('resistance.fire')==fire&&merged.get('penetration.fire')==pen&&merged.get('crit_multiplier')==150,String(merged.describe()));item.unWear();
 check('unequipping-removes-only-tracked-bonuses',CP.stats(A,CP.current()).stats.get('resistance.fire')==0,'stale bonuses');
 var draws=CMath.getRandState();prepare(A,'reroll',item,1);check('preparation-does-not-roll-or-spend',draws.equals(CMath.getRandState())&&material.basePhyStats().weight()==8,'preparation spent');
 material.basePhyStats().setWeight(9);material.recoverPhyStats();var stale=state(A);draws=CMath.getRandState();
 refuse('changed-material-snapshot-refuses-commit',function(){commit(A);});
 check('stale-refusal-atomic-and-no-dice',stale===state(A)&&draws.equals(CMath.getRandState())&&data(item).revision==0,'stale request changed');
 material.basePhyStats().setWeight(8);material.recoverPhyStats();IC.cancel(A,0);
 var notifications=0,settledBeforeCallback=false,reentrantRefused=false,lostSettled=false;
 cm.on('itemLost',function(who,cost){if(who===A&&cost===material){var s=JSON.parse(state(A));lostSettled=!IC.inCommit()&&s.pending===''&&s.settled==s.serial&&cost.amDestroyed();}},'fix-item-cost-lost');
 cm.on('itemCrafted',function(who,target,receipt){if(who!==A)return;notifications++;var r=JSON.parse(String(receipt)),s=JSON.parse(state(A));settledBeforeCallback=!IC.inCommit()&&s.pending===''&&s.settled==r.request&&data(target).revision==r.revision;try{prepare(A,'reroll',item,1);}catch(e){reentrantRefused=true;}return false;},'fix-item-crafted');
 prepare(A,'reroll',item,1);var pending=JSON.parse(state(A)),request=pending.serial,before=data(item);commit(A);var after=data(item);
 check('one-cost-and-one-revision',material.basePhyStats().weight()==7&&after.revision==1,'cost/revision mismatch');
 check('authored-pile-name-kept-after-partial-spend',String(material.Name())==='some test iron',String(material.Name()));
 check('selective-reroll-preserves-other-source',JSON.stringify(before.modifiers[1])===JSON.stringify(after.modifiers[1])&&before.modifiers[0].uid===after.modifiers[0].uid,'unselected modifier changed');
 check('advisory-event-sees-settled-state',notifications==1&&settledBeforeCallback&&reentrantRefused,'observer boundary failed');
 var committedState=state(A),committedText=String(item.text());draws=CMath.getRandState();commit(A,request);
 check('duplicate-commit-is-idempotent',committedState===state(A)&&committedText===String(item.text())&&material.basePhyStats().weight()==7&&notifications==1&&draws.equals(CMath.getRandState()),'duplicate charged or fired');
 prepare(A,'protect',item,1);commit(A);check('protection-saved',data(item).modifiers[0].protected===true,'lock missing');draws=CMath.getRandState();weight=material.basePhyStats().weight();
 refuse('protected-reroll-rejected',function(){prepare(A,'reroll',item,1);});refuse('protected-removal-rejected',function(){prepare(A,'remove',item,1);});
 check('rejected-protection-spends-nothing',draws.equals(CMath.getRandState())&&weight==material.basePhyStats().weight(),'protected path spent');
 prepare(A,'unprotect',item,1);commit(A);var removed=data(item).modifiers[0],remaining=data(item).modifiers[1];prepare(A,'remove',item,1);commit(A);
 check('remove-affects-one-modifier',data(item).modifiers.length==1&&data(item).modifiers[0].uid===remaining.uid,'bad removal');
 prepare(A,'add',item,removed.pin.id);commit(A);check('addition-obeys-freed-slot-and-group',data(item).modifiers.length==2&&material.basePhyStats().weight()==2,'addition/cost failed');
 item.setName('a renamed mantle');prepare(A,'reroll',item,1);commit(A);prepare(A,'reroll',item,1);commit(A);
 check('external-rename-survives-repeated-edits',String(item.Name())==='a renamed mantle'&&material.amDestroyed(),'name reset or full pile not consumed');
 check('full-cost-item-event-observes-settled-transaction',lostSettled,'itemLost saw partial state');
 var clone=IM.copyOf(item);items.push(clone);var original=data(item),copied=data(clone);
 check('explicit-copy-keeps-rolls-with-new-identities',original.item!==copied.item&&original.modifiers[0].uid!==copied.modifiers[0].uid&&JSON.stringify(original.modifiers[0].values)===JSON.stringify(copied.modifiers[0].values),'copy identity/values failed');
 check('explicit-copy-is-unplaced-and-unworn',clone.owner()==null&&clone.container()==null&&clone.rawWornCode()==0,'copy remained placed');
 var plain=CMClass.getItem('GenArmor');plain.setName('a plain legacy armor');plain.basePhyStats().setLevel(1);plain.recoverPhyStats();items.push(plain);
 check('legacy-affix-path-preserved',CM.core.Affixes.apply(plain,2)==1&&String(plain.Name()).indexOf('Old')>=0&&!IM.isManaged(plain),'legacy changed');
 var critical=java.lang.Class.forName('com.planet_ink.coffee_mud.core.ItemCrafting').getDeclaredField('critical');critical.setAccessible(true);critical.setBoolean(null,true);var refusedSnapshot=false;
 try{try{CM.core.save.WorldState.capture(M);}catch(e){refusedSnapshot=true;}check('save-refuses-partial-transaction',String(CM.core.save.SaveGame.exportPlaytest(M,new java.io.File(root,'guard-save'))).indexOf('settled boundary')>=0&&refusedSnapshot,'partial save permitted');}finally{critical.setBoolean(null,false);}
 item.wearAt(CM.Items.interfaces.Wearable.WORN_TORSO);var activeFire=CP.stats(A,CP.current()).stats.get('resistance.fire');
 var off=MD.load(root,'combat.profiles','');current.set(null,OM.prepare(off,String(OM.snapshot()),true));
 check('disable-suspends-bonuses-with-record-kept',CP.stats(A,CP.current()).stats.get('resistance.fire')==0&&data(item).modifiers.length==2&&String(OM.snapshot()).indexOf('"requires_handler":"items.modifiers"')>=0,'disable lost data or kept bonus');
 current.set(null,OM.prepare(plan,String(OM.snapshot()),true));check('resume-restores-frozen-rolls',CP.stats(A,CP.current()).stats.get('resistance.fire')==activeFire,'rolls reinterpreted');item.unWear();
 // A generic copy (a rejuv, reset or RandomItems copy) carries the item's record, but is not the item it is bound to.
 var ownFire=0,ownPen=0,mine=data(item).modifiers;for(var om=0;om<mine.length;om++){ownFire+=mine[om].values['resistance.fire']||0;ownPen+=mine[om].values['penetration.fire']||0;}
 var generic=item.copyOf();items.push(generic);var copyOwn=generic.fetchEffect('Prop_ItemModifiers').combatStats();
 check('fresh-generic-copy-grants-nothing',String(CM.core.InstanceIds.existing(generic))!==String(CM.core.InstanceIds.existing(item))&&copyOwn.get('resistance.fire')==0&&copyOwn.get('penetration.fire')==0,String(copyOwn.describe()));
 // One made again from saved text (a room reset re-reading its items) keeps the INSTANCEID too: the ID still finds the first item.
 generic.fetchEffect('ExtraData').setStat('INSTANCEID',CM.core.InstanceIds.existing(item));var sameId=String(CM.core.InstanceIds.existing(generic))===String(CM.core.InstanceIds.existing(item));
 copyOwn=generic.fetchEffect('Prop_ItemModifiers').combatStats();var ownOwn=item.fetchEffect('Prop_ItemModifiers').combatStats();
 check('same-id-generic-copy-grants-nothing',sameId&&copyOwn.get('resistance.fire')==0&&copyOwn.get('penetration.fire')==0&&ownOwn.get('resistance.fire')==ownFire&&ownOwn.get('penetration.fire')==ownPen,sameId+' copy '+copyOwn.describe()+' own '+ownOwn.describe());
 // As RandomItems does: AFFIXES on the unplaced copy, which must not throw out of its tick.
 var copyAffixes=-1,copyAffixError='';try{copyAffixes=CM.core.Affixes.apply(generic,1);}catch(e){copyAffixError=String(e);}
 check('generated-affixes-on-a-generic-copy-do-not-escape',copyAffixError===''&&copyAffixes==0,copyAffixError+' '+copyAffixes);
 B.addItem(generic);generic.wearAt(CM.Items.interfaces.Wearable.WORN_TORSO);item.wearAt(CM.Items.interfaces.Wearable.WORN_TORSO);
 var copyStats=null,copyError='';try{copyStats=CP.stats(B,CP.current()).stats;CP.prepare(B,A);CP.prepare(A,B);}catch(e){copyError=String(e);}
 check('generic-copy-grants-nothing-and-combat-goes-on',copyError===''&&copyStats.get('resistance.fire')==0&&copyStats.get('penetration.fire')==0,copyError||String(copyStats.describe()));
 check('record-owner-keeps-its-bonuses-beside-a-copy',CP.stats(A,CP.current()).stats.get('resistance.fire')==ownFire&&CP.stats(A,CP.current()).stats.get('penetration.fire')==ownPen,String(CP.stats(A,CP.current()).stats.describe()));
 var copyDetails=String(generic.fetchEffect('Prop_ItemModifiers').combatStatDetails());check('generic-copy-explains-withheld-bonuses',copyDetails.indexOf('withheld')>=0&&copyDetails.indexOf('another item')>=0,copyDetails);
 var copyFindings=String(IM.checkItems(java.util.Arrays.asList(R),IM.check()));check('generic-copy-diagnosed-by-world-check',copyFindings.indexOf('bound to another item')>=0,copyFindings);
 generic.unWear();B.delItem(generic);
 // Lowering the campaign limit withholds an over-limit item's bonuses instead of failing its wearer's composed combat.
 OM.configure('items.modifiers','max_modifiers','1');var limited=null,limitError='';try{limited=CP.stats(A,CP.current()).stats;CP.prepare(A,B);CP.prepare(B,A);}catch(e){limitError=String(e);}
 check('over-limit-record-degrades-instead-of-failing-combat',limitError===''&&limited.get('resistance.fire')==0&&limited.get('penetration.fire')==0&&mine.length==2,limitError||String(limited.describe()));
 var limitFindings=String(IM.checkItems(java.util.Arrays.asList(R),IM.check()));check('over-limit-record-diagnosed-by-world-check',limitFindings.indexOf('limit of 1')>=0,limitFindings);
 OM.configure('items.modifiers','max_modifiers','6');check('restored-limit-restores-bonuses',CP.stats(A,CP.current()).stats.get('resistance.fire')==ownFire,String(CP.stats(A,CP.current()).stats.describe()));item.unWear();
 var extra=ore(A,3),sourceText=read(affixFile);
 prepare(A,'reroll',item,1);var validLedger=state(A),badLedger=JSON.parse(validLedger);badLedger.receipts='{"01":{}}';OM.put('items.modifiers',OM.character(A),MD.parse(JSON.stringify(badLedger)));draws=CMath.getRandState();weight=extra.basePhyStats().weight();refuse('noncanonical-receipt-key-refused-before-commit',function(){commit(A);});check('malformed-ledger-refusal-does-not-roll-or-charge',draws.equals(CMath.getRandState())&&extra.basePhyStats().weight()==weight,'bad ledger changed dice or costs');OM.put('items.modifiers',OM.character(A),MD.parse(validLedger));IC.cancel(A,0);
 write(affixFile,sourceText.replace('<ROLL','<AFFECT/><ROLL'));check('strict-xml-rejects-empty-effect-child',!IM.check().errors.isEmpty(),'empty AFFECT accepted');write(affixFile,sourceText);
 var operationFile=new java.io.File(root,'itemmods/embertrial.reroll.v1.json'),operationText=read(operationFile),materialCheck=JSON.parse(operationText);materialCheck.material='NOT_A_REGISTERED_MATERIAL';write(operationFile,JSON.stringify(materialCheck));check('definition-check-diagnoses-unregistered-material',!IM.check().errors.isEmpty(),'unknown material accepted');materialCheck.material='WHITE GOLD';write(operationFile,JSON.stringify(materialCheck));check('definition-check-accepts-native-multiword-material',IM.check().errors.isEmpty(),String(IM.check().report()));write(operationFile,operationText);
 var invalidAttributes=['POS="bogus"','POWER="bogus"','CURSED="bogus"','LEVEL="1-2-3"'];for(var ia=0;ia<invalidAttributes.length;ia++){var invalidAttribute=invalidAttributes[ia],attr=String(invalidAttribute).split('=')[0],badText=sourceText.replace('TRACKED="true"','TRACKED="true" '+invalidAttribute);if(attr!=='CURSED')badText=badText.replace(new RegExp(attr+'="[^\"]*"'),invalidAttribute);write(affixFile,badText);check('strict-xml-rejects-'+attr, !IM.check().errors.isEmpty(),String(IM.check().report()));}
 // The pin of the affix being edited (Wardwoven), whatever the seed rolled onto the item.
 var wardPin=IR.pin(IM.definitions().find('embertrial.fireguard1',1,'affix'));IM.definitions().pinned(wardPin,'affix');
 write(affixFile,sourceText.replace('NAME="Wardwoven"','NAME="Changed ward"'));IM.reload();
 var oldPinRejected=false,pinError='';try{IM.definitions().pinned(wardPin,'affix');}catch(e){oldPinRejected=true;pinError=String(e);}
 check('same-version-xml-edit-suspends-crafting',oldPinRejected&&pinError.indexOf('changed without a new version')>=0,'edit silently changed pin '+pinError);
 write(affixFile,sourceText);IM.reload();prepare(A,'reroll',item,1);draws=CMath.getRandState();write(new java.io.File(root,'itemmods/bad.v1.json'),'{}');refuse('invalid-reload-atomic',function(){IM.reload();});
 check('failed-reload-keeps-old-accepted-library',IM.definitions().errors.isEmpty()&&draws.equals(CMath.getRandState()),'partial reload');new java.io.File(root,'itemmods/bad.v1.json').delete();IC.cancel(A,0);
 // A MODBASE roll that cannot run yet (a definition fails CHECK, or off the game thread) leaves a once-only roll unspent.
 function coffer(name){var c=CMClass.getItem('GenContainer');c.setName(name);R.addItem(c);items.push(c);var p=CMClass.getAbility('Prop_LootTable');p.setMiscText('FILE=randareas/embertrial-loot.xml ID=embertrial_mantle ON=OPEN');c.addNonUninvokableEffect(p);return p;}
 function openCoffer(p){p.executeMsg(p.affecting(),CMClass.getMsg(A,p.affecting(),null,CMMsg.MSG_OPEN,'<S-NAME> open(s) <T-NAME>.'));var n=0;for(var ci=0;ci<R.numItems();ci++){var I=R.getItem(ci);if(I.container()==p.affecting()){n++;if(items.indexOf(I)<0)items.push(I);}}return n;}
 var badLoot=coffer('a fix coffer'),offLoot=coffer('a fix far coffer');write(new java.io.File(root,'itemmods/bad.v1.json'),'{}');IM.reset();
 var brokenOpen=openCoffer(badLoot);check('failed-modbase-roll-leaves-coffer-unrolled',brokenOpen==0&&String(badLoot.getStat('ROLLED'))==='FALSE'&&String(badLoot.text()).indexOf('ROLLED')<0,brokenOpen+' '+badLoot.text());
 var spawner=CMClass.getMOB('GenMob');spawner.setName('a fix spawner');spawner.basePhyStats().setLevel(1);spawner.recoverPhyStats();actors.push(spawner);
 var spawnLoot=CMClass.getAbility('Prop_LootTable');spawnLoot.setMiscText('FILE=randareas/embertrial-loot.xml ID=embertrial_mantle ON=SPAWN');spawner.addNonUninvokableEffect(spawnLoot);spawner.bringToLife(R,true);
 check('failed-spawn-roll-left-unspent',spawner.numItems()==0&&field(spawnLoot,'lifeRolled')==false&&field(spawnLoot,'unspent')==true,'items '+spawner.numItems());
 new java.io.File(root,'itemmods/bad.v1.json').delete();IM.reset();
 var fixedOpen=openCoffer(badLoot),fixedItem=null;for(var fi=0;fi<R.numItems();fi++)if(R.getItem(fi).container()==badLoot.affecting())fixedItem=R.getItem(fi);
 check('fixed-definitions-let-the-same-coffer-roll',fixedOpen==1&&String(badLoot.getStat('ROLLED'))==='TRUE'&&IM.isManaged(fixedItem),fixedOpen+' '+badLoot.text());
 spawnLoot.okMessage(spawner,CMClass.getMsg(spawner,null,null,CMMsg.MSG_DEATH,null));var spawned=0;for(var sp=0;sp<spawner.numItems();sp++){if(IM.isManaged(spawner.getItem(sp)))spawned++;items.push(spawner.getItem(sp));}
 check('unspent-spawn-roll-made-as-the-mob-dies',spawned==1&&field(spawnLoot,'lifeRolled')==true,'managed items '+spawned);
 var worker=new java.lang.Thread(new java.lang.Runnable({run:function(){openCoffer(offLoot);}}));worker.start();worker.join();
 check('off-game-thread-modbase-roll-leaves-coffer-unrolled',String(offLoot.getStat('ROLLED'))==='FALSE'&&openCoffer(offLoot)==1&&String(offLoot.getStat('ROLLED'))==='TRUE',String(offLoot.text()));
 // CANCEL stays open while crafting is switched off; COMMIT and RECEIPT of a cancelled request say so.
 prepare(A,'reroll',item,1);var cancelledId=JSON.parse(state(A)).serial;OM.configure('items.modifiers','allow_crafting','false');var cancelReply='';
 try{cancelReply=String(IC.cancel(A,cancelledId));}catch(e){cancelReply='refused: '+e;}
 check('cancel-works-while-crafting-is-disabled',cancelReply.indexOf('Cancelled craft #'+cancelledId)>=0&&JSON.parse(state(A)).pending==='',cancelReply);
 var commitCancelled=String(IC.commit(A,cancelledId));check('commit-after-cancel-says-cancelled',commitCancelled.indexOf('was cancelled')>=0&&commitCancelled.indexOf('Already settled')<0,commitCancelled);
 check('receipt-of-cancelled-request-says-cancelled',String(IC.receipt(A,cancelledId)).indexOf('was cancelled')>=0,String(IC.receipt(A,cancelledId)));
 check('commit-of-a-committed-request-still-says-settled',String(IC.commit(A,request)).indexOf('Already settled craft #'+request)>=0,String(IC.commit(A,request)));
 OM.configure('items.modifiers','allow_crafting','true');
 // A partly spent pile whose name shows its weight is renamed for what is left.
 var C=actor('Pile owner'),pileItem=make(C),pile=ore(C,8);CMLib.materials().adjustResourceName(pile);var pileBefore=String(pile.Name());prepare(C,'reroll',pileItem,1);commit(C);
 check('partly-spent-pile-renamed-for-its-weight',pileBefore.indexOf('8#')>=0&&String(pile.Name()).indexOf('7#')>=0&&String(pile.displayText()).indexOf('7#')>=0&&pile.basePhyStats().weight()==7,pileBefore+' -> '+pile.Name()+' / '+pile.displayText());
 // A world that never opts in keeps its legacy affixes.xml: an oversized or linked one is not a CHECK error.
 var bare=Files.createTempDirectory('sud-item-bare-').toFile(),bareAffixes=new java.io.File(bare,'affixes.xml'),pad=new java.lang.StringBuilder(),withModule=current.get(null);
 while(pad.length()<270000)pad.append('<!-- legacy affix notes -->\n');write(bareAffixes,'<AFFIXES>'+legacy+String(pad)+'</AFFIXES>');
 function itemmodFindings(){var f=CM.core.WorldCheck.run(null,java.util.Collections.singleton('ITEMMODS')),out={errors:0,all:[]};for(var k=0;k<f.size();k++){out.all.push(f.get(k).severity+' '+f.get(k).what);if(String(f.get(k).severity)==='ERROR')out.errors++;}return out;}
 try{
  WorldFiles.configure(bare);current.set(null,OM.prepare(MD.load(bare,'',''),null,false));IM.reset();CM.core.Affixes.reload();
  var bareBig=itemmodFindings();check('module-less-world-oversized-affixes-is-no-error',bareAffixes.length()>262144&&!IM.inUse()&&bareBig.errors==0,bareBig.all.join('; '));
  bareAffixes['delete']();Files.createSymbolicLink(bareAffixes.toPath(),affixFile.toPath());var bareLink=itemmodFindings();check('module-less-world-linked-affixes-is-no-error',bareLink.errors==0,bareLink.all.join('; '));
  new java.io.File(bare,'itemmods').mkdirs();var usedLink=itemmodFindings();check('opted-in-world-still-refuses-a-linked-affix-file',IM.inUse()&&usedLink.errors>0&&usedLink.all.join('; ').indexOf('affixes.xml must be a regular world file')>=0,usedLink.all.join('; '));
 }finally{
  WorldFiles.configure(root);current.set(null,withModule);IM.reset();CM.core.Affixes.reload();
  var barePaths=Files.walk(bare.toPath());try{var bareList=barePaths.sorted(java.util.Comparator.reverseOrder()).iterator();while(bareList.hasNext())Files.deleteIfExists(bareList.next());}finally{barePaths.close();}
 }
 check('receipt-retained-through-module-state-roundtrip',JSON.parse(state(A)).settled>0&&String(IC.receipt(A,request)).indexOf('"operation"')>=0,'receipt missing');
}finally{
 CM.core.JSEvents.off('itemcrafted','fix-item-crafted');CM.core.JSEvents.off('itemlost','fix-item-cost-lost');for(var i=0;i<actors.length;i++)actors[i].destroy();for(var j=0;j<items.length;j++)items[j].destroy();R.destroy();
 WorldFiles.configure(originalWorld);known.set(null,oldKnown);current.set(null,oldCampaign);CP.reset();IM.reset();CM.core.Affixes.reload();
 var paths=Files.walk(root.toPath());try{var list=paths.sorted(java.util.Comparator.reverseOrder()).iterator();while(list.hasNext())Files.deleteIfExists(list.next());}finally{paths.close();CM.core.GenSeed.end(seed);}
}
done();
