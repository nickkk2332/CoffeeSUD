// S25, in a game built from the world files: the lieutenant's base stats,
// kept there as named attributes of PROP, are as they were
FIXFILE = "s25-lich";
var R = CMLib.map().getRoom("Crypt#1");
var lich = null;
for (var i = 0; (R != null) && (i < R.numInhabitants()); i++) if (R.fetchInhabitant(i).Name() == "a lich lieutenant") lich = R.fetchInhabitant(i);
check("lich", lich != null, "no lieutenant in Crypt#1");
check("level", (lich != null) && (lich.basePhyStats().level() == 3), "level " + ((lich == null) ? "" : lich.basePhyStats().level()));
done();
