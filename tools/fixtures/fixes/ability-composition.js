FIXFILE = "ability-composition";
var seededBefore=CM.core.GenSeed.begin(42);
var AC=CM.core.AbilityComposition, CD=CM.core.CompositionDefinitions, MD=CM.core.ModuleDefinitions, OM=CM.core.OptionalModules;
var Files=java.nio.file.Files, JString=java.lang.String;
var folder=Files.createTempDirectory("sud-composed-live-").toFile();
var originalWorld=WorldFiles.worldDir();
var currentField=java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current");currentField.setAccessible(true);
var previousCampaign=currentField.get(null);
var knownField=java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums");knownField.setAccessible(true);
var previousKnown=knownField.get(null);
function read(file){return String(new JString(Files.readAllBytes(file.toPath()),"UTF-8"));}
function write(file,obj){Files.write(file.toPath(),new JString(JSON.stringify(obj)).getBytes("UTF-8"));}
function throws(name,fn){var refused=false;try{fn();}catch(e){refused=true;}check(name,refused,"expected refusal");}
function commands(text){return CM.core.CMParms.parse(text);}
function cast(actor,target,auto){return actor.fetchAbility("Skill_Compose").invoke(actor,commands("CAST example.ember"),target,auto||false,0);}
function learn(actor){var a=CMClass.getAbility("Skill_Compose");a.setProficiency(100);actor.addAbility(a);AC.learn(actor,"example.ember");return a;}
function cleanCombat(){for(var i=0;i<actors.length;i++)actors[i].makePeace(true);}
var room=scratchRoom(),actors=[];
function actor(name){var m=newMob("GenMob",room);m.setName(name);m.baseState().setHitPoints(10000);m.baseState().setMana(1000);m.baseState().setMovement(1000);m.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_SAVE_FIRE,-1000);m.recoverCharStats();m.recoverMaxState();m.resetToMaxState();actors.push(m);return m;}
try {
    var example=new java.io.File("../docs/creator/examples/ability-composition");
    var mods=new java.io.File(folder,"modules");mods.mkdirs();
    var defs=new java.io.File(folder,"compositions");defs.mkdirs();
    var moduleJSON=JSON.parse(read(new java.io.File(example,"modules/abilities.composition.json")));
    write(new java.io.File(mods,"abilities.composition.json"),moduleJSON);
    var files=new java.io.File(example,"compositions").listFiles();
    for(var i=0;i<files.length;i++) {
        var raw=JSON.parse(read(files[i]));
        if(raw.kind=="skill")raw.stats.cooldown=20;
        if(raw.id=="example.burning")raw.modifiers[2].value=100;
        write(new java.io.File(defs,String(files[i].getName())),raw);
    }
    var plan=MD.load(folder,"abilities.composition","");check("manifest-valid",plan.errors.isEmpty(),String(plan.problems()));
    currentField.set(null,OM.prepare(plan,null,false));WorldFiles.configure(folder);AC.reset();
    var nativeSnapshot=String(OM.snapshot());
    check("native-handler-required-by-save",nativeSnapshot.indexOf('"requires_handler":"abilities.composition"')>=0,"missing native capability marker");
    throws("unknown-native-handler-refuses-restore",function(){OM.prepare(plan,nativeSnapshot.replace('"requires_handler":"abilities.composition"','"requires_handler":"missing.native"'),true);});
    throws("native-handler-marker-cannot-be-cleared",function(){OM.prepare(plan,nativeSnapshot.replace('"requires_handler":"abilities.composition"','"requires_handler":""'),true);});
    var invalidContract=JSON.parse(JSON.stringify(moduleJSON));invalidContract.state.cooldowns.max_length=10;
    write(new java.io.File(mods,"abilities.composition.json"),invalidContract);
    check("native-state-contract-validated",!MD.load(folder,"abilities.composition","").errors.isEmpty(),"undersized native state contract accepted");
    write(new java.io.File(mods,"abilities.composition.json"),moduleJSON);
    check("native-adapter-registered",CMClass.getAbility("Skill_Compose")!=null&&CMClass.getAbility("CompositionBurn")!=null,"ability registration");
    var alice=actor("Composer Alice"), bob=actor("Composer Bob"),victim=actor("Target One"),second=actor("Target Two"),third=actor("Target Three"),fourth=actor("Target Four");
    throws("cannot-self-grant-wrapper",function(){AC.learn(alice,"example.ember");});
    var skill=learn(alice);learn(bob);
    var snap=String(OM.snapshot());AC.list(alice);AC.show(alice,"example.ember");
    check("inspection-is-read-only",String(OM.snapshot())===snap,"inspection changed durable module state");
    AC.link(alice,"example.ember","example.chain");AC.link(alice,"example.ember","example.power");AC.link(alice,"example.ember","example.burning");
    var all=AC.compiled(alice,"example.ember");
    check("character-support-isolation",all.supports.size()==3&&AC.compiled(bob,"example.ember").supports.isEmpty(),"loadout leaked");
    check("scheduler-uses-modified-actions",Number(skill.checkedCastingCost(alice,commands("COMPOSE CAST example.ember target")))===1.25,"action cost");
    check("scheduler-accepts-uppercase-ids",Number(skill.checkedCastingCost(alice,commands("COMPOSE CAST EXAMPLE.EMBER target")))===1.25,"uppercase ID action cost");
    check("configuration-has-no-action-cost",Number(skill.checkedCastingCost(alice,commands("COMPOSE SHOW example.ember")))===0,"show cost");
    var before=String(OM.snapshot());throws("duplicate-support-atomic",function(){AC.link(alice,"example.ember","example.power");});
    check("failed-link-preserves-state",String(OM.snapshot())===before,"partial link persisted");
    // Put the other composer in the caster's group: a chain must skip them.
    bob.setFollowing(alice);var mana=alice.curState().getMana(),bobHP=bob.curState().getHitPoints();
    check("cast-accepted",skill.invoke(alice,commands("CAST EXAMPLE.EMBER"),victim,false,0),"native cast failed");
    check("cost-paid-once-for-chain",alice.curState().getMana()==mana-15,String(alice.curState().getMana()));
    check("three-distinct-targets-only",victim.curState().getHitPoints()<10000&&second.curState().getHitPoints()<10000&&third.curState().getHitPoints()<10000&&fourth.curState().getHitPoints()==10000,"chain target limit");
    check("chain-skips-group",bob.curState().getHitPoints()==bobHP,"chain struck ally");
    var manaAfter=alice.curState().getMana(),hpAfter=victim.curState().getHitPoints();
    check("cooldown-rejects-without-cost",!cast(alice,victim)&&alice.curState().getMana()==manaAfter&&victim.curState().getHitPoints()==hpAfter,"repeated cast charged or hit");
    check("cooldown-is-character-specific",Number(AC.remaining(alice,"example.ember"))>0&&Number(AC.remaining(bob,"example.ember"))==0,"cooldown leaked");
    var burn=victim.fetchEffect("CompositionBurn");check("burn-applied",burn!=null,"missing secondary effect");
    if(burn!=null) {
        var text=String(burn.text());AC.unlink(alice,"example.ember","example.burning");
        check("removal-does-not-rewrite-inflight-effect",String(burn.text())===text,"inflight snapshot changed");
        var burnHP=victim.curState().getHitPoints();burn.tick(victim,Tickable.TICKID_MOB);
        check("burn-tick-deals-damage",victim.curState().getHitPoints()<burnHP,"no burn damage");
        burn.tick(victim,Tickable.TICKID_MOB);burn.tick(victim,Tickable.TICKID_MOB);
        check("burn-expires-exactly",victim.fetchEffect("CompositionBurn")==null,"lingering effect remains");
    }
    check("support-removal-recalculates",Number(AC.compiled(alice,"example.ember").value("mana"))===11.25,"cost not recalculated");
    AC.forget(alice,"example.ember");AC.learn(alice,"example.ember");
    check("forget-relearn-keeps-cooldown",Number(AC.remaining(alice,"example.ember"))>0,"respec reset cooldown");
    // Module restore exercises actual typed persisted records without cached actor/loadout state.
    AC.link(bob,"example.ember","example.power");var saved=String(OM.snapshot());
    currentField.set(null,OM.prepare(plan,saved,true));AC.reset();
    check("snapshot-restores-loadout-and-cooldown",AC.compiled(bob,"example.ember").supports.size()==1&&Number(AC.remaining(alice,"example.ember"))>0,"state restore");
    var budgetCaster=actor("Budget caster");learn(budgetCaster);AC.link(budgetCaster,"example.ember","example.chain");AC.link(budgetCaster,"example.ember","example.burning");
    OM.configure("abilities.composition","event_budget","1");var otherHP=second.curState().getHitPoints();
    check("event-budget-bounds-hits-and-reactions",cast(budgetCaster,victim)&&second.curState().getHitPoints()==otherHP&&victim.fetchEffect("CompositionBurn")==null,"budget overrun");
    OM.configure("abilities.composition","event_budget","32");OM.configure("abilities.composition","max_chains","0");
    var noChainCaster=actor("No chain caster");learn(noChainCaster);AC.link(noChainCaster,"example.ember","example.chain");otherHP=second.curState().getHitPoints();
    check("zero-chain-campaign-setting",cast(noChainCaster,victim)&&second.curState().getHitPoints()==otherHP,"zero chain limit ignored");
    OM.configure("abilities.composition","max_chains","4");
    // Native auto/script invocation must still pay the configured cost.
    cleanCombat();callTyped(bob,null,"setFollowing",["MOB"],[null]);var bobMana=bob.curState().getMana();
    check("auto-does-not-bypass-cost",cast(bob,victim,true)&&bob.curState().getMana()==bobMana-9,"auto bypass");
    // Reduced limits suspend the loadout, but allow the player to recover by removal.
    OM.configure("abilities.composition","max_supports","0");throws("reduced-limit-suspends",function(){AC.compiled(bob,"example.ember");});
    AC.unlink(bob,"example.ember","example.power");check("unlink-recovers-after-limit-reduction",AC.compiled(bob,"example.ember").supports.isEmpty(),"removal blocked");
    OM.configure("abilities.composition","max_supports","3");
    // Versioned library updates leave existing bindings pinned; same-version edits suspend them.
    var emberFile=new java.io.File(defs,"example.ember.v1.json"),ember=JSON.parse(read(emberFile));
    var v2=JSON.parse(JSON.stringify(ember));v2.version=2;v2.damage.fire=40;write(new java.io.File(defs,"example.ember.v2.json"),v2);AC.reload();
    check("new-version-does-not-upgrade-existing-binding",AC.compiled(alice,"example.ember").skill.version==1,"silent upgrade");
    AC.learn(bob,"example.ember");check("explicit-upgrade-adopts-latest",AC.compiled(bob,"example.ember").skill.version==2,"upgrade unavailable");
    ember.damage.fire=99;write(emberFile,ember);AC.reload();throws("same-version-edit-suspends",function(){AC.compiled(alice,"example.ember");});
    check("other-version-stays-active",AC.compiled(bob,"example.ember").skill.version==2,"unrelated binding suspended");
    AC.link(bob,"example.ember","example.power");
    new java.io.File(defs,"example.power.v1.json").delete();AC.reload();
    throws("missing-support-suspends",function(){AC.compiled(bob,"example.ember");});
    AC.unlink(bob,"example.ember","example.power");
    check("missing-support-removable",AC.compiled(bob,"example.ember").supports.isEmpty(),"cannot recover missing support");
    var badFile=new java.io.File(defs,"bad.v1.json");write(badFile,{schema:99});
    var knownBefore=String(WorldFiles.loadedManifest());throws("invalid-reload-refused",function(){AC.reload();});
    check("invalid-reload-keeps-library-and-provenance",AC.compiled(bob,"example.ember").skill.version==2&&String(WorldFiles.loadedManifest())===knownBefore,"partial reload");badFile.delete();
    var gated=JSON.parse(JSON.stringify(v2));gated.id="test.gated";gated.version=1;gated.requires_ability="Spell_Lightning";
    write(new java.io.File(defs,"test.gated.v1.json"),gated);AC.reload();
    throws("learned-prerequisite-required",function(){AC.learn(bob,"test.gated");});
    var prerequisite=CMClass.getAbility("Spell_Lightning");bob.addAbility(prerequisite);AC.learn(bob,"test.gated");
    check("learned-prerequisite-unlocks",AC.compiled(bob,"test.gated").skill.id=="test.gated","gate did not open");
    bob.delAbility(prerequisite);throws("lost-prerequisite-suspends",function(){AC.compiled(bob,"test.gated");});bob.addAbility(prerequisite);
    gated.version=2;gated.mask="-LEVEL +>=99";write(new java.io.File(defs,"test.gated.v2.json"),gated);AC.reload();
    throws("character-mask-required",function(){AC.learn(bob,"test.gated");});
    gated.version=3;gated.mask="NO_SUCH_GATE";var badMask=new java.io.File(defs,"test.gated.v3.json");write(badMask,gated);
    check("invalid-mask-diagnosed",!AC.check().errors.isEmpty(),"invalid mask accepted");
    throws("invalid-mask-reload-refused",function(){AC.reload();});badMask.delete();
    check("failed-gate-reload-keeps-pins",AC.compiled(bob,"test.gated").skill.version==1,"gate reload altered binding");
    // No paid invocation while inactive; all already-applied burns stop safely.
    CM.Abilities.Misc.CompositionBurn.apply(bob,victim,5,3,skill);
    check("burn-before-disable",victim.fetchEffect("CompositionBurn")!=null,"missing burn");
    var offPlan=MD.load(folder,"","");currentField.set(null,OM.prepare(offPlan,String(OM.snapshot()),true));
    var hp=victim.curState().getHitPoints(),manaBefore=bob.curState().getMana();
    check("module-disable-blocks-casting",!cast(bob,victim)&&bob.curState().getMana()==manaBefore,"disabled cast");
    var existing=victim.fetchEffect("CompositionBurn");
    if(existing!=null){existing.tick(victim,Tickable.TICKID_MOB);check("disabled-burn-stops",victim.curState().getHitPoints()==hp&&victim.fetchEffect("CompositionBurn")==null,"disabled damage");}
    // Empty saved old campaigns never adopt the newly enabled family.
    currentField.set(null,OM.prepare(plan,null,true));
    check("old-save-needs-explicit-module-adoption",!OM.enabled("abilities.composition"),"old save auto-adopted new combat module");
} finally {
    for(var i=0;i<actors.length;i++)actors[i].destroy();room.destroy();
    WorldFiles.configure(originalWorld);knownField.set(null,previousKnown);currentField.set(null,previousCampaign);AC.reset();
    var stream=Files.walk(folder.toPath());try{var paths=stream.sorted(java.util.Comparator.reverseOrder()).iterator();while(paths.hasNext())Files.deleteIfExists(paths.next());}finally{stream.close();}
    CM.core.GenSeed.end(seededBefore);
}
done();
