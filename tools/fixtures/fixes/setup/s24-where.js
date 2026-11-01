// S24: where the station and the cutter are, as one line, and how many of
// each there are
FIXFILE = "s24-where";
var where = { "a derelict station": [], "a patrol cutter": [] };
for (var s = CMLib.space().getSpaceObjects(); s.hasMoreElements();) {
	var O = s.nextElement();
	var n = String(O.Name());
	var c = O.coordinates().toLongs();
	if (where[n] !== undefined) where[n].push(c[0] + "," + c[1] + "," + c[2] + "/" + Math.round(O.speed()));
}
check("one-station", where["a derelict station"].length == 1, where["a derelict station"].length + " stations");
check("one-cutter", where["a patrol cutter"].length == 1, where["a patrol cutter"].length + " cutters");
M.tell("FIXINFO space " + where["a derelict station"].join(";") + " " + where["a patrol cutter"].join(";")); // short, as the game wraps at 80
done();
