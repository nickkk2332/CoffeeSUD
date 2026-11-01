// DT28: VESSEL STATUS shows a vessel as it is; LIST SHIPS has its anchor, course and owner; LIST INSTANCES lists instances.
FIXFILE = "dt28-vessel";
var Directions = CM.core.Directions;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixmariner");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function vessel(words) { var L = new java.util.ArrayList(); L.add("VESSEL"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("VesselCmd").execute(B, L, 0); return out(); }
var cmdCls = CMClass.getCommand("VesselCmd").getClass();

check("usage", has(vessel("STATUS"), "VESSEL STATUS [NAME]"), "no name");
check("unknown", has(vessel("STATUS the Nofixship"), "There is no vessel 'the Nofixship'."), "an unknown vessel");
// a ship, as S15 makes one: docked here, anchor up, on course east twice, with a sailor and an owner
var ship = CMClass.getItem("GenSailingShip");
ship.setName("the Fixgull");
ship.setDisplayText("The Fixgull is here.");
R.addItem(ship);
ship.dockHere(R);
CMLib.map().registerWorldObjectLoaded(R.getArea(), R, ship);
ship.rename("the Fixgull");
var deck = ship.getArea().getProperMap().nextElement();
var sailor = newMob("GenMob", deck);
sailor.setName("a fixes sailor");
sailor.setStartRoom(deck);
ship.setAnchorDown(false);
var course = new java.util.ArrayList();
course.add(java.lang.Integer.valueOf(Directions.EAST));
course.add(java.lang.Integer.valueOf(Directions.EAST));
ship.setCurrentCourse(course);
ship.setOwnerName("Fixowner");
var em = CMClass.getBehavior("Emoter");
em.setParms("min=5 max=9 chance=50;creaks.");
ship.addBehavior(em);

var said = vessel("STATUS Fixgull");
var at = "" + CMLib.map().getExtendedRoomID(R);
check("header", has(said, "Vessel the Fixgull (GenSailingShip):"), said);
check("where", has(said, "docked at " + at), said);
check("course", has(said, "anchor up, course: east, east, top speed "), said);
check("hull", has(said, "Hull: ") && has(said, "% of "), said);
check("owner", has(said, "Owner: Fixowner, home port: "), said);
check("behaviour", has(said, "Behaviour on the vessel: Emoter: "), said);
check("crew", has(said, "Crew (1): a fixes sailor") && has(said, "Passengers (0): none"), said);
// LIST SHIPS' new columns
var cols = cmdCls.getMethod("shipColumns", java.lang.Class.forName("com.planet_ink.coffee_mud.Items.interfaces.Boardable")).invoke(null, ship);
check("columns", ("" + cols[0]) == "up" && ("" + cols[1]) == "east, east" && ("" + cols[2]) == "Fixowner", "columns " + cols[0] + " | " + cols[1] + " | " + cols[2]);
ship.setAnchorDown(true);
cols = cmdCls.getMethod("shipColumns", java.lang.Class.forName("com.planet_ink.coffee_mud.Items.interfaces.Boardable")).invoke(null, ship);
check("anchor-down", ("" + cols[0]) == "down", "anchor " + cols[0]);
// LIST INSTANCES: the test world has none
var inst = "" + cmdCls.getMethod("instances").invoke(null);
check("instances", has(inst, "Instance areas:"), inst);

sailor.destroy();
ship.destroy();
B.setSession(null);
B.destroy();
done();
