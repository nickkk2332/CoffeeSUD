// S17, in builder mode: a hut east of the start room, for play to destroy
FIXFILE = "s17-build";
var Directions = CM.core.Directions;
var R = M.location();
var A = R.getArea();
var H = CMClass.getLocale("StdRoom");
H.setRoomID(A.Name() + "#40");
H.setArea(A);
H.setDisplayText("A doomed hut");
H.setDescription("It will not stand long.");
R.setRawExit(Directions.EAST, CMClass.getExit("Open"));
R.rawDoors()[Directions.EAST] = H;
H.setRawExit(Directions.WEST, CMClass.getExit("Open"));
H.rawDoors()[Directions.WEST] = R;
// as builder commands do: the database cache, then the world files at quit
CMLib.database().DBCreateRoom(H);
CMLib.database().DBUpdateExits(R);
CMLib.database().DBUpdateExits(H);
check("hut", R.getRoomInDir(Directions.EAST) == H, "no hut east");
done();
