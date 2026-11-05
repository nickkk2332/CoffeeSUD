// Diagnostic audit probes, run only against the fixes harness's scratch world.
FIXFILE='audit-project-probe';
var V=CM.core.Vocabulary,WF=CM.core.save.WorldFiles,JS=CM.core.JSStore;
function report(k,v){M.tell('AUDIT '+k+'='+v);}
function prop(h,id,s){var a=CMClass.getAbility(id);a.setMiscText(s);h.addNonUninvokableEffect(a);return a;}
function setfield(obj,name,value){for(var c=obj.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);return;}catch(e){}throw 'no field '+name;}
var a=CMClass.getAreaType('StdArea');a.setName('AuditStatusProbe');CMLib.map().addArea(a);
function room(n){var r=CMClass.getLocale('StdRoom');r.setRoomID(a.Name()+'#'+n);r.setArea(a);a.addProperRoom(r);return r;}
var r1=room(1),r2=room(2),n1=newMob('GenMob',r1),n2=newMob('GenMob',r1),item=CMClass.getItem('GenItem'),item2=CMClass.getItem('GenItem');
var files={},keys=[];
function put(path,body){if(files[path]===undefined)files[path]={exists:V.file(path).isFile(),body:String(V.read(path))};V.write(path,body);}
function restore(path){if(files[path].exists)V.write(path,files[path].body);else V.file(path)['delete()']();}
try {
 n1.setName('an identical audit guard');n2.setName('an identical audit guard');n1.setStartRoom(r1);n2.setStartRoom(r1);
 keys.push(String(JS.keyOf(n1)));cm.store(n1).auditSecret='FIRST_NPC';
 report('same_name_npcs_share_js_store',String(cm.store(n2).auditSecret)=='FIRST_NPC');
 report('object_vars_control_distinct',String(CM.core.ObjectVars.hostOf(n1))!=String(CM.core.ObjectVars.hostOf(n2)));
 item.setName('an audit token');r1.addItem(item);item2.setName('another audit token');r1.addItem(item2);
 keys.push(String(JS.keyOf(item)));cm.store(item).auditSecret='ITEM_STATE';r1.delItem(item);r2.addItem(item);keys.push(String(JS.keyOf(item)));
 report('moving_item_loses_js_store',typeof cm.store(item).auditSecret=='undefined');r2.delItem(item);r1.addItem(item);
 report('moving_back_recovers_old_store',String(cm.store(item).auditSecret)=='ITEM_STATE');
 var untracked=[];
 ['creation.ini','origins.ini','perks.ini','panel.txt','combinations.txt','itemsets.txt','kits/audit-probe.kit','encounters/audit-probe.enc'].forEach(function(path){var sum=String(WF.checksum());put(path,'# audit content\n');if(String(WF.checksum())==sum)untracked.push(path);restore(path);});
 report('untracked_author_files',untracked.join(','));
 var base=String(WF.checksum());put('vocab/audit-tracked-control.ini','AUDIT=ok\n');report('vocab_tracking_control',String(WF.checksum())!=base);restore('vocab/audit-tracked-control.ini');
 put('routes/audit-gap.xml','<AUDIT_GAP>\n<STOPS><STOP1>'+r1.roomID()+'</STOP1><STOP3>'+r2.roomID()+'</STOP3></STOPS>\n</AUDIT_GAP>');
 var mover=CMClass.getBehavior('MovingRoom');mover.setParms('ROUTE=audit-gap.xml LINE=AUDIT_GAP WAIT=1 TRAVEL=1');
 report('gapped_route_runtime_stop_count',field(mover,'listOfRooms').size());
 report('gapped_route_validation',String(CM.core.AuthoringCheck.run('ROUTES',null)));restore('routes/audit-gap.xml');
 prop(item,'Prop_Details','first=One ID=AUDIT_UNIQUE;second=Two ID=AUDIT_DUP');prop(item2,'Prop_Details','other=Three ID=AUDIT_DUP');
 report('second_detail_duplicate_id_validation',String(CM.core.AuthoringCheck.run('IDS',null)));
 report('xml_percent_formula',String(CMLib.xml().restoreAngleBrackets('level %20 + 1')));
 var before=String(WF.bootChecksum());put('vocab/audit-live.ini','AUDIT=changed\n');CM.core.WorldAssets.reload();
 report('live_reload_keeps_old_boot_stamp',String(WF.bootChecksum())==before&&String(WF.checksum())!=before);restore('vocab/audit-live.ini');
 var rotation=CMClass.getBehavior('CombatAbilities');rotation.setParms('proficient OPENER=Skill_Trip +Spell_MagicMissile(EVERY=2 COOLDOWN=4)');setfield(rotation,'round',new java.lang.Integer(7));field(rotation,'lastUsedRound').put('Spell_MagicMissile',new java.lang.Integer(6));
 var rotationState=String(rotation.getStat('STATE')),rotation2=CMClass.getBehavior('CombatAbilities');rotation2.setParms(rotation.getParms());rotation2.setStat('STATE',rotationState);
 report('rotation_state_length',rotationState.length);report('rotation_round_before_after','7:'+field(rotation2,'round'));report('rotation_cooldowns_after',field(rotation2,'lastUsedRound').size());
 var boss=CMClass.getBehavior('BossPhases');boss.setParms('AT=ROUND 10: ROOM EMOTE A delayed phase.');setfield(boss,'round',new java.lang.Integer(7));setfield(boss,'engaged',java.lang.Boolean.TRUE);var bossState=String(boss.getStat('STATE')),boss2=CMClass.getBehavior('BossPhases');boss2.setParms(boss.getParms());boss2.setStat('STATE',bossState);
 report('boss_before_first_phase_state_length',bossState.length);report('boss_round_before_after','7:'+field(boss2,'round'));
 field(boss,'fired').add(new java.lang.Integer(0));setfield(boss,'phase',new java.lang.Integer(2));bossState=String(boss.getStat('STATE'));boss2.setStat('STATE',bossState);report('boss_after_phase_control_round',field(boss2,'round'));report('boss_engaged_before_after','true:'+field(boss2,'engaged'));
} finally {for(var p in files)restore(p);keys.forEach(function(k){JS.remove(k);});n1.destroy();n2.destroy();CMLib.map().delArea(a);a.destroy();CM.core.WorldAssets.reload();}
done();
