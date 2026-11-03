// G8: layout="MAP": a floor plan drawn in a MAP tag or a MAPFILE; . o L @ ^ v and GLYPH templates; - | + / \ links, + a door;
// a line of = for the next floor, ^ to v; the rooms get the usual tags; the plan stays the same whatever the seed.
FIXFILE = "g8-map";
var D = CM.core.Directions;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var dir = new java.io.File(WorldFiles.worldDir(), "resources/randareas");
dir.mkdirs();
var F = new java.io.File(dir, "fixg8.xml"), MF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "maps"), "fixg8.txt");
MF.getParentFile().mkdirs();
var MAP = "\n  L+o-B\n    |\n    @-^\n  =====\n      v-o\n";
function write(f, t) { java.nio.file.Files.write(f.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
write(MF, MAP);
var ROOM = '<ROOM class="StdRoom"><TITLE>Fixg8 $roomtag_nodetype $roomtag_glyph</TITLE><DESCRIPTION>Doors $roomtag_doors.</DESCRIPTION><EXIT class="Open" /></ROOM>';
write(F, '<AREADATA><AREA id="fixg8_area" layout="MAP" requires="areaname=$"><NAME>$areaname</NAME><CLASS>StdArea</CLASS>'
	+ '<MAP>' + MAP + '</MAP><GLYPH char="B" room="fixg8_boss" />' + ROOM + '</AREA>'
	+ '<AREA id="fixg8_file" layout="MAP" mapfile="world/maps/fixg8.txt" requires="areaname=$"><NAME>$areaname</NAME><CLASS>StdArea</CLASS>'
	+ '<GLYPH char="B" room="fixg8_boss" />' + ROOM + '</AREA>'
	+ '<ROOM id="fixg8_boss" class="StdRoom"><TITLE>The fixg8 boss room</TITLE><DESCRIPTION>Big.</DESCRIPTION><EXIT class="Open" /></ROOM></AREADATA>');
function cmd(words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand("Generate").execute(M, L, 0); }
function rooms(A) { var o = []; for (var r = A.getProperMap(); r.hasMoreElements();) o.push(r.nextElement()); return o; }
function titled(A, t) { return rooms(A).filter(function (R) { return s(R.displayText()) == t; }); }
function plan(A) { return rooms(A).map(function (R) { var o = [s(R.displayText())]; for (var d = 0; d < D.NUM_DIRECTIONS(); d++) o.push(R.rawDoors()[d] == null ? "-" : s(R.rawDoors()[d].displayText())); return o.join(","); }).sort().join("|"); }
var made = [];
function gen(id, name, seed) { cmd(["GENERATE", "NOSAVE", "AREA", id, "FROM", "randareas/fixg8.xml", "areaname=" + name, "SEED=" + seed, "UNLINKED"]); var A = CMLib.map().getArea(name); made.push(A); return A; }
try {
	var A = gen("fixg8_area", "Fixg8Abbey", 1);
	var L = titled(A, "Fixg8 leaf L"), B = titled(A, "The fixg8 boss room"), up = titled(A, "Fixg8 street ^"), dn = titled(A, "Fixg8 street v"), top = titled(A, "Fixg8 leaf o");
	var e0 = A.getRoom("Fixg8Abbey#0");
	check("rooms", A.properSize() == 7 && L.length == 1 && B.length == 1 && up.length == 1 && dn.length == 1 && top.length == 1 && e0 != null && has(e0.displayText(), "@"),
		"size " + A.properSize() + " titles " + rooms(A).map(function (R) { return s(R.displayText()); }).join(", "));
	var LR = L[0], o = LR.rawDoors()[D.EAST];
	var door = LR.getRawExit(D.EAST);
	check("door", o != null && door != null && door.hasADoor() && o.getRawExit(D.WEST) === door && o.rawDoors()[D.WEST] === LR && has(LR.description(), ",e,")
		&& !o.getRawExit(D.EAST).hasADoor(), "door " + (door == null ? "none" : door.ID()) + " " + LR.description());
	check("stairs", up[0].rawDoors()[D.UP] === dn[0] && dn[0].rawDoors()[D.DOWN] === up[0] && dn[0].rawDoors()[D.EAST] === top[0] && up[0].rawDoors()[D.WEST] === e0,
		"up " + up[0].rawDoors()[D.UP] + " down " + dn[0].rawDoors()[D.DOWN]);
	check("tags", o.rawDoors()[D.SOUTH] === e0 && B[0].rawDoors()[D.WEST] === o && titled(A, "Fixg8 interior o").length == 1,
		rooms(A).map(function (R) { return s(R.displayText()); }).join(", "));
	// the same plan from the file, and with another seed
	var A2 = gen("fixg8_file", "Fixg8Copy", 9);
	var p1 = plan(A), p2 = plan(A2);
	check("plan", A2.properSize() == 7 && p1 == p2, p1 + "\n" + p2);
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "layout=\"MAP\""), "no help");
} finally {
	made.forEach(function (A) { if (A != null) CMLib.map().obliterateMapArea(A); });
	if (F.exists()) F["delete"]();
	if (MF.exists()) MF["delete"]();
}
done();
