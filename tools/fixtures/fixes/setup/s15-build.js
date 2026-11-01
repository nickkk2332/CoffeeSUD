// S15, in builder mode: three plains east of the start room, under the sky,
// for an airship to fly over
FIXFILE = "s15-build";
var Directions = CM.core.Directions;
var R = M.location();
var A = R.getArea();
var prev = R;
var made = [];
for (var i = 0; i < 3; i++) {
	var P = CMClass.getLocale("Plains");
	P.setRoomID(A.Name() + "#" + (20 + i));
	P.setArea(A);
	P.setDisplayText("Open plains " + (i + 1));
	P.setDescription("Grass runs to the sky.");
	prev.setRawExit(Directions.EAST, CMClass.getExit("Open"));
	prev.rawDoors()[Directions.EAST] = P;
	P.setRawExit(Directions.WEST, CMClass.getExit("Open"));
	P.rawDoors()[Directions.WEST] = prev;
	CMLib.database().DBCreateRoom(P);
	made.push(P);
	prev = P;
}
// as builder commands do: the database cache, then the world files at quit
CMLib.database().DBUpdateExits(R);
for (var j = 0; j < made.length; j++) CMLib.database().DBUpdateExits(made[j]);
check("plains", R.getRoomInDir(Directions.EAST) == made[0], "the start room does not lead east");
done();
