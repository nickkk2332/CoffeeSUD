// B8: GMODIFY knows exits' and areas' fields, and changes exits, saved with their room.
FIXFILE = "b8-gmodify";
var R0 = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function cmd(words) { var L = new java.util.ArrayList(); for (var i = 0; i < words.length; i++) L.add(words[i]); M.doCommand(L, 0); }
var gm = CMClass.getCommand("GModify");
var fields = "" + call(gm, "getAllStatFields", []);
check("fields", has(fields, "CLOSEDTEXT") && has(fields, "KEYNAME") && has(fields, "AUTHOR") && has(fields, "PLAYERLEVEL"), "the fields have no exit or area ones");

// a door of this room, saved, then renamed by GMODIFY
var d = -1;
for (var i = 0; i < 4; i++) if (R0.rawDoors()[i] == null && R0.getRawExit(i) == null) { d = i; break; }
var R2 = otherRoom();
var X = CMClass.getExit("GenDoor");
X.setName("a fixb8wood door");
R0.rawDoors()[d] = R2;
R0.setRawExit(d, X);
CMLib.database().DBUpdateExits(R0);
CM.core.Unsaved.clear();
cmd(["GMODIFY", "ROOM", "name=[ss]fixb8wood", "CHANGE=name=fixb8stone"]);
var live = M.location().getRawExit(d);
check("renamed", live != null && has(live.name(), "fixb8stone") && !has(live.name(), "fixb8wood"), "the door is " + (live == null ? "gone" : live.name()));
var T = CMClass.getLocale("StdRoom");
var RID = "" + CMLib.map().getExtendedRoomID(M.location());
CMLib.database().DBReadRoomExits(RID, T, false);
var saved = T.getRawExit(d);
check("saved", saved != null && has(saved.name(), "fixb8stone"), "the database has " + (saved == null ? "no door" : saved.name()));
// a search alone changes nothing
var before = "" + M.location().getRawExit(d).name();
cmd(["GMODIFY", "ROOM", "name=[ss]fixb8stone"]);
check("search", ("" + M.location().getRawExit(d).name()) == before, "a search changed the door");

// put the room back
R0 = M.location();
R0.rawDoors()[d] = null;
R0.setRawExit(d, null);
CMLib.database().DBUpdateExits(R0);
CM.core.UndoLog.clear();
CM.core.Unsaved.clear();
done();
