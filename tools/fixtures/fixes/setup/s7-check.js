// S7, after the restore: the bell-ringer's own variable is still set
FIXFILE = "s7-check";
var R = M.location();
var ringer = null;
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a bell-ringer") ringer = R.fetchInhabitant(i);
check("ringer", ringer != null, "no bell-ringer");
var B = (ringer == null) ? null : ringer.fetchBehavior("Scriptable");
check("own-variable", (B != null) && ("" + B.getVar(ringer.Name(), "TOLLS")) == "1", "TOLLS is '" + ((B == null) ? "" : B.getVar(ringer.Name(), "TOLLS")) + "'");
done();
