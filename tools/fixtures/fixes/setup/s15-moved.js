// S15, some turns after the restore: the Gull has flown on east
FIXFILE = "s15-moved";
var gull = null;
for (var r = CMLib.map().rooms(); r.hasMoreElements();) {
	var R = r.nextElement();
	for (var i = 0; i < R.numItems(); i++) if (R.getItem(i).Name() == "the Gull") gull = R.getItem(i);
}
var before = String(java.lang.System.getProperty("s15.gull"));
var now = (gull == null) ? "" : String(CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(gull)));
check("flew-on", (now == "New Area#22") && (before != now), "was at " + before + ", now at " + now);
done();
