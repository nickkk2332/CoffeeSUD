// S18 and S19, in builder mode: a crypt below the start room, an instance
// area (InstanceArea, a 300-tick run) with a lich's lieutenant in its second
// room; and mines west, which generate a dungeon (StdAutoGenInstance)
FIXFILE = "s19-build";
var Directions = CM.core.Directions;
var R0 = M.location();
function area(cls, name) {
	var A = CMClass.getAreaType(cls);
	A.setName(name);
	CMLib.map().addArea(A);
	CMLib.database().DBCreateArea(A);
	return A;
}
function room(A, n, title) {
	var R = CMClass.getLocale("StdRoom");
	R.setRoomID(A.Name() + "#" + n);
	R.setArea(A);
	R.setDisplayText(title);
	R.setDescription(title + ".");
	CMLib.database().DBCreateRoom(R);
	return R;
}
function link(a, dir, b) {
	var op = Directions.getOpDirectionCode(dir);
	a.setRawExit(dir, CMClass.getExit("Open"));
	a.rawDoors()[dir] = b;
	b.setRawExit(op, CMClass.getExit("Open"));
	b.rawDoors()[op] = a;
	CMLib.database().DBUpdateExits(a);
	CMLib.database().DBUpdateExits(b);
}
var crypt = area("StdArea", "Crypt");
var c0 = room(crypt, 0, "A crypt stair");
var c1 = room(crypt, 1, "A crypt corridor");
link(R0, Directions.DOWN, c0);
link(c0, Directions.NORTH, c1);
var lich = CMClass.getMOB("GenMob");
lich.setName("a lich lieutenant");
lich.setDisplayText("A lich lieutenant stands here.");
lich.basePhyStats().setLevel(3);
lich.recoverPhyStats();
lich.setStartRoom(c1);
lich.bringToLife(c1, true);
CMLib.database().DBUpdateMOBs(c1);
var inst = CMClass.getAbility("InstanceArea");
inst.setMiscText("ID=FIXCRYPT DURATION=300");
crypt.addNonUninvokableEffect(inst);
CMLib.database().DBUpdateArea(crypt.Name(), crypt);
var mines = area("StdAutoGenInstance", "Mines");
mines.setStat("GENERATIONFILEPATH", "randareas/example.xml");
mines.setStat("OTHERVARS", "AREA_ID=maze_dungeon areasize=6");
var m0 = room(mines, 0, "A mine entrance");
link(R0, Directions.WEST, m0);
CMLib.database().DBUpdateArea(mines.Name(), mines);
// and a vault south, a dungeon kept for its group however long it is left
// (PERSIST=true), going quiet after a minute with no one in it
var vault = area("StdAutoGenInstance", "Vault");
vault.setStat("GENERATIONFILEPATH", "randareas/example.xml");
vault.setStat("OTHERVARS", "AREA_ID=maze_dungeon areasize=4 PERSIST=true PASSIVEMINS=1");
var v0 = room(vault, 0, "A vault door");
link(R0, Directions.SOUTH, v0);
CMLib.database().DBUpdateArea(vault.Name(), vault);
check("built", (R0.getRoomInDir(Directions.DOWN) == c0) && (R0.getRoomInDir(Directions.WEST) == m0) && (crypt.fetchEffect("InstanceArea") != null), "the crypt or mines are not linked");
done();
