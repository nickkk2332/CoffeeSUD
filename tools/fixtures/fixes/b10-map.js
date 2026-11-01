// B10: WORLD MAP draws the rooms around the builder, or the area, with numbers and marks, and
// writes the area's map as a page of HTML.
FIXFILE = "b10-map";
var DM = CM.core.DigMode;
var Dirs = CM.core.Directions;
var WF = CM.core.save.WorldFiles;
var R0 = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function list(a) { var L = new java.util.ArrayList(); for (var i = 0; i < a.length; i++) L.add(a[i]); return L; }
function free(R) { for (var i = 0; i < 4; i++) if (R.rawDoors()[i] == null && R.getRawExit(i) == null) return i; return -1; }
var WC = CMClass.getCommand("WorldCmd").getClass();
var worldMap = WC.getMethod("worldMap", java.lang.Class.forName("com.planet_ink.coffee_mud.MOBS.interfaces.MOB"), java.lang.Class.forName("java.util.List"));
function map(words) { return "" + worldMap.invoke(null, M, list(words)); }
var made = [];

// two rooms out from here, the second one way only
var d = free(R0);
var dn = "" + CMLib.directions().getDirectionName(d);
DM.path(M, dn, "StdRoom", true, "A fixb10 first", null);
var A = R0.rawDoors()[d];
made.push(A);
DM.path(M, dn + "," + dn, "StdRoom", false, "A fixb10 second", null);
var B = A.rawDoors()[d];
made.push(B);
// and one in the area that nothing leads to
DM.path(M, dn + "," + dn + "," + dn, "StdRoom", true, "A fixb10 lost", null);
var C = B.rawDoors()[d];
made.push(C);
B.rawDoors()[d] = null; B.setRawExit(d, null);
C.rawDoors()[Dirs.getOpDirectionCode(d)] = null; C.setRawExit(Dirs.getOpDirectionCode(d), null);

var plain = map([]);
check("plain", has(plain, "*") && (has(plain, "-") || has(plain, "|")) && has(plain, "o"), plain);
var ids = map(["RADIUS", "2", "IDS"]);
var aid = "" + CMLib.map().getExtendedRoomID(A);
check("ids", has(ids, aid + "  A fixb10 first") && has(ids, "" + CMLib.map().getExtendedRoomID(R0)), ids);
var flags = map(["AREA", "FLAGS"]);
var oneway = (d == Dirs.EAST) ? ">" : (d == Dirs.WEST) ? "<" : (d == Dirs.SOUTH) ? "v" : "^";
check("one-way", has(flags, oneway), flags);
check("unreached", has(flags, "! Not reached from here at all: ") && has(flags, "" + CMLib.map().getExtendedRoomID(C)), flags);
check("bad-word", has(map(["NOSUCH"]), "'NOSUCH' is none of those."), "an unknown word");
// the page
var said = map(["HTML=fixb10"]);
var F = new java.io.File(new java.io.File(WF.worldDir(), "maps"), "fixb10.html");
var page = F.isFile() ? "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8") : "";
check("html", F.isFile() && has(said, "is written to") && has(page, "<table>") && has(page, "id=\"" + aid + "\"") && has(page, "href=\"#" + aid + "\""), said);
F["delete"]();
F.getParentFile()["delete"]();

for (var i = made.length - 1; i >= 0; i--) CMLib.map().obliterateMapRoom(made[i]);
CM.core.Unsaved.clear();
CM.core.UndoLog.clear();
done();
