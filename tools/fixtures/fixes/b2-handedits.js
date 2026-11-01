// B2: a world file changed outside the game is not written over: what SAVE would write goes beside
// it, WORLD STATUS and WORLD DIFF show it, and SAVE FORCE writes over it.
FIXFILE = "b2-handedits";
var WF = CM.core.save.WorldFiles;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixhandbuilder");
B.setSavable(false);
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function world(words) { var L = new java.util.ArrayList(); L.add("WORLD"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("WorldCmd").execute(B, L, 0); return out(); }
function readFile(F) { return "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"); }
function writeFile(F, s) { java.nio.file.Files.write(F.toPath(), new java.lang.String(s).getBytes("UTF-8")); }
var save = CMClass.getCommand("Save");
var rep = save.getClass().getDeclaredMethod("reportWorldFiles", java.lang.Class.forName("com.planet_ink.coffee_mud.MOBS.interfaces.MOB"), java.lang.Integer.TYPE);
rep.setAccessible(true);
function report(n) { rep.invoke(save, B, new java.lang.Integer(n)); return out(); }

var worldDir = WF.worldDir();
var F = new java.io.File(new java.io.File(worldDir, "areas"), "new-area.cmare");
var copy = new java.io.File(F.getPath() + ".builder");
var sum = new java.io.File(CM.core.save.SaveGame.databaseDir(), "world.sum");
// to start, the files as the game has them
var n = WF.exportAll();
check("start", n >= 0 && WF.keptOutside().isEmpty(), "the first export: " + n + ", kept " + WF.keptOutside());
var status = world("STATUS");
check("start-status", has(status, "No world file was changed outside the game"), status);
var game = readFile(F);

// edited by hand: SAVE leaves it, and writes the game's copy beside it
var m = /<RDESC>([^<]*)<\/RDESC>/.exec(game);
var orig = m ? m[1] : "";
var edited = game.replace("<RDESC>" + orig + "</RDESC>", "<RDESC>Fixb2 a stone room written by hand.</RDESC>");
check("edit-made", orig.length > 0 && edited != game, "no room text in the file");
writeFile(F, edited);
status = world("STATUS");
check("status-edited", has(status, "areas/new-area.cmare (edited)")
	&& has(status, "SAVE would leave, as they were changed outside the game: areas/new-area.cmare"), status);
var diff = world("DIFF new-area");
check("diff", has(diff, "areas/new-area.cmare (changed outside the game") && has(diff, "- <RDESC>Fixb2 a stone room written by hand.</RDESC>")
	&& has(diff, "+ <RDESC>" + orig.substring(0, 40)), diff);
check("diff-none", has(world("DIFF nosuchfixfile"), "There is no world file 'nosuchfixfile'."), "an unknown file");
n = WF.exportAll();
check("kept", readFile(F) == edited && copy.isFile() && readFile(copy) == game && WF.keptOutside().contains("areas/new-area.cmare"),
	"after SAVE: kept " + WF.keptOutside() + ", copy " + copy.isFile());
check("sum-gone", !sum.isFile(), "world.sum is still there, so the next game would not load the edit");
var said = report(n);
check("told", has(said, "Not written, as they were changed outside the game: areas/new-area.cmare.") && has(said, "SAVE FORCE writes over them"), said);
WF.exportAll();
check("kept-again", readFile(F) == edited && WF.keptOutside().contains("areas/new-area.cmare"), "the second SAVE: kept " + WF.keptOutside());
// SAVE FORCE writes over it
WF.exportAll(true);
check("force", readFile(F) == game && !copy.isFile() && WF.keptOutside().isEmpty() && sum.isFile(),
	"after SAVE FORCE: copy " + copy.isFile() + ", kept " + WF.keptOutside() + ", sum " + sum.isFile());
check("force-status", !has(world("STATUS"), "areas/new-area.cmare"), "still listed");

// added by hand: SAVE does not delete it
var Q = new java.io.File(new java.io.File(worldDir, "quests"), "fixb2.quest");
writeFile(Q, "set name fixb2\nset duration 100\n");
status = world("STATUS");
check("status-added", has(status, "quests/fixb2.quest (added)") && has(status, "changed outside the game: quests/fixb2.quest"), status);
WF.exportAll();
check("added-kept", Q.isFile() && !new java.io.File(Q.getPath() + ".builder").isFile() && WF.keptOutside().contains("quests/fixb2.quest"),
	"after SAVE: " + Q.isFile() + ", kept " + WF.keptOutside());
said = world("DIFF fixb2");
check("diff-delete", has(said, "quests/fixb2.quest (changed outside the game") && has(said, "SAVE would delete it, all 3 lines."), said);
Q["delete"]();
WF.exportAll();
check("added-gone", WF.keptOutside().isEmpty(), "kept " + WF.keptOutside());

// deleted by hand: SAVE does not make it again
F["delete"]();
status = world("STATUS");
check("status-deleted", has(status, "areas/new-area.cmare (deleted)"), status);
WF.exportAll();
check("deleted-kept", !F.isFile() && copy.isFile() && readFile(copy) == game, "after SAVE: " + F.isFile() + ", copy " + copy.isFile());
said = world("DIFF areas/new-area.cmare");
check("diff-gone", has(said, "not on disk; SAVE would write it"), said);
WF.exportAll(true);
check("restored", F.isFile() && readFile(F) == game && !copy.isFile() && WF.keptOutside().isEmpty() && sum.isFile(),
	"after SAVE FORCE: " + F.isFile() + ", copy " + copy.isFile());

// the line diff
var ld = CMClass.getCommand("WorldCmd").getClass().getMethod("lineDiff", java.lang.Class.forName("java.lang.String"), java.lang.Class.forName("java.lang.String"), java.lang.Integer.TYPE);
var d = "" + ld.invoke(null, "a\nb\nc\nd\n", "a\nB\nc\nd\ne\n", new java.lang.Integer(50));
check("linediff", d == "[line 2:, - b, + B, line 5:, + e]", d);
B.setSession(null);
B.destroy();
done();
