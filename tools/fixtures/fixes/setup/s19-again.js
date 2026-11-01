// S19, having walked out of the instance and back in: the same instance
FIXFILE = "s19-again";
var A = M.location().getArea();
check("same-instance", String(A.Name()) == "1_Crypt", "now in " + A.Name());
check("no-second", CMLib.map().getArea("2_Crypt") == null, "a second instance was made");
done();
