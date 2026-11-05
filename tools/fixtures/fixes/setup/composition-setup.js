FIXFILE="composition-played-setup";
var AC=CM.core.AbilityComposition;
var ability=CMClass.getAbility("Skill_Compose");ability.setProficiency(100);M.addAbility(ability);
M.baseState().setMana(1000);M.baseState().setHitPoints(10000);M.recoverMaxState();M.resetToMaxState();
var target=newMob("GenMob",M.location());target.setName("CinderTarget");target.setDisplayText("CinderTarget stands here.");
target.baseState().setHitPoints(10000);target.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_SAVE_FIRE,-1000);target.recoverCharStats();target.recoverMaxState();target.resetToMaxState();
check("module-enabled",CM.core.OptionalModules.enabled("abilities.composition"),"module off");
check("ordinary-character-not-archon",!CMSecurity.isASysOp(M),"test unexpectedly has all skills");
done();
