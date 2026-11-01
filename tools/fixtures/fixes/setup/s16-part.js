// S16: the carving has a part a save cannot keep (an ability it helps),
// which SAVE says
FIXFILE = "s16-part";
var carving = M.fetchEffect("Carpentry");
check("carving", carving != null, "no carving under way");
if (carving != null) carving.helpingAbility = CMClass.getAbility("Skill_Dodge");
done();
