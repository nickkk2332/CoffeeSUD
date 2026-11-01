// S12, after the restore: still wanted for theft, and still owing the fine
FIXFILE = "s12-check";
var A = M.location().getArea();
var B = A.fetchBehavior("Arrest");
var laws = (B == null) ? null : B.legalInfo(A);
// the warrant is back, against the player as they came back; the area's law
// then deals with it by its own rules: the player here is an Archon, whom the
// law pardons, so it may be among the old warrants as "pardoned"
var mine = 0;
if (laws != null) {
	var lists = [laws.warrants(), laws.oldWarrants()];
	for (var l = 0; l < lists.length; l++) for (var it = lists[l].iterator(); it.hasNext();) {
		var W = it.next();
		var crime = "" + W.crime();
		if (W.criminal() == M && (crime == "theft" || crime == "pardoned")) mine++;
	}
}
check("still-wanted", mine == 1, mine + " warrants for theft");
check("still-owes", (B != null) && B.finesOwed(M) == 25.0, "owes " + ((B == null) ? "?" : B.finesOwed(M)));
done();
