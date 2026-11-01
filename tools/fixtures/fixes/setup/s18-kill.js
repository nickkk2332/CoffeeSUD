// S18: a mob of the generated mines is killed, wherever it is
FIXFILE = "s18-kill";
var A = M.location().getArea();
var victim = null;
for (var r = A.getProperMap(); r.hasMoreElements() && (victim == null);) {
	var R = r.nextElement();
	for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).isMonster()) { victim = R.fetchInhabitant(i); break; }
}
check("victim", victim != null, "no mob in the mines");
if (victim != null) victim.destroy();
done();
