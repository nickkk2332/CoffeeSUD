// DT13: WORLD CHECK finds, by severity, exits to nowhere, doors with no way back, rooms nothing reaches, keys that exist nowhere, default text and empty shops.
FIXFILE = "dt13-worldcheck";
var WC = CM.core.WorldCheck;
var Directions = CM.core.Directions;
function has(list, part) { return ("" + list).indexOf(part) >= 0; }
var A = M.location().getArea();
function room(n, title) {
	var R = CMClass.getLocale("StdRoom");
	R.setRoomID(A.Name() + "#" + n);
	R.setArea(A);
	R.setDisplayText(title);
	R.setDescription("A fixes room for the world check.");
	return R;
}
var K = room(9301, "A fixes vault");
var L = room(9302, "A fixes cell");
var KID = A.Name() + "#9301", LID = A.Name() + "#9302";
// north: an exit to nowhere; east: a locked door, its key nowhere, into a room with no way back
K.setRawExit(Directions.NORTH, CMClass.getExit("Open"));
var door = CMClass.getExit("GenExit");
door.setDoorsNLocks(true, false, true, true, true, true);
door.setKeyName("fixes bone key");
K.setRawExit(Directions.EAST, door);
K.rawDoors()[Directions.EAST] = L;
// the new room's text, a generic mob as made, a shop with nothing, a locked chest
L.setDescription("Brand new database room! You need to change this text with the MODIFY ROOM command.");
var mob = CMClass.getMOB("GenMob");
mob.bringToLife(K, true);
var shop = CMClass.getMOB("GenShopkeeper");
shop.setName("Fixshopkeep");
shop.bringToLife(K, true);
var chest = CMClass.getItem("GenContainer");
chest.setName("a fixes chest");
chest.setDoorsNLocks(true, false, true, true, true, true);
chest.setKeyName("fixes chest key");
K.addItem(chest);

var lines = "" + WC.report(WC.run(A, null));
check("nowhere", has(lines, "[exits] " + KID + " north: an exit to nowhere"), lines);
check("no-way-back", has(lines, "[exits] " + KID + " east: a door into " + LID + ", which has no way back"), lines);
check("door-key", has(lines, "[keys] " + KID + " east: locked with key 'fixes bone key', which exists nowhere"), lines);
check("chest-key", has(lines, "[keys] " + KID + " a fixes chest: locked with key 'fixes chest key', which exists nowhere"), lines);
check("reach", has(lines, "rooms nothing leads to from the start: ") && has(lines, KID), lines);
check("new-room", has(lines, "[text] " + LID + ": the new room's description"), lines);
check("default-name", has(lines, "[text] " + KID + " a generic mob: GenMob's default name"), lines);
check("shop", has(lines, "[shops] " + KID + " Fixshopkeep: a shop with nothing to sell"), lines);
check("order", lines.indexOf("Errors:") >= 0 && lines.indexOf("Errors:") < lines.indexOf("Warnings:"), lines);
// a key anywhere in the world unlocks the finding
var key = CMClass.getItem("GenKey");
key.setKey("fixes chest key");
K.addItem(key);
var keyed = "" + WC.report(WC.run(A, new java.util.TreeSet(java.util.Arrays.asList(["KEYS"]))));
check("key-found", !has(keyed, "fixes chest key") && has(keyed, "fixes bone key"), keyed);
check("only", !has(keyed, "[exits]") && !has(keyed, "[text]"), keyed);

// the command
var B = newMob("GenMob", M.location());
B.setName("Fixworldreader");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var Lw = new java.util.ArrayList(); Lw.add("WORLD"); Lw.add("CHECK"); Lw.add("AREA"); Lw.add("" + A.Name()); Lw.add("ONLY=keys,shops");
CMClass.getCommand("WorldCmd").execute(B, Lw, 0);
var said = "" + field(BS, "bout").toString();
check("cmd", has(said, "WORLD CHECK: 1 errors, 1 warnings.") && has(said, "fixes bone key") && has(said, "Fixshopkeep"), said);
check("start-rooms", WC.startRoomIDs().size() > 0, "no start rooms");

B.setSession(null);
B.destroy();
mob.destroy(); shop.destroy(); chest.destroy(); key.destroy();
A.delProperRoom(K); A.delProperRoom(L);
done();
