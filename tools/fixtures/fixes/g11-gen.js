// G11: JavaScript's gen object, in every scope (JRUN's here, and a MOBPROG <SCRIPT>): item, mob, string, room, area, layout.
FIXFILE = "g11-gen";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var Dir = CM.core.Directions;
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg11.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA>'
	+ '<ITEM id="fixg11_charm" class="GenItem" level="$level" name="a fixg11 charm for $owner" />'
	+ '<MOB id="fixg11_owl" class="GenMob" level="$level" race="Bird" gender="F"><NAME>a fixg11 owl</NAME><DISPLAY>$mob_name is here.</DISPLAY><ALIGNMENT>0</ALIGNMENT></MOB>'
	+ '<STRING id="fixg11_roll">$[1?1000000]</STRING>'
	+ '<ROOM id="fixg11_room" class="StdRoom"><TITLE>A fixg11 loft</TITLE><DESCRIPTION>A small fixg11 loft.</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</AREADATA>').getBytes("UTF-8"));
var here = M.location();
var dir = -1;
for (var d = 0; d < Dir.NUM_DIRECTIONS(); d++) if (dir < 0 && here.getRoomInDir(d) == null && here.getRawExit(d) == null) dir = d;
var dirName = s(CMLib.directions().getDirectionName(dir));
var made = [];
try {
	var items = gen.item("randareas/fixg11.xml", "fixg11_charm", { level: 3, owner: "Pip" });
	var mobs = gen.mob("randareas/fixg11.xml", "fixg11_owl", { level: 6 });
	check("objects", items.length == 1 && s(items[0].Name()) == "a fixg11 charm for Pip" && items[0].owner() == null
		&& mobs.length == 1 && s(mobs[0].Name()) == "a fixg11 owl" && mobs[0].basePhyStats().level() == 6 && mobs[0].location() == null,
		"items " + items.length + " " + (items.length ? items[0].Name() : "") + " mobs " + mobs.length);
	made.push(items[0]); made.push(mobs[0]);
	var r1 = gen.string("randareas/fixg11.xml", "fixg11_roll", null), r2 = gen.string("randareas/fixg11.xml", "fixg11_roll", {});
	check("string", CMath.isInteger(s(r1)) && s(r1) != s(r2), r1 + " / " + r2);
	var loft = gen.room("randareas/fixg11.xml", "fixg11_room", dirName);
	check("room", loft != null && s(loft.displayText()) == "A fixg11 loft" && here.getRoomInDir(dir) == loft, "loft " + loft);
	if (loft != null) { here.rawDoors()[dir] = null; here.setRawExit(dir, null); loft.destroy(); }
	// a layout: the same seed, the same nodes
	function plan(seed) { var n = gen.layout("TREE", 12, seed), o = []; for (var i = 0; i < n.length; i++) o.push(n[i].type + "@" + n[i].x + "," + n[i].y + ":" + n[i].exits.join("/")); return o.join(" "); }
	var p1 = plan(5), p2 = plan(5), p3 = plan(6);
	var n0 = gen.layout("TREE", 12, 5)[0];
	check("layout", p1 == p2 && p1 != p3 && p1.split(" ").length >= 12 && n0.index == 0 && n0.exits.length > 0, p1 + "\n" + p3);
	// an area, seeded
	var A = gen.area("randareas/forest.xml", "maze_dungeon", dirName, 13, { areaname: "Fixg11Woods", areasize: 6, level_range: 3, aggrochance: 0, theme: "forest" });
	check("area", A != null && s(A.getBlurbFlag("GENSEED")) == "13" && here.getRoomInDir(dir) != null && here.getRoomInDir(dir).getArea() == A, "area " + A);
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
	if (A != null) CMLib.map().obliterateMapArea(A);
	// in a MOBPROG <SCRIPT> too
	clearVar("G11S");
	var m = newMob("GenMob", here); m.setName("Fixg11sage");
	scriptOn(m, "RAND_PROG 100\n<SCRIPT>setVar('FIXTEST','G11S',gen.item('randareas/fixg11.xml','fixg11_charm',{level:1,owner:'Wren'})[0].Name());</SCRIPT>\n~\n");
	tickMob(m); m.destroy();
	check("script", gvar("G11S") == "a fixg11 charm for Wren", "G11S " + gvar("G11S"));
	var bad = ""; try { gen.item("randareas/fixg11.xml", "fixg11_none", null); } catch (e) { bad = s(e); }
	check("errors", has(bad, "fixg11_none"), bad);
} finally {
	made.forEach(function (o) { o.destroy(); });
	F["delete"]();
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
}
done();
