// B11: RUNFILE runs world/macros/NAME.cmd with its args; JRUN has a builder object.
FIXFILE = "b11-macros";
var WF = CM.core.save.WorldFiles;
var R0 = M.location();
var RID = "" + CMLib.map().getExtendedRoomID(R0);
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function list(a) { var L = new java.util.ArrayList(); for (var i = 0; i < a.length; i++) L.add(a[i]); return L; }
function writeFile(F, s) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(s).getBytes("UTF-8")); }
var RF = CMClass.getCommand("RunFileCmd").getClass();
var run = RF.getMethod("run", java.lang.Class.forName("com.planet_ink.coffee_mud.MOBS.interfaces.MOB"), java.lang.Class.forName("java.lang.String"), java.lang.Class.forName("java.util.List"));
var macros = new java.io.File(WF.worldDir(), "macros");

// a macro: its args and $room put in, a line that fails, and on
var F1 = new java.io.File(macros, "fixb11.cmd");
writeFile(F1, "# a fixes macro\nVARS SET FIXTEST FIXB11A $1\nVARS SET FIXTEST FIXB11B $room\n\nNOSUCHFIXCMD x\nVARS SET FIXTEST FIXB11C $2\n");
["FIXB11A", "FIXB11B", "FIXB11C", "FIXB11D", "FIXB11J"].forEach(clearVar);
var said = "" + run.invoke(null, M, "fixb11", list(["one", "two"]));
check("vars", gvar("FIXB11A") == "one" && gvar("FIXB11B") == RID && gvar("FIXB11C") == "two", gvar("FIXB11A") + "/" + gvar("FIXB11B") + "/" + gvar("FIXB11C"));
check("summary", has(said, "RUNFILE fixb11: 4 lines run, 1 failed:") && has(said, "line 5 'NOSUCHFIXCMD x': no command 'NOSUCHFIXCMD' you may use"), said);
// STOPONERROR stops at the first that fails
var F2 = new java.io.File(macros, "fixb11stop.cmd");
writeFile(F2, "STOPONERROR\nNOSUCHFIXCMD\nVARS SET FIXTEST FIXB11D yes\n");
said = "" + run.invoke(null, M, "fixb11stop", list([]));
check("stop", gvar("FIXB11D") == "" && has(said, "1 lines run, 1 failed, and it stopped there (STOPONERROR)"), said);
check("none", has("" + run.invoke(null, M, "nosuchfixmacro", list([])), "There is no world/macros/nosuchfixmacro.cmd."), "an unknown macro");
F1["delete"]();
F2["delete"]();
macros["delete"]();

// JRUN's builder: dig a room, put an item in it, save it, count the area's rooms, run a command
var free = -1;
for (var i = 0; i < 4; i++) if (R0.rawDoors()[i] == null && R0.getRawExit(i) == null) { free = i; break; }
var dn = "" + CMLib.directions().getDirectionName(free);
var js = "var R = builder.here();\n"
	+ "var N = builder.dig('" + dn + "', 'StdRoom');\n"
	+ "var I = builder.create('item', 'StdItem', '' + N.roomID());\n"
	+ "builder.saveRoom('' + N.roomID());\n"
	+ "var n = 0;\n"
	+ "builder.forEachRoom('' + R.getArea().Name(), function(r) { n++; });\n"
	+ "builder.cmd('VARS SET FIXTEST FIXB11J ' + n);\n";
new CM.core.CMFile(CM.core.Resources.makeFileResourceName("fixb11/build.js"), null, CM.core.CMFile.FLAG_FORCEALLOW).saveText(js);
CMClass.getCommand("JRun").execute(M, list(["JRUN", "resources/fixb11/build.js"]), 0);
var N = R0.rawDoors()[free];
var item = null;
if (N != null) for (var e = N.items(); e.hasMoreElements();) { var X = e.nextElement(); if (("" + X.ID()) == "StdItem") item = X; }
check("builder", N != null && item != null && N.hasTag("TODO") && CMath.s_int(gvar("FIXB11J")) > 1, "room " + N + ", item " + item + ", rooms " + gvar("FIXB11J"));
var counts = (N == null) ? [0, 0] : CMLib.database().DBCountRoomMobsItems("" + CMLib.map().getExtendedRoomID(N));
check("saved", counts[1] == 1, "the room has " + counts[1] + " items saved");
if (N != null) CMLib.map().obliterateMapRoom(N);
new java.io.File(new java.io.File(new java.io.File(WF.worldDir(), "resources"), "fixb11"), "build.js")["delete"]();
new java.io.File("resources/fixb11/build.js")["delete"]();
CM.core.Unsaved.clear();
CM.core.UndoLog.clear();
done();
