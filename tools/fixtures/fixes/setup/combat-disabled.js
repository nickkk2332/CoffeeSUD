FIXFILE="combat-played-disabled";
check("only-child-suspended",!CM.core.OptionalModules.enabled("combat.profiles")&&CM.core.OptionalModules.enabled("abilities.composition"),"wrong module status");
var target=M.location().fetchInhabitant("ProfileTarget");
if(target!=null){var hp=target.curState().getHitPoints(),burn=target.fetchEffect("CompositionBurn");if(burn!=null)burn.tick(target,Tickable.TICKID_MOB);
check("no-disabled-profile-burn",target.curState().getHitPoints()==hp&&target.fetchEffect("CompositionBurn")==null,"disabled effect damaged");}
check("required-handler-preserved",String(CM.core.OptionalModules.snapshot()).indexOf('"requires_handler":"combat.profiles"')>=0,"native marker lost");
done();
