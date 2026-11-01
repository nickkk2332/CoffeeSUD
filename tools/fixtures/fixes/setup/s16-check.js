// S16, after the restore: the crop is back in its room, with its crop item,
// its room and its ticks; the bowl being carved is still being carved
FIXFILE = "s16-check";
var R = CMLib.map().getRoom("New Area#20");
var crop = R.fetchEffect("Farming");
check("back", crop != null, "no crop grows here after the restore");
var act = (crop == null) ? "" : String(crop.getStat("ACTIVITY"));
check("crop-item", act.indexOf('Farming.found" T="ITEM"') >= 0, "activity " + act);
check("crop-room", act.indexOf('Farming.room" T="ROOM">New Area#20<') >= 0, "activity " + act);
check("invoker", (crop != null) && (crop.invoker() == M), "the crop's farmer is " + ((crop == null) ? "" : crop.invoker()));
var carving = M.fetchEffect("Carpentry");
var cact = (carving == null) ? "" : String(carving.getStat("ACTIVITY"));
check("carving", carving != null, "the carving stopped");
check("bowl-made", cact.indexOf('CraftingSkill.buildingI" T="ITEM"') >= 0, "activity " + cact);
check("work-room", cact.indexOf('CommonSkill.activityRoom" T="ROOM">New Area#20<') >= 0, "activity " + cact);
done();
