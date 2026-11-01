// B1: SAVE ROOM HOME saves each mob into its own start room: a visitor is left out, a wanderer is kept.
FIXFILE = "b1-saveroom";
var R = M.location();
var R2 = otherRoom();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixsaver");
B.setSavable(false);
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
var save = CMClass.getCommand("Save");
var cmdCls = save.getClass();
var homeMobs = cmdCls.getMethod("homeMobs", java.lang.Class.forName("com.planet_ink.coffee_mud.Locales.interfaces.Room"), java.lang.Class.forName("java.util.List"));

var visitor = newMob("GenMob", R);
visitor.setName("a fixes visiting guard");
visitor.setStartRoom(R2);
var wanderer = newMob("GenMob", R2);
wanderer.setName("a fixes wandering cook");
wanderer.setStartRoom(R);
var skipped = new java.util.ArrayList();
var mine = homeMobs.invoke(null, R, skipped);
check("visitor-out", !mine.contains(visitor) && has(skipped.toString(), "a fixes visiting guard (home " + CMLib.map().getExtendedRoomID(R2) + ")"), "skipped " + skipped);
check("wanderer-in", mine.contains(wanderer), "the wanderer is not saved here");
save.saveRoomHome(B, R);
var said = out();
check("told", has(said, "skipped: a fixes visiting guard (home "), said);
var counts = CMLib.database().DBCountRoomMobsItems(R.roomID());
check("saved", counts[0] == mine.size(), "the room has " + counts[0] + " mobs saved, and " + mine.size() + " are its own");
// put it back
visitor.destroy(); wanderer.destroy();
save.saveRoomHome(B, R);
B.setSession(null);
B.destroy();
done();
