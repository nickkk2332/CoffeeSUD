// S17, in play: the room north burns (FieryRoom, its title and text
// charred) and gets a lasting effect; a cellar is dug below it, with a key
// in it, which becomes the player's start room; the hut east is destroyed;
// the area's description and climate change
FIXFILE = "s17-play";
var Directions = CM.core.Directions;
var Places = CM.core.interfaces.Places;
var R0 = M.location();
var A = R0.getArea();
var R1 = R0.getRoomInDir(Directions.NORTH);
R1.setDisplayText("A Charred Ruin");
R1.setDescription("Ash covers everything.");
var fire = CMClass.getBehavior("FieryRoom");
fire.setParms("burnticks=40 nofiretext nonpc");
R1.addBehavior(fire);
R1.addNonUninvokableEffect(CMClass.getAbility("Prop_NoTeleport"));
var cellar = CMClass.getLocale("StdRoom");
cellar.setRoomID(A.Name() + "#30");
cellar.setArea(A);
cellar.setDisplayText("A dark cellar");
cellar.setDescription("Damp stone walls.");
R1.setRawExit(Directions.DOWN, CMClass.getExit("Open"));
R1.rawDoors()[Directions.DOWN] = cellar;
cellar.setRawExit(Directions.UP, CMClass.getExit("Open"));
cellar.rawDoors()[Directions.UP] = R1;
var key = CMClass.getItem("GenItem");
key.setName("a cellar key");
key.setDisplayText("A cellar key lies here.");
cellar.addItem(key);
M.setStartRoom(cellar);
var hut = R0.getRoomInDir(Directions.EAST);
check("hut-there", (hut != null) && (hut.displayText() == "A doomed hut"), "no hut to destroy");
CMLib.map().obliterateMapRoom(hut);
A.setDescription("A land of ash.");
A.setClimateType(Places.CLIMASK_DRY);
check("changed", (R1.fetchBehavior("FieryRoom") != null) && (CMLib.map().getRoom(A.Name() + "#40") == null) && (R0.getRoomInDir(Directions.EAST) == null), "the changes did not take");
done();
