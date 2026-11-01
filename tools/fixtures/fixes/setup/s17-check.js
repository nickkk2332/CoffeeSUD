// S17, after the restore: the burnt room, the cellar and its key, the hut
// gone, the area changed, and the start room the cellar
FIXFILE = "s17-check";
var Directions = CM.core.Directions;
var Places = CM.core.interfaces.Places;
var R0 = CMLib.map().getRoom("New Area#0");
var A = R0.getArea();
var R1 = R0.getRoomInDir(Directions.NORTH);
check("charred", (R1 != null) && (R1.displayText() == "A Charred Ruin") && (R1.description() == "Ash covers everything."), "room north is " + ((R1 == null) ? "gone" : R1.displayText()));
var fires = 0;
for (var b = R1.behaviors(); b.hasMoreElements();) if (b.nextElement().ID() == "FieryRoom") fires++;
check("fire-once", fires == 1, fires + " FieryRoom behaviours");
var fire = R1.fetchBehavior("FieryRoom");
var burn = (fire == null) ? "" : String(fire.getStat("STATE"));
var left = CMath.s_int(burn.replace(/.*<BURN>([0-9]+)<\/BURN>.*/, "$1"));
check("burning", (left > 0) && (left < 40), "burn state " + burn);
var props = 0;
for (var e = R1.effects(); e.hasMoreElements();) if (e.nextElement().ID() == "Prop_NoTeleport") props++;
check("effect-once", props == 1, props + " Prop_NoTeleport");
var cellar = CMLib.map().getRoom("New Area#30");
check("cellar", (cellar != null) && (cellar.displayText() == "A dark cellar"), "no cellar");
check("down", (cellar != null) && (R1.getRoomInDir(Directions.DOWN) == cellar) && (cellar.getRoomInDir(Directions.UP) == R1), "the cellar is not linked");
var keys = 0;
if (cellar != null) for (var i = 0; i < cellar.numItems(); i++) if (cellar.getItem(i).Name() == "a cellar key") keys++;
check("key", keys == 1, keys + " keys in the cellar");
check("hut-gone", (CMLib.map().getRoom("New Area#40") == null) && (R0.getRoomInDir(Directions.EAST) == null), "the hut is back");
check("area", (A.description() == "A land of ash.") && (A.getClimateTypeCode() == Places.CLIMASK_DRY), "area: " + A.description() + ", climate " + A.getClimateTypeCode());
check("start", (M.getStartRoom() == cellar) && (cellar != null), "start room " + M.getStartRoom());
done();
