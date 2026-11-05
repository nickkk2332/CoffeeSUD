// Project review: combat state, instance identity, accepted assets, and parser/validator parity.
FIXFILE='review-core';
var V=CM.core.Vocabulary,WF=CM.core.save.WorldFiles,WA=CM.core.WorldAssets,JS=CM.core.JSStore,II=CM.core.InstanceIds;
function str(x){return String(x);}
function setfield(obj,name,value){for(var c=obj.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);return;}catch(e){}throw 'no field '+name;}
function prop(h,id,s){var a=CMClass.getAbility(id);a.setMiscText(s);h.addNonUninvokableEffect(a);return a;}
var area=CMClass.getAreaType('StdArea');area.setName('ReviewCore');CMLib.map().addArea(area);
function room(n){var r=CMClass.getLocale('StdRoom');r.setRoomID(area.Name()+'#'+n);r.setArea(area);area.addProperRoom(r);return r;}
var r1=room(1),r2=room(2),n1=newMob('GenMob',r1),n2=newMob('GenMob',r1),item=CMClass.getItem('GenItem'),other=CMClass.getItem('GenItem');
var files={},keys=[];
function put(path,body){if(files[path]===undefined)files[path]={exists:V.file(path).isFile(),body:str(V.read(path))};V.write(path,body);}
function restore(path){if(files[path].exists)V.write(path,files[path].body);else V.file(path)['delete()']();}
try {
 n1.setName('an identical review guard');n2.setName('an identical review guard');n1.setStartRoom(r1);n2.setStartRoom(r1);
 keys.push(str(JS.keyOf(n1)),str(JS.keyOf(n2)));cm.store(n1).secret='first';
 check('same names have independent stores',typeof cm.store(n2).secret=='undefined',JS.keys());
 check('legacy queued mob references reject ambiguity',II.legacy(n1,n2.Name(),CM.MOBS.interfaces.MOB)==null,'picked first duplicate');
 check('legacy target references reject ambiguity',II.legacy(n1,n2.Name(),CM.core.interfaces.Environmental)==null,'rebound to host');
 var originalID=str(II.id(n1));var copy=n1.copyOf();copy.setName('a copied review guard');
 check('copy has new instance identity',str(II.id(copy))!=originalID&&str(II.id(n1))==originalID,'copy reused or erased identity');
 var originalData=n1.fetchEffect('ExtraData'),copyData=copy.fetchEffect('ExtraData');copyData.setStat('REVIEW','copy');
 check('copy metadata is independent',str(originalData.getStat('REVIEW'))!='copy','ExtraData map shared');copy.destroy();
 item.setName('a review token');r1.addItem(item);other.setName('a second review token');r1.addItem(other);
 keys.push(str(JS.keyOf(item)));cm.store(item).secret='portable';r1.delItem(item);r2.addItem(item);item.setName('a renamed review token');
 check('item store follows movement and rename',str(cm.store(item).secret)=='portable',JS.keyOf(item));
 var ref=str(II.reference(n2)),text=str(n2.text()),restored=CMClass.getMOB('GenMob');n2.destroy();restored.setMiscText(text);restored.bringToLife(r1,false);n2=restored;
 check('instance reference resolves restored copy',II.find(ref)==n2&&str(II.reference(n2))==ref,ref);
 var legacy='mob:'+r1.roomID()+'/'+n2.Name();JS.set(legacy,'{legacy:true}');keys.push(legacy);JS.remove(str(JS.keyOf(n2)));
 check('ambiguous legacy data is retained explicitly',typeof cm.store(n2).legacy=='undefined'&&JS.has(legacy),'legacy store silently assigned to duplicate');
 n2.setName('a unique migration review guard');legacy='mob:'+r1.roomID()+'/'+n2.Name();JS.set(legacy,'{legacy:true}');keys.push(legacy);
 check('unique legacy actor remains compatible',II.legacy(n1,n2.Name(),CM.MOBS.interfaces.MOB)==n2,'unique legacy binding lost');
 JS.remove(str(JS.keyOf(n2))); // remove the empty new store, then request an unambiguous old key
 check('unambiguous legacy store migrates',cm.store(n2).legacy===true&&!JS.has(legacy),JS.keys());
 var rotation=CMClass.getBehavior('CombatAbilities');rotation.setParms('proficient OPENER=Skill_Trip +Spell_MagicMissile(EVERY=2 COOLDOWN=4 ONCE)');
 setfield(rotation,'round',new java.lang.Integer(7));setfield(rotation,'tickDown',new java.lang.Integer(3));
 field(rotation,'lastUsedRound').put('Spell_MagicMissile',new java.lang.Integer(6));field(rotation,'rotationLog').add('Spell_MagicMissile@6');
 var rotation2=CMClass.getBehavior('CombatAbilities');rotation2.setParms(rotation.getParms());rotation2.setStat('STATE',rotation.getStat('STATE'));
 check('rotation round and schedule survive',field(rotation2,'round')==7&&field(rotation2,'tickDown')==3,rotation2.getStat('STATE'));
 check('rotation cooldown and ONCE history survive',field(rotation2,'lastUsedRound').get('Spell_MagicMissile')==6&&field(rotation2,'rotationLog').contains('Spell_MagicMissile@6'),rotation2.getStat('STATE'));
 var boss=CMClass.getBehavior('BossPhases');boss.setParms('AT=ROUND 10: IMMUNE 3');setfield(boss,'round',new java.lang.Integer(7));setfield(boss,'engaged',java.lang.Boolean.TRUE);setfield(boss,'immuneFor',new java.lang.Integer(2));
 field(boss,'summoned').add(n2);setfield(boss,'everSummoned',java.lang.Boolean.TRUE);
 var boss2=CMClass.getBehavior('BossPhases');boss2.setParms(boss.getParms());boss2.setStat('STATE',boss.getStat('STATE'));
 check('boss early round and engagement survive',field(boss2,'round')==7&&field(boss2,'engaged')==true,boss2.getStat('STATE'));
 check('boss immunity and summon references survive',field(boss2,'immuneFor')==2&&field(boss2,'pendingSummoned').size()==1&&field(boss2,'everSummoned')==true,boss2.getStat('STATE'));
 ['creation.ini','origins.ini','perks.ini','classes/mobs/ReviewMarker.class','combinations.txt','itemsets.txt','kits/review.kit','encounters/review.enc'].forEach(function(path){var before=str(WF.checksum());put(path,'# review asset\n');check('tracked '+path,str(WF.checksum())!=before,path);restore(path);});
 put('vocab/review-live.ini','REVIEW=old\n');WA.reload();var accepted=str(WF.loadedChecksum());
 put('vocab/review-live.ini','REVIEW=new\n');check('unloaded edit keeps accepted manifest',str(WF.loadedChecksum())==accepted&&str(WF.checksum())!=accepted,'unaccepted edit changed save version');
 WA.reload();check('reload updates accepted manifest',str(WF.loadedManifest().get('vocab/review-live.ini'))==str(WF.fileSums().get('vocab/review-live.ini'))&&str(WF.loadedChecksum())!=accepted,WF.loadedManifest());restore('vocab/review-live.ini');
 var gap='<REVIEW><STOPS><STOP1>'+r1.roomID()+'</STOP1><STOP3>'+r2.roomID()+'</STOP3></STOPS></REVIEW>';
 put('routes/review-gap.xml',gap);var mover=CMClass.getBehavior('MovingRoom');mover.setParms('ROUTE=review-gap.xml LINE=REVIEW');
 check('runtime rejects gapped route',field(mover,'listOfRooms').isEmpty(),field(mover,'listOfRooms'));
 check('validator rejects gapped route',str(CM.core.AuthoringCheck.run('ROUTES',null)).indexOf('expected STOP2')>=0,CM.core.AuthoringCheck.run('ROUTES',null));
 put('routes/review-gap.xml',gap.replace('STOP3','STOP2').replace('STOP3','STOP2'));mover.setParms('ROUTE=review-gap.xml LINE=REVIEW');
 check('runtime accepts valid route',field(mover,'listOfRooms').size()==2,field(mover,'listOfRooms'));
 check('validator accepts valid route',str(CM.core.AuthoringCheck.run('ROUTES',null)).indexOf('review-gap')<0,CM.core.AuthoringCheck.run('ROUTES',null));restore('routes/review-gap.xml');
 prop(item,'Prop_Details','first=One ID=REVIEW_UNIQUE;second=Two ID=REVIEW_DUP');prop(other,'Prop_Details','other=Three ID=REVIEW_DUP');
 var findings=str(CM.core.AuthoringCheck.run('IDS',null));check('validator checks every detail record',findings.indexOf('duplicate Prop_Details ID REVIEW_DUP')>=0&&findings.indexOf('detail 2')>=0,findings);
} finally {for(var p in files)restore(p);keys.forEach(function(k){JS.remove(k);});n1.destroy();n2.destroy();CMLib.map().delArea(area);area.destroy();WA.reload();}
done();
