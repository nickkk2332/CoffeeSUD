// S15, after the restore: one Gull and one Wren, each with its crew and
// cargo once, the Gull still under way (anchor up, a course, or moved on)
FIXFILE = "s15-check";
function vessels(name) {
	var found = [];
	for (var r = CMLib.map().rooms(); r.hasMoreElements();) {
		var R = r.nextElement();
		for (var i = 0; i < R.numItems(); i++)
			if (R.getItem(i).Name() == name) found.push(R.getItem(i));
	}
	return found;
}
function aboard(V, name) {
	var n = 0;
	for (var r = V.getArea().getProperMap(); r.hasMoreElements();) {
		var R = r.nextElement();
		for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).Name() == name) n++;
		for (var j = 0; j < R.numItems(); j++) if (R.getItem(j).Name() == name) n++;
	}
	return n;
}
var gulls = vessels("the Gull");
var wrens = vessels("the Wren");
check("one-gull", gulls.length == 1, gulls.length + " Gulls");
check("one-wren", wrens.length == 1, wrens.length + " Wrens");
var gull = gulls[0];
var wren = wrens[0];
if (gull != null) {
	check("captain", aboard(gull, "a captain") == 1, aboard(gull, "a captain") + " captains");
	var cap = null;
	for (var r = gull.getArea().getProperMap(); r.hasMoreElements();) {
		var D = r.nextElement();
		for (var i = 0; i < D.numInhabitants(); i++) if (D.fetchInhabitant(i).Name() == "a captain") cap = D.fetchInhabitant(i);
	}
	check("captain-hurt", (cap != null) && (cap.curState().getHitPoints() == 7), "captain hit points " + ((cap == null) ? "" : cap.curState().getHitPoints()));
	check("sailors", aboard(gull, "a sailor") == 2, aboard(gull, "a sailor") + " sailors");
	check("crate", aboard(gull, "a crate of fish") == 1, aboard(gull, "a crate of fish") + " crates");
	var where = CMLib.map().roomLocation(gull);
	var id = CMLib.map().getExtendedRoomID(where);
	check("under-way", (!gull.isAnchorDown()) && ((gull.getCurrentCourse().size() > 0) || (id != "New Area#0")), "anchor " + gull.isAnchorDown() + ", course " + gull.getCurrentCourse() + ", at " + id);
	java.lang.System.setProperty("s15.gull", id);
}
if (wren != null)
	check("deckhand", aboard(wren, "a deckhand") == 1, aboard(wren, "a deckhand") + " deckhands");
done();
