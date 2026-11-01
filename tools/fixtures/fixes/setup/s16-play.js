// S16, before planting: the player farms on the plains east of the start
// room, at the start of the day, in clear weather, with a bundle of wheat;
// and can carve wood, with a piece of oak for a bowl
FIXFILE = "s16-play";
var RawMaterial = CM.Items.interfaces.RawMaterial;
var plains = CMLib.map().getRoom("New Area#20");
plains.bringMobHere(M, false);
var A = plains.getArea();
A.getTimeObj().setHourOfDay(1);
A.getClimateObj().setCurrentWeatherType(0);
A.getClimateObj().setNextWeatherType(0);
var F = CMClass.getAbility("Farming");
F.setProficiency(100);
M.addAbility(F);
var wheat = CMLib.materials().makeResource(RawMaterial.RESOURCE_WHEAT, "" + plains.domainType(), false, null, "");
plains.addItem(wheat); // farming and carving use what is on the ground
var C = CMClass.getAbility("Carpentry");
C.setProficiency(100);
M.addAbility(C);
var oak = CMLib.materials().makeResource(RawMaterial.RESOURCE_OAK, "" + plains.domainType(), false, null, "");
oak.basePhyStats().setWeight(5);
oak.recoverPhyStats();
plains.addItem(oak);
check("ready", (M.location() == plains) && (M.fetchAbility("Farming") != null) && (wheat.owner() == plains) && (oak.owner() == plains), "not ready to farm");
done();
