// B4: the rooms building commands change are marked unsaved: LIST UNSAVED, SAVE CHANGED, %U,
// MODIFY of a mob or item, and BUILDERAUTOSAVE=1.
FIXFILE = "b4-unsaved";
var U = CM.core.Unsaved;
var R = M.location();
var RID = "" + CMLib.map().getExtendedRoomID(R);
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function cmd(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); M.doCommand(L, 0); }
function list(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
var saveCmd = CMClass.getCommand("Save");
var created = [];
function itemsNow() { var s = new java.util.HashSet(); for (var e = R.items(); e.hasMoreElements();) s.add(e.nextElement()); return s; }
function createItem() { var was = itemsNow(); cmd("CREATE ITEM StdItem"); var made = null; for (var e = R.items(); e.hasMoreElements();) { var I = e.nextElement(); if (!was.contains(I)) { made = I; created.push(I); } } return made; }
// to start, the room as it is saved
saveCmd.saveChangedRoom(M, R);
U.clear();
check("start", U.count() == 0 && has(U.report(), "No room has unsaved mobs or items."), U.report());

// CREATE marks the room, with the change in its counts
var before = CMLib.database().DBCountRoomMobsItems(RID);
var made = createItem();
check("made", made != null, "CREATE ITEM made nothing here");
var report = U.report();
check("marked", U.count() == 1 && has(report, RID + ": CREATE (+1 item)"), report);
check("summary", has(U.summary(5), RID + " (+1 item)"), "" + U.summary(5));
check("prompt", ("" + CMLib.utensils().buildPrompt(M, "[%U]")).indexOf("[1]") >= 0, "" + CMLib.utensils().buildPrompt(M, "[%U]"));
// a command that is no building command marks nothing more
cmd("LOOK");
check("look", U.count() == 1, "LOOK marked a room");
// SAVE CHANGED saves exactly those
cmd("SAVE CHANGED");
var after = CMLib.database().DBCountRoomMobsItems(RID);
check("saved", U.count() == 0 && after[1] == before[1] + 1, "unsaved " + U.count() + ", items saved " + before[1] + " then " + after[1]);

// MODIFY of a mob or item marks the builder's room, as it changes no count
var modify = CMClass.getCommand("Modify");
U.begin(M, modify, list("MODIFY ITEM"));
U.end(M, modify);
check("modify", U.count() == 1 && has(U.report(), RID + ": MODIFY (changed)"), U.report());
U.begin(M, modify, list("MODIFY ROOM"));
U.end(M, modify);
check("modify-room", U.count() == 1, "MODIFY ROOM counted");
U.clear();
U.begin(M, modify, list("MODIFY ROOM"));
U.end(M, modify);
check("room-saves-itself", U.count() == 0, "MODIFY ROOM marked the room");

// BUILDERAUTOSAVE=1: saved after each command
CMProps.instance().setProperty("BUILDERAUTOSAVE", "1");
createItem();
var after2 = CMLib.database().DBCountRoomMobsItems(RID);
check("autosave", U.count() == 0 && after2[1] == after[1] + 1, "unsaved " + U.count() + ", items " + after[1] + " then " + after2[1]);
CMProps.instance().remove("BUILDERAUTOSAVE");

// put the room back as it was saved
for (var i = 0; i < created.length; i++) created[i].destroy();
saveCmd.saveChangedRoom(M, R);
U.clear();
var end = CMLib.database().DBCountRoomMobsItems(RID);
check("restored", end[1] == before[1] && U.count() == 0, "items " + before[1] + " at the start, " + end[1] + " at the end");
done();
