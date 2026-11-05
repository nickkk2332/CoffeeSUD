// B5: WORLD RELOAD of an area (those in it moved out and back, exits into it linked again), a
// script file, a quest's file, the catalog, and the files changed; DEVWATCH's looks.
FIXFILE = "b5-reload";
var WF = CM.core.save.WorldFiles;
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
var R0 = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R0);
B.setName("Fixb5builder");
B.setSavable(false);
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function run(cmdName, first, words) { var L = new java.util.ArrayList(); L.add(first); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand(cmdName).execute(B, L, 0); return out(); }
function world(words) { return run("WorldCmd", "WORLD", words); }
function script(words) { return run("ScriptCmd", "SCRIPT", words); }
function readFile(F) { return "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"); }
function writeFile(F, s) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(s).getBytes("UTF-8")); }
var worldDir = WF.worldDir();
WF.exportAll();

// an area of its own, from the shipped new area's file with another name
var areas = new java.io.File(worldDir, "areas");
var F = new java.io.File(areas, "fixb5-area.cmare");
writeFile(F, readFile(new java.io.File(areas, "new-area.cmare")).replace(/New Area/g, "Fixb5 Area"));
var said = "" + WF.loadAreaFile("areas/fixb5-area.cmare");
var A = CMLib.map().getArea("Fixb5 Area");
check("loaded", A != null && said == "null" && !WF.changedOutside().containsKey("areas/fixb5-area.cmare"), said);
var R1 = CMLib.map().getRoom("Fixb5 Area#0");
var d = -1;
for (var i = 0; i < 4; i++) if (R0.rawDoors()[i] == null && R0.getRawExit(i) == null) { d = i; break; }
R0.rawDoors()[d] = R1;
R0.setRawExit(d, CMClass.getExit("Open"));
R1.bringMobHere(M, false);

// a room's description changed in the file, outside the game
var text = readFile(F);
var m = /<RDESC>([^<]*)<\/RDESC>/.exec(text);
writeFile(F, text.replace("<RDESC>" + m[1] + "</RDESC>", "<RDESC>Fixb5 a description from the file.</RDESC>"));
check("changed", has(world("STATUS"), "areas/fixb5-area.cmare (edited)"), "WORLD STATUS does not list it");
CM.core.Unsaved.mark(R1, "TEST");
said = world("RELOAD Fixb5 Area");
check("refused", has(said, "rooms of Fixb5 Area have unsaved mobs or items, which WORLD RELOAD drops"), said);
CM.core.Unsaved.clear();
said = world("RELOAD Fixb5 Area");
var A2 = CMLib.map().getArea("Fixb5 Area");
var R1b = CMLib.map().getRoom("Fixb5 Area#0");
check("area", has(said, "Fixb5 Area is loaded again from areas/fixb5-area.cmare.") && A2 != null && R1b != null && R1b != R1, said);
var found = false;
for (var e = A2.getProperMap(); e.hasMoreElements();) if (has(e.nextElement().description(), "Fixb5 a description from the file.")) found = true;
check("desc", found, "no room of it has the description from the file");
check("back", M.location() == R1b, "the builder is in " + CMLib.map().getExtendedRoomID(M.location()));
check("relinked", R0.rawDoors()[d] == R1b, "the exit leads to " + R0.rawDoors()[d]);
check("known", !WF.changedOutside().containsKey("areas/fixb5-area.cmare"), "still changed outside the game");
R0.bringMobHere(M, false);
check("unknown", has(world("RELOAD nosuchfixarea"), "There is no area 'nosuchfixarea'."), "an unknown area");

// a script file, read again
var greet = new CMFile(Resources.makeFileResourceName("fixb5/greet.script"), null, CMFile.FLAG_FORCEALLOW);
greet.saveText("FUNCTION_PROG FIXB5\nmpsetvar FIXTEST FIXB5 one\n~\n");
var host = newMob("GenMob", R0);
host.setName("Fixb5host");
scriptOn(host, "LOAD=fixb5/greet.script");
clearVar("FIXB5");
script("CALL Fixb5host FIXB5");
check("script-one", gvar("FIXB5") == "one", "FIXB5 is '" + gvar("FIXB5") + "'");
greet.saveText("FUNCTION_PROG FIXB5\nmpsetvar FIXTEST FIXB5 two\n~\n");
said = world("RELOAD SCRIPT fixb5/greet.script");
clearVar("FIXB5");
script("CALL Fixb5host FIXB5");
check("script-two", gvar("FIXB5") == "two" && has(said, "1 scripts that name fixb5/greet.script are read again."), "FIXB5 is '" + gvar("FIXB5") + "' / " + said);

// a quest, read again from its world file
var QF = new java.io.File(new java.io.File(worldDir, "quests"), "fixb5.quest");
writeFile(QF, "set name fixb5\nset duration 100\n");
var Q = CMClass.getCommon("DefaultQuest");
Q.setScript("set name fixb5\nset duration 100", true);
CMLib.quests().addQuest(Q);
writeFile(QF, "set name fixb5\nset duration 200\n");
said = world("RELOAD QUEST fixb5");
check("quest", Q.duration() == 200 && has(said, "The quest fixb5 is read again from quests/fixb5.quest."), Q.duration() + " / " + said);

// the catalog, in place
var I = CMClass.getItem("GenItem");
I.setName("a fixb5 lantern");
I.setDescription("It has a fixb5 old glow.");
I.recoverPhyStats();
CMLib.catalog().addCatalog(I);
WF.exportAll();
var CF = new java.io.File(worldDir, "catalog.cmare");
var ct = CF.isFile() ? readFile(CF) : "";
check("catalog-file", has(ct, "a fixb5 lantern") && has(ct, "fixb5 old glow"), "catalog.cmare has no lantern");
writeFile(CF, ct.split("fixb5 old glow").join("fixb5 new glow"));
said = world("RELOAD CATALOG");
var cat = CMLib.catalog().getCatalogItem("a fixb5 lantern");
check("catalog", cat != null && has(cat.description(), "fixb5 new glow") && has(said, "The catalog is loaded again from its file."), (cat == null ? "gone" : cat.description()) + " / " + said);

// DEVWATCH's look: a script touched, a world file changed
var pending = CMClass.getCommand("DevWatchCmd").getClass().getMethod("pending");
pending.invoke(null);
var gf = new java.io.File(new java.io.File(worldDir, "resources"), "fixb5/greet.script");
if (!gf.isFile()) gf = new java.io.File("resources/fixb5/greet.script");
gf.setLastModified(gf.lastModified() + 5000);
var p = "" + pending.invoke(null);
check("watch-script", has(p, "WORLD RELOAD SCRIPT fixb5/greet.script") && !has(p, "CHANGED"), p);
writeFile(QF, "set name fixb5\nset duration 300\n");
p = "" + pending.invoke(null);
check("watch-world", has(p, "WORLD RELOAD CHANGED"), p);
// WORLD RELOAD CHANGED does what changed
said = world("RELOAD CHANGED");
check("changed-all", Q.duration() == 300 && has(said, "The quest fixb5 is read again"), Q.duration() + " / " + said);
said = run("DevWatchCmd", "DEVWATCH", "ON 5");
function watching() { return "" + CMClass.getCommand("DevWatchCmd").getClass().getMethod("isOn").invoke(null); }
check("watch-on", has(said, "every 5 seconds") && watching() == "true", said);
said = run("DevWatchCmd", "DEVWATCH", "OFF");
check("watch-off", has(said, "DEVWATCH is off.") && watching() == "false", said);

// put the world back
R0.rawDoors()[d] = null;
R0.setRawExit(d, null);
CMLib.catalog().delCatalog(cat);
CMLib.quests().delQuest(Q);
CMLib.quests().save();
QF["delete"]();
CMLib.map().obliterateMapArea(A2);
F["delete"]();
host.destroy();
gf["delete"]();
WF.markLoaded("resources/fixb5/greet.script");
WF.exportAll();
var left = WF.changedOutside();
check("clean", !left.containsKey("areas/fixb5-area.cmare") && !left.containsKey("quests/fixb5.quest"), "" + left);
B.setSession(null);
B.destroy();
done();
