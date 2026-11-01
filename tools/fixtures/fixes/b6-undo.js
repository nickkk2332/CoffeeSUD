// B6: UNDO puts back what a building command changed (a room made again from its XML, the builder
// waiting outside); UNDO LIST; world/.history; the export's backups, WORLD BACKUPS and RESTOREFILE.
FIXFILE = "b6-undo";
var WF = CM.core.save.WorldFiles;
var UL = CM.core.UndoLog;
var R0 = M.location();
var RID = "" + CMLib.map().getExtendedRoomID(R0);
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
// the builder whose output is kept stands elsewhere, as UNDO makes this room again
var B = newMob("GenMob", otherRoom());
B.setName("Fixb6builder");
B.setSavable(false);
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function run(cmdName, first, words) { var L = new java.util.ArrayList(); L.add(first); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand(cmdName).execute(B, L, 0); return out(); }
function world(words) { return run("WorldCmd", "WORLD", words); }
function undo(words) { return run("UndoCmd", "UNDO", words); }
function cmd(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); M.doCommand(L, 0); }
function list(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
function count(R, id) { var n = 0; for (var e = R.items(); e.hasMoreElements();) { var I = e.nextElement(); if (I != null && ("" + I.ID()) == id) n++; } return n; }
var saveCmd = CMClass.getCommand("Save");
UL.clear();
CM.core.Unsaved.clear();
var worldDir = WF.worldDir();

// CREATE is logged; UNDO takes it back
var before = count(R0, "StdItem");
cmd("CREATE ITEM StdItem");
check("created", count(M.location(), "StdItem") == before + 1, "CREATE made nothing");
var hist = undo("LIST");
check("listed", UL.entries().size() == 1 && has(hist, "1. CREATE ITEM StdItem @" + RID) && has(hist, "1 room)"), hist);
var hdir = new java.io.File(worldDir, ".history");
var files = hdir.listFiles();
var kept = 0;
for (var i = 0; files != null && i < files.length; i++) if (("" + files[i].getName()).endsWith(".undo")) kept++;
check("kept", kept == 1 && new java.io.File(hdir, ".gitignore").isFile(), "world/.history has " + kept + " entries");
var said = undo("");
var R1 = M.location();
check("undone", has(said, "Undone: CREATE ITEM StdItem @" + RID + " (1 room put back).") && count(R1, "StdItem") == before && ("" + CMLib.map().getExtendedRoomID(R1)) == RID,
	said + " / items " + count(R1, "StdItem"));
check("emptied", UL.entries().size() == 0 && has(undo(""), "There is nothing to undo."), "entries left " + UL.entries().size());

// a change that leaves the room as it was is not logged
var modify = CMClass.getCommand("Modify");
UL.begin(M, modify, list("MODIFY ITEM nothing"));
UL.end(modify);
check("nochange", UL.entries().size() == 0, "an unchanged room was logged");
// MODIFY of an item: the room as it was before is put back
var I = CMClass.getItem("GenItem");
I.setName("a fixb6 cup");
R1.addItem(I);
saveCmd.saveChangedRoom(M, R1);
UL.begin(M, modify, list("MODIFY ITEM cup"));
I.setName("a fixb6 broken cup");
UL.end(modify);
check("modify-logged", UL.entries().size() == 1, "MODIFY was not logged");
said = undo("1");
var R2 = M.location();
var cup = R2.findItem(null, "fixb6");
check("modify-undone", cup != null && ("" + cup.name()) == "a fixb6 cup", (cup == null ? "no cup" : cup.name()) + " / " + said);

// the export's backups: a file it writes over is kept first
CMProps.instance().setProperty("BACKUPS", "2");
WF.exportAll();
var path = "areas/new-area.cmare";
var n0 = WF.backupsOf(path).size();
for (var k = 0; k < 3; k++) {
	var J = CMClass.getItem("StdItem");
	R2.addItem(J);
	saveCmd.saveChangedRoom(M, R2);
	WF.exportAll();
	java.lang.Thread.sleep(5);
}
var stamps = WF.backupsOf(path);
check("backups", stamps.size() == 2 && n0 <= 2, "backups " + n0 + " then " + stamps);
said = world("BACKUPS");
check("backups-all", has(said, path + ": 2, the newest " + stamps.get(1)), said);
said = world("BACKUPS new-area");
check("backups-file", has(said, "Backups of " + path) && has(said, "" + stamps.get(0)), said);
var oldest = "" + stamps.get(0);
var backupText = "" + new java.lang.String(java.nio.file.Files.readAllBytes(new java.io.File(new java.io.File(worldDir, ".backup"), "areas~new-area.cmare." + oldest).toPath()), "UTF-8");
said = world("RESTOREFILE new-area " + oldest);
var now = "" + new java.lang.String(java.nio.file.Files.readAllBytes(new java.io.File(worldDir, path).toPath()), "UTF-8");
check("restorefile", now == backupText && has(said, path + " is put back as it was at " + oldest) && WF.changedOutside().containsKey(path), said);
check("restore-none", has(world("RESTOREFILE nosuchfixfile"), "There is no world file 'nosuchfixfile'."), "an unknown file");
CMProps.instance().remove("BACKUPS");

// put the room back as it was: the test's items gone, saved, the file written as the game has it
var gone = [];
for (var e = R2.items(); e.hasMoreElements();) { var X = e.nextElement(); if (X != null && (("" + X.ID()) == "StdItem" || has(X.name(), "fixb6"))) gone.push(X); }
for (var g = 0; g < gone.length; g++) gone[g].destroy();
saveCmd.saveChangedRoom(M, R2);
WF.exportAll(true);
UL.clear();
CM.core.Unsaved.clear();
check("clean", count(M.location(), "StdItem") == before && !WF.changedOutside().containsKey(path), "items " + count(M.location(), "StdItem"));
B.setSession(null);
B.destroy();
done();
