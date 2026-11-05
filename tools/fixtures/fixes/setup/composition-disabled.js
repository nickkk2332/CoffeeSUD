FIXFILE="composition-played-disabled";
check("module-suspended",!CM.core.OptionalModules.enabled("abilities.composition"),"module remained active");
var target=M.location().fetchInhabitant("CinderTarget"),mana=M.curState().getMana();
check("inactive-cast-refused",!M.fetchAbility("Skill_Compose").invoke(M,CM.core.CMParms.parse("CAST example.ember"),target,false,0)&&M.curState().getMana()==mana,"inactive cast spent resources");
if(target!=null){var hp=target.curState().getHitPoints(),burn=target.fetchEffect("CompositionBurn");if(burn!=null)burn.tick(target,Tickable.TICKID_MOB);
check("no-disabled-burn-damage",target.curState().getHitPoints()==hp&&target.fetchEffect("CompositionBurn")==null,"disabled effect still damaging");}
done();
