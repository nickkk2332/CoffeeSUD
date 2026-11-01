// S12, in builder mode: the start area has laws
FIXFILE = "s12-build";
var A = M.location().getArea();
var B = CMClass.getBehavior("Arrest");
B.setParms("");
A.addBehavior(B);
CMLib.database().DBUpdateArea(A.Name(), A);
check("laws", A.fetchBehavior("Arrest") != null, "no Arrest");
done();
