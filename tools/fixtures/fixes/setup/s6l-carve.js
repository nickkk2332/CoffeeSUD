// S6: carving, by a recipe whose result is CATALOG:a fix totem, with oak on
// the ground
FIXFILE = "s6l-carve";
var RawMaterial = CM.Items.interfaces.RawMaterial;
var R = M.location();
var C = CMClass.getAbility("Carpentry");
C.setProficiency(100);
M.addAbility(C);
var oak = CMLib.materials().makeResource(RawMaterial.RESOURCE_OAK, "" + R.domainType(), false, null, "");
oak.basePhyStats().setWeight(5);
oak.recoverPhyStats();
R.addItem(oak);
check("ready", oak.owner() == R, "no oak");
done();
