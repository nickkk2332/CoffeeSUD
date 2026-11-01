// DT26: NOTE writes playtest notes outside the world and the saves; NOTES lists, marks and goes to them.
FIXFILE = "dt26-notes";
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixnoter");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function run(words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("NoteCmd").execute(B, L, 0); return out(); }
// a notes file of the test's own, so the real one is left alone
var cmdCls = CMClass.getCommand("NoteCmd").getClass();
var F = java.io.File.createTempFile("fixnotes", ".txt");
F["delete"]();
cmdCls.getMethod("setNotesFile", java.lang.Class.forName("java.io.File")).invoke(null, F);

check("empty", has(run("NOTES"), "There are no notes in"), "no notes yet");
var said = run("NOTE the miller mentions a dog that is not in the room");
check("written", has(said, "Note 1 written to "), said);
var text = "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
var roomID = "" + CMLib.map().getExtendedRoomID(R);
check("file", has(text, "[ ] ") && has(text, " | builder mode | Fixnoter | room " + roomID + " (") && has(text, " | the miller mentions a dog that is not in the room"), text);
var dummy = newMob("GenMob", R);
dummy.setName("a fixes dog");
said = run("NOTE HERE the dog is here now");
dummy.destroy();
text = "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
check("here", has(said, "Note 2 written") && has(text, "  " + R.ID() + " in ") && has(text, "  mob a fixes dog (GenMob, level 5)"), text);
said = run("NOTES");
check("list", has(said, "1. [ ] ") && has(said, "2. [ ] ") && has(said, "2 to do, 0 done"), said);
said = run("NOTES DONE 1");
check("done", has(said, "Note 1 is done.") && has(run("NOTES"), "1 to do, 1 done") && !has(run("NOTES"), "1. [x]"), said);
check("all", has(run("NOTES ALL"), "1. [x] "), "NOTES ALL shows the done one");
check("undone", has(run("NOTES UNDONE 1"), "Note 1 is to do again."), "UNDONE");
check("bad-number", has(run("NOTES DONE 9"), "There is no note 9: there are 2."), "a note that is not there");
check("note-list", has(run("NOTE LIST"), "2 to do, 0 done"), "NOTE LIST is NOTES");
// GOTO goes to the note's room
var other = otherRoom();
if (other != null) {
	other.bringMobHere(B, false);
	run("NOTES GOTO 1");
	check("goto", B.location() == R, "after GOTO the noter is in " + CMLib.map().getExtendedRoomID(B.location()));
}

var unset = java.lang.reflect.Array.newInstance(java.lang.Class.forName("java.lang.Object"), 1);
cmdCls.getMethod("setNotesFile", java.lang.Class.forName("java.io.File")).invoke(null, unset);
F["delete"]();
B.setSession(null);
B.destroy();
done();
