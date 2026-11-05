FIXFILE="composition-played-restored";
var AC=CM.core.AbilityComposition;
var skill=AC.compiled(M,"example.ember");
check("three-supports-restored",skill.supports.size()==3,String(skill.supports));
check("cooldown-restored",Number(AC.remaining(M,"example.ember"))>0,"cooldown lost");
var target=M.location().fetchInhabitant("CinderTarget"),burn=target==null?null:target.fetchEffect("CompositionBurn");
check("target-and-burn-restored",target!=null&&burn!=null,"missing target or effect");
if(burn!=null){
    var data=CM.core.ModuleDefinitions.parse(String(burn.text()));
    check("persistent-source-restored",String(data.get("source"))===String(CM.core.InstanceIds.reference(M)),"source ID changed");
    check("ticks-remain",Number(burn.getStat("TICKDOWN"))>0,"no duration");
    var hp=target.curState().getHitPoints();burn.tick(target,Tickable.TICKID_MOB);
    check("restored-burn-still-deals-damage",target.curState().getHitPoints()<hp,"restored snapshot inert");
    check("restored-burn-correct-invoker",burn.invoker()===M,"wrong kill credit");
}
var mana=M.curState().getMana();
check("restored-cooldown-enforced",!M.fetchAbility("Skill_Compose").invoke(M,CM.core.CMParms.parse("CAST example.ember"),target,false,0)&&M.curState().getMana()==mana,"cooldown bypass");
done();
