FIXFILE="combat-profiles";
var seeded=CM.core.GenSeed.begin(42),CP=CM.core.CombatProfiles,CR=CM.core.CombatRules,AC=CM.core.AbilityComposition,OM=CM.core.OptionalModules,MD=CM.core.ModuleDefinitions;
var Files=java.nio.file.Files,JString=java.lang.String;
var folder=Files.createTempDirectory("sud-combat-live-").toFile(),originalWorld=WorldFiles.worldDir();
var currentField=java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current");currentField.setAccessible(true);var originalCampaign=currentField.get(null);
var knownField=java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums");knownField.setAccessible(true);var originalKnown=knownField.get(null);
function read(f){return String(new JString(Files.readAllBytes(f.toPath()),"UTF-8"));}
function write(f,obj){Files.write(f.toPath(),new JString(JSON.stringify(obj)).getBytes("UTF-8"));}
function throws(name,fn){var refused=false;try{fn();}catch(e){refused=true;}check(name,refused,"expected refusal");}
function copyDir(source,target){target.mkdirs();var files=source.listFiles();for(var i=0;i<files.length;i++)Files.copy(files[i].toPath(),new java.io.File(target,String(files[i].getName())).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
var room=scratchRoom(),actors=[];
function actor(name){var a=newMob("GenMob",room);a.setName(name);a.baseState().setHitPoints(10000);a.baseState().setMana(1000);a.baseState().setMovement(1000);a.recoverMaxState();a.resetToMaxState();actors.push(a);return a;}
function property(host,values){var a=CMClass.getAbility("Prop_CombatStats");a.setMiscText(JSON.stringify(values));host.addNonUninvokableEffect(a);return a;}
function cast(source,target,id){var own=CM.core.GenSeed.begin(1);try{return source.fetchAbility("Skill_Compose").invoke(source,CM.core.CMParms.parse("CAST "+(id||"example.ember")),target,false,0);}finally{CM.core.GenSeed.end(own);}}
function learn(a,id){var skill=a.fetchAbility("Skill_Compose");if(skill==null){skill=CMClass.getAbility("Skill_Compose");skill.setProficiency(100);a.addAbility(skill);}AC.learn(a,id||"example.ember");return skill;}
function wear(a,name,values){var item=CMClass.getItem("GenArmor");item.setName(name);item.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_TORSO);a.addItem(item);property(item,values);item.wearAt(CM.Items.interfaces.Wearable.WORN_TORSO);return item;}
try{
 var comp=new java.io.File("../docs/creator/examples/ability-composition"),pack=new java.io.File("../docs/creator/examples/combat-profiles");
 var modules=new java.io.File(folder,"modules"),defs=new java.io.File(folder,"compositions"),profiles=new java.io.File(folder,"combat");
 copyDir(new java.io.File(comp,"modules"),modules);copyDir(new java.io.File(comp,"compositions"),defs);copyDir(new java.io.File(pack,"modules"),modules);copyDir(new java.io.File(pack,"combat"),profiles);
 var files=profiles.listFiles();for(var i=0;i<files.length;i++){var p=JSON.parse(read(files[i]));p.hit.minimum=100;p.hit.maximum=100;p.block_cap=100;write(files[i],p);}
 var emberFile=new java.io.File(defs,"example.ember.v1.json"),ember=JSON.parse(read(emberFile));ember.stats.cooldown=0;write(emberFile,ember);
 var burningFile=new java.io.File(defs,"example.burning.v1.json"),burning=JSON.parse(read(burningFile));burning.modifiers[0].value=10;burning.modifiers[2].value=100;write(burningFile,burning);
 var cfgFile=new java.io.File(modules,"combat.profiles.json"),cfg=JSON.parse(read(cfgFile));cfg.settings.profile.default=String(MD.json(CR.pin(CR.load(folder).find("example.final",1))));write(cfgFile,cfg);
 var plan=MD.load(folder,"combat.profiles","");check("native-module-and-dependency-valid",plan.errors.isEmpty(),String(plan.problems()));
 currentField.set(null,OM.prepare(plan,null,false));WorldFiles.configure(folder);AC.reset();CP.reset();
 check("native-capability-saved",String(OM.snapshot()).indexOf('"requires_handler":"combat.profiles"')>=0,"missing required engine capability");
 var a=actor("Rule caster"),b=actor("Rule target"),other=actor("Other caster"),skill=learn(a);learn(other);
 a.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_INTELLIGENCE,100);a.recoverCharStats();
 b.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_SAVE_FIRE,1000);b.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_SAVE_MAGIC,1000);b.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_SAVE_SPELLS,1000);b.recoverCharStats();
 var ward=property(b,{schema:1,resistance:{fire:50}}),hp=b.curState().getHitPoints(),mana=a.curState().getMana();
 check("profile-cast",cast(a,b),"cast rejected");
 check("no-double-save-or-spell-fudge",b.curState().getHitPoints()==hp-6,String(b.curState().getHitPoints())+" expected "+(hp-6));
 check("native-resource-cost-once",a.curState().getMana()==mana-6,"resource mismatch");
 var trace=String(CP.trace(a,1));
 check("trace-records-applied-hp",trace.indexOf("HP removed 6")>=0&&trace.indexOf("world-approved 6")>=0,trace);
 check("trace-explains-sources-and-resistance",trace.indexOf("Rule target")>=0&&trace.indexOf("effective 50%")>=0,trace);
 var dice=CMath.getRandState(),saved=String(OM.snapshot()),hpRead=b.curState().getHitPoints();CP.trace(a,1);CP.describeStats(a);AC.show(a,"example.ember");CMClass.getCommand("CombatResult").execute(a,CM.core.CMParms.parse("COMBATRESULT"),0);
 check("inspection-does-not-reroll-or-mutate",dice.equals(CMath.getRandState())&&saved===String(OM.snapshot())&&b.curState().getHitPoints()==hpRead,"inspection changed state");
 check("inspection-has-zero-scheduler-cost",CMClass.getCommand("CombatResult").checkedActionsCost(a,CM.core.CMParms.parse("COMBATRESULT"))==0,"inspection spent an action");
 check("separate-character-history",String(CP.trace(other,1)).indexOf("No such")>=0,"history leaked");
 var armor=wear(b,"Fire ward mail",{schema:1,resistance:{fire:25}});hp=b.curState().getHitPoints();cast(a,b);
 check("worn-equipment-applies",b.curState().getHitPoints()==hp-3,"worn resistance not applied");
 armor.unWear();hp=b.curState().getHitPoints();cast(a,b);
 check("unequip-recomputes",b.curState().getHitPoints()==hp-6,"stale equipped source");
 var pen=wear(a,"Penetration mail",{schema:1,penetration:{fire:20}});hp=b.curState().getHitPoints();cast(a,b);
 check("penetration-after-cap",b.curState().getHitPoints()==hp-8,"penetration order wrong");pen.unWear();
 var crit=property(a,{schema:1,crit_chance:100,crit_multiplier:50});hp=b.curState().getHitPoints();cast(a,b);
 check("critical-applied-once",b.curState().getHitPoints()==hp-12,"critical multiplier incorrect");a.delEffect(crit);
 ward.setMiscText('{"schema":1,"spell_block":100}');hp=b.curState().getHitPoints();cast(a,b);
 check("full-block-no-damage",b.curState().getHitPoints()==hp&&String(CP.trace(a,1)).indexOf("Full block")>=0,"block failed");
 ward.setMiscText('{"schema":1,"immune":["fire"]}');hp=b.curState().getHitPoints();cast(a,b);
 check("typed-immunity-no-damage",b.curState().getHitPoints()==hp&&String(CP.trace(a,1)).indexOf("immune, rejected")>=0,"immunity failed");
 ward.setMiscText('{"schema":2}');mana=a.curState().getMana();check("invalid-primary-stats-before-payment",!cast(a,b)&&a.curState().getMana()==mana,"invalid stats charged");
 var roomList=new java.util.ArrayList();roomList.add(room);check("world-validator-finds-invalid-stats",!CP.checkProperties(roomList).isEmpty(),"invalid property unreported");
 ward.setMiscText('{"schema":1,"resistance":{"fire":50}}');
 pen.wearAt(CM.Items.interfaces.Wearable.WORN_TORSO);AC.link(a,"example.ember","example.burning");cast(a,b);var burn=b.fetchEffect("CompositionBurn");check("profile-burn-applied",burn!=null,"native fire save incorrectly rejected profile burn");
 var burnText=String(burn.text());check("burn-saves-profile-and-offense",burnText.indexOf('"profile":')>=0&&burnText.indexOf('"penetration":20')>=0,burnText);
 pen.unWear();AC.unlink(a,"example.ember","example.burning");CP.use("example.history",1);hp=b.curState().getHitPoints();burn.tick(b,Tickable.TICKID_MOB);
 check("inflight-burn-keeps-old-profile-and-penetration",b.curState().getHitPoints()==hp-7&&String(burn.text())===burnText&&String(CP.trace(a,1)).indexOf("example.final.v1")>=0,"burn was reinterpreted");
 ward.setMiscText('{"schema":1,"immune":["burning"]}');hp=b.curState().getHitPoints();burn.tick(b,Tickable.TICKID_MOB);
 check("gaining-ailment-immunity-removes-burn",b.curState().getHitPoints()==hp&&b.fetchEffect("CompositionBurn")==null,"immune burn persisted");
 var converted=JSON.parse(JSON.stringify(ember));converted.id="test.converted";converted.name="Converted strike";converted.damage={physical:100};converted.stats.mana=0;converted.modifiers=[{op:"convert",from:"physical",to:"fire",value:100},{stat:"damage",op:"increased",value:20},{stat:"damage.physical",op:"increased",value:50},{stat:"damage.fire",op:"increased",value:100}];write(new java.io.File(defs,"test.converted.v1.json"),converted);AC.reload();learn(a,"test.converted");ward.setMiscText('{"schema":1}');
 CP.use("example.final",1);hp=b.curState().getHitPoints();cast(a,b,"test.converted");check("final-type-golden-real-hit",b.curState().getHitPoints()==hp-220,"final conversion golden failed");
 CP.use("example.history",1);hp=b.curState().getHitPoints();cast(a,b,"test.converted");check("history-golden-real-hit",b.curState().getHitPoints()==hp-270,"history conversion golden failed");
 check("skill-sheet-uses-selected-model",String(AC.show(a,"test.converted")).indexOf("damage.fire=270")>=0,"skill sheet showed legacy scaling");
 var file=new java.io.File(profiles,"example.final.v1.json"),v1=JSON.parse(read(file)),v2=JSON.parse(JSON.stringify(v1));v2.version=2;write(new java.io.File(profiles,"example.final.v2.json"),v2);CP.use("example.final",1);CP.reload();
 check("new-file-does-not-upgrade-campaign-pin",CP.current().version==1,"implicit upgrade");CP.use("example.final",0);check("explicit-profile-upgrade",CP.current().version==2,"upgrade unavailable");
 var bad=new java.io.File(profiles,"bad.v1.json");write(bad,{schema:99});var provenance=String(WorldFiles.loadedManifest());throws("failed-reload-rejected",function(){CP.reload();});
 check("failed-reload-atomic",CP.current().version==2&&String(WorldFiles.loadedManifest())===provenance,"partial reload");bad.delete();
 var secondFile=new java.io.File(profiles,"example.final.v2.json"),validText=read(secondFile);secondFile.delete();CP.reload();mana=a.curState().getMana();
 check("missing-profile-suspends-before-cost",!cast(a,b)&&a.curState().getMana()==mana,"missing pin charged or cast");
 Files.write(secondFile.toPath(),new JString(validText).getBytes("UTF-8"));CP.reload();
 var nativeHp=b.curState().getHitPoints(),nativeSeed=CM.core.GenSeed.begin(1);try{CMLib.combat().postDamage(a,b,skill,10,CMMsg.MASK_ALWAYS|CMMsg.TYP_FIRE,CM.Items.interfaces.Weapon.TYPE_BURNING,"Native <DAMAGE> <T-NAME>!");}finally{CM.core.GenSeed.end(nativeSeed);}
 check("legacy-native-path-still-uses-saves",b.curState().getHitPoints()==nativeHp,"profile bypass leaked into unrelated damage");
 ward.setMiscText('{"schema":1,"resistance":{"fire":50}}');
 var intercept="veto",inside=false,nestedUntouched=false;
 var hook=new JavaAdapter(CM.Abilities.StdAbility,{
  ID:function(){return "ProfileApprovalTest";},
  okMessage:function(host,msg){
   if(inside||msg.target()!==b||msg.targetMinor()!=CMMsg.TYP_DAMAGE)return true;
   if(intercept==="veto")return false;
   if(intercept==="redirect"){msg.setTarget(other);return true;}
   if(intercept==="adjust"){msg.setValue(3);return true;}
   inside=true;var before=b.curState().getHitPoints(),innerSeed=CM.core.GenSeed.begin(1);
   try{CMLib.combat().postDamage(a,b,skill,10,CMMsg.MASK_ALWAYS|CMMsg.TYP_FIRE,CM.Items.interfaces.Weapon.TYPE_BURNING,"Nested native <DAMAGE> <T-NAME>!");nestedUntouched=b.curState().getHitPoints()==before;}
   finally{CM.core.GenSeed.end(innerSeed);inside=false;}
   return true;
  }
 });room.addNonUninvokableEffect(hook);
 hp=b.curState().getHitPoints();cast(a,b);trace=String(CP.trace(a,1));
 check("world-damage-veto-retained",b.curState().getHitPoints()==hp&&trace.indexOf("world veto")>=0&&trace.indexOf("HP removed 0")>=0,trace);
 intercept="redirect";var otherHp=other.curState().getHitPoints();cast(a,b);trace=String(CP.trace(a,1));
 check("redirect-refused-without-reusing-mitigation",b.curState().getHitPoints()==hp&&other.curState().getHitPoints()==otherHp&&trace.indexOf("delivery refused")>=0,trace);
 intercept="adjust";cast(a,b);trace=String(CP.trace(a,1));
 check("external-adjustment-and-real-hp-traced",b.curState().getHitPoints()==hp-3&&trace.indexOf("world-approved 3, HP removed 3")>=0,trace);
 intercept="nested";hp=b.curState().getHitPoints();cast(a,b);room.delEffect(hook);
 check("nested-native-damage-does-not-steal-receipt",nestedUntouched&&b.curState().getHitPoints()==hp-6&&String(CP.trace(a,1)).indexOf("HP removed 6")>=0,String(CP.trace(a,1)));
 var pinned=CP.prepare(a,b).pin();CM.Abilities.Misc.CompositionBurn.applyProfile(a,b,10,3,skill,pinned,20);
 burn=b.fetchEffect("CompositionBurn");var previousBurn=burn;
 check("weaker-burn-keeps-stronger-snapshot",!CM.Abilities.Misc.CompositionBurn.applyProfile(a,b,5,3,skill,pinned,0)&&b.fetchEffect("CompositionBurn")===previousBurn,"weaker burn replaced snapshot across class loaders");
 secondFile.delete();CP.reload();hp=b.curState().getHitPoints();burn.tick(b,Tickable.TICKID_MOB);
 check("missing-burn-profile-suspends-one-tick",b.curState().getHitPoints()==hp&&b.fetchEffect("CompositionBurn")!=null&&String(CP.trace(a,1)).indexOf("suspended for this tick")>=0,"missing profile damaged or removed snapshot");
 Files.write(secondFile.toPath(),new JString(validText).getBytes("UTF-8"));CP.reload();burn.tick(b,Tickable.TICKID_MOB);
 check("repaired-burn-profile-resumes",b.curState().getHitPoints()==hp-7,"repaired pin stayed inert");burn.unInvoke();
 for(var recent=0;recent<10;recent++)cast(a,b);
 check("history-bounded-to-eight-events",String(CP.trace(a,8)).indexOf("Combat result #")>=0&&String(CP.trace(a,9)).indexOf("No such")>=0,"history bound incorrect");
 OM.configure("combat.profiles","player_trace","false");throws("creator-can-hide-player-traces",function(){CP.playerAccess();});OM.configure("combat.profiles","player_trace","true");
 var spec=CP.prepare(a,b);CM.Abilities.Misc.CompositionBurn.applyProfile(a,b,10,3,skill,spec.pin(),0);check("pending-burn-before-disable",b.fetchEffect("CompositionBurn")!=null,"missing pending burn");
 var off=MD.load(folder,"abilities.composition","");currentField.set(null,OM.prepare(off,String(OM.snapshot()),true));hp=b.curState().getHitPoints();b.fetchEffect("CompositionBurn").tick(b,Tickable.TICKID_MOB);
 check("disabled-profile-burn-stops",b.curState().getHitPoints()==hp&&b.fetchEffect("CompositionBurn")==null,"disabled burn damaged");
 check("disabled-profile-keeps-stage-2-path",CP.prepare(a,b)==null&&OM.enabled("abilities.composition"),"parent composition disabled");
 check("disabled-native-requirement-preserved",String(OM.snapshot()).indexOf('"requires_handler":"combat.profiles"')>=0,"capability lost on suspension");
 currentField.set(null,OM.prepare(plan,String(OM.snapshot()),true));check("resumed-profile-retains-selected-version",CP.current().version==2,"profile setting lost");
 var doomed=actor("Doomed target");doomed.baseState().setHitPoints(1);doomed.recoverMaxState();doomed.resetToMaxState();cast(a,doomed);trace=String(CP.trace(a,1));
 check("lethal-hit-keeps-native-death-and-actual-hp",doomed.amDead()&&trace.indexOf("HP removed 1")>=0,trace);
 CP.reset();check("history-clears-on-campaign-reset",String(CP.trace(a,1)).indexOf("No such")>=0,"old trace crossed campaigns");
 currentField.set(null,OM.prepare(plan,null,true));check("legacy-save-does-not-auto-adopt-combat",!OM.enabled("combat.profiles"),"legacy save auto adopted");
}finally{
 for(var i=0;i<actors.length;i++)actors[i].destroy();room.destroy();WorldFiles.configure(originalWorld);knownField.set(null,originalKnown);currentField.set(null,originalCampaign);AC.reset();CP.reset();
 var stream=Files.walk(folder.toPath());try{var paths=stream.sorted(java.util.Comparator.reverseOrder()).iterator();while(paths.hasNext())Files.deleteIfExists(paths.next());}finally{stream.close();CM.core.GenSeed.end(seeded);}
}
done();
