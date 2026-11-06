FIXFILE="combat-played-restored";
var CP=CM.core.CombatProfiles,AC=CM.core.AbilityComposition;
check("campaign-selection-restored",String(CP.current().id)==="example.history"&&CP.current().version==1,"selection changed");
check("loadout-and-cooldown-restored",AC.compiled(M,"example.ember").supports.size()==1&&Number(AC.remaining(M,"example.ember"))>0,"composition state lost");
check("old-process-trace-not-restored",String(CP.trace(M,1)).indexOf("No such")>=0,"history crossed process");
var target=M.location().fetchInhabitant("ProfileTarget"),burn=target==null?null:target.fetchEffect("CompositionBurn");
check("target-and-burn-restored",target!=null&&burn!=null,"missing target or effect");
if(burn!=null){
 var data=CM.core.ModuleDefinitions.parse(String(burn.text()));
 check("burn-keeps-original-profile-and-penetration",String(data.get("profile").get("id"))==="example.final"&&Number(data.get("penetration"))==20,"snapshot reinterpreted");
 check("persistent-source-restored",String(data.get("source"))===String(CM.core.InstanceIds.reference(M)),"source identity changed");
 var mail=M.findItem("ProfileMail");check("equipment-property-restored",mail!=null&&CP.stats(M,CP.current()).stats.get("penetration.fire")==20,"equipment stats lost");
 if(mail!=null)mail.unWear();
 check("unequip-removes-future-penetration",CP.stats(M,CP.current()).stats.get("penetration.fire")==0,"stale equipment stats");
 var hp=target.curState().getHitPoints();burn.tick(target,Tickable.TICKID_MOB);
 check("saved-burn-applies-seven-hp",target.curState().getHitPoints()==hp-7,"wrong restored mitigation");
 check("restored-credit-and-trace",burn.invoker()===M&&String(CP.trace(M,1)).indexOf("example.final.v1")>=0&&String(CP.trace(M,1)).indexOf("HP removed 7")>=0,"wrong source or trace");
}
var mana=M.curState().getMana(),dice=CMath.getRandState(),state=String(CM.core.OptionalModules.snapshot());
CMClass.getCommand("CombatResult").execute(M,CM.core.CMParms.parse("COMBATRESULT"),0);
CMClass.getCommand("CombatResult").execute(M,CM.core.CMParms.parse("COMBATRESULT STATS"),0);
check("inspection-no-dice-or-state",dice.equals(CMath.getRandState())&&state===String(CM.core.OptionalModules.snapshot()),"inspection changed state");
check("restored-cooldown-enforced",!M.fetchAbility("Skill_Compose").invoke(M,CM.core.CMParms.parse("CAST example.ember"),target,false,0)&&M.curState().getMana()==mana,"cooldown bypass");
done();
