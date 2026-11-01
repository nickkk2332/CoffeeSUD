// S24, in builder mode: a derelict station, still, and a patrol cutter on a
// course, in space and in no room, written to the world's files at quit
FIXFILE = "s24-build";
var Coord3D = CM.core.collections.Coord3D;
var Dir3D = CM.core.collections.Dir3D;
function at(x, y, z) {
	var a = java.lang.reflect.Array.newInstance(java.lang.Long.TYPE, 3);
	a[0] = x; a[1] = y; a[2] = z;
	return new Coord3D(a);
}
var station = CMClass.getItem("Asteroid");
station.setName("a derelict station");
station.setDisplayText("A derelict station hangs here.");
station.setRadius(100);
CMLib.space().addObjectToSpace(station, at(1000000, 2000000, 3000000));
var cutter = CMClass.getItem("Asteroid");
cutter.setName("a patrol cutter");
cutter.setDisplayText("A patrol cutter flies by.");
cutter.setRadius(50);
cutter.setDirection(new Dir3D(0.5, 1.0));
cutter.setSpeed(1000);
CMLib.space().addObjectToSpace(cutter, at(5000000, 5000000, 5000000));
check("in-space", CMLib.space().isObjectInSpace(station) && CMLib.space().isObjectInSpace(cutter), "not in space");
done();
