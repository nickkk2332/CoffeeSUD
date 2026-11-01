// S19, inside the crypt's instance: the lich's lieutenant is killed
FIXFILE = "s19-play";
var R = M.location();
var A = R.getArea();
check("in-instance", /^[0-9]+_Crypt$/.test(String(A.Name())), "in " + CMLib.map().getExtendedRoomID(R));
var lich = null;
for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == "a lich lieutenant") lich = R.fetchInhabitant(i);
check("lich-here", lich != null, "no lieutenant in " + CMLib.map().getExtendedRoomID(R));
if (lich != null) lich.destroy();
M.tell("FIXINFO crypt room " + CMLib.map().getExtendedRoomID(R));
done();
