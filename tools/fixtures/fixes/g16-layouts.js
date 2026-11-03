// G16: the CAVE, CLUMP, LINEAR, RING and ISLANDS layouts; a comma list of layouts, one at random; levels="n" floors joined by
// stairs at rooms that are not leaves, with ROOMTAG_LEVEL and ROOMTAG_STAIRS.
FIXFILE = "g16-layouts";
var D = CM.core.Directions;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg16.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg16_area" layout="$layout" levels="$levels" requires="areaname=$,areasize=int,layout=$,levels=$">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS>'
	+ '<ROOM class="StdRoom"><TITLE>$roomtag_nodetype L$roomtag_level S$roomtag_stairs $AREA_LAYOUT</TITLE><DESCRIPTION>A room.</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</AREA></AREADATA>').getBytes("UTF-8"));
function cmd(words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand("Generate").execute(M, L, 0); }
function rooms(A) { var o = []; for (var r = A.getProperMap(); r.hasMoreElements();) o.push(r.nextElement()); return o; }
function reach(A) {
	var e = A.getRoom(A.Name() + "#0"), seen = [e], todo = [e];
	while (todo.length) { var R = todo.shift(); for (var d = 0; d < D.NUM_DIRECTIONS(); d++) { var N = R.rawDoors()[d]; if (N != null && N.getArea() == A && seen.indexOf(N) < 0) { seen.push(N); todo.push(N); } } }
	return seen.length;
}
function edges(A) { var c = 0; rooms(A).forEach(function (R) { for (var d = 0; d < D.NUM_DIRECTIONS(); d++) if (R.rawDoors()[d] != null) c++; }); return c / 2; }
function count(A, p) { return rooms(A).filter(function (R) { return has(R.displayText(), p); }).length; }
var made = [];
function gen(name, layout, size, seed, levels) {
	cmd(["GENERATE", "NOSAVE", "AREA", "fixg16_area", "FROM", "randareas/fixg16.xml", "areaname=" + name, "areasize=" + size, "layout=" + layout, "levels=" + (levels || 1), "SEED=" + seed, "UNLINKED"]);
	var A = CMLib.map().getArea(name); made.push(A); return A;
}
try {
	var bad = [];
	["CAVE", "CLUMP", "LINEAR", "RING", "ISLANDS"].forEach(function (lay) {
		[3, 8].forEach(function (seed) {
			var A = gen("Fixg16" + lay + seed, lay, 20, seed);
			if (A == null) { bad.push(lay + " " + seed + ": none"); return; }
			var n = A.properSize(), e0 = A.getRoom(A.Name() + "#0");
			var ok = n >= 12 && n <= 32 && reach(A) == n && e0 != null && e0.rawDoors()[D.SOUTH] == null && count(A, lay) == n;
			if (lay == "LINEAR" && !(count(A, "street") >= 6 && count(A, "leaf") >= 3)) ok = false;
			if (lay == "RING" && !(edges(A) >= n)) ok = false;
			if (lay == "CLUMP" && !(count(A, "square") >= 4)) ok = false;
			if (lay == "ISLANDS" && !(n >= 14)) ok = false;
			if (!ok) bad.push(lay + " " + seed + ": rooms " + n + " reached " + reach(A) + " edges " + edges(A) + " entrance " + (e0 == null ? "none" : e0.displayText()) + " streets " + count(A, "street") + " leaves " + count(A, "leaf") + " squares " + count(A, "square"));
		});
	});
	check("layouts", bad.length == 0, bad.join("\n"));
	// a comma list: one at random
	var seen = {};
	for (var seed = 1; seed <= 8; seed++) { var A = gen("Fixg16Mix" + seed, "LINEAR,RING", 12, seed); if (A != null) seen[count(A, "LINEAR") > 0 ? "LINEAR" : (count(A, "RING") > 0 ? "RING" : "?")] = true; }
	check("list", seen.LINEAR && seen.RING && !seen["?"], JSON.stringify(seen));
	// levels: three floors joined by stairs
	var A = gen("Fixg16Mine", "TREE", 18, 5, 3);
	var lv = [1, 2, 3].map(function (l) { return count(A, " L" + l + " "); });
	var downs = count(A, "down"), ups = count(A, "up");
	var stairsOk = true;
	rooms(A).forEach(function (R) {
		var dn = R.rawDoors()[D.DOWN];
		if (dn != null && (dn.rawDoors()[D.UP] !== R || !has(R.displayText(), "down") || !has(dn.displayText(), "up"))) stairsOk = false;
		if (has(R.displayText(), "down") && has(R.displayText(), "leaf")) stairsOk = false;
	});
	check("levels", lv[0] >= 2 && lv[1] >= 2 && lv[2] >= 2 && downs == 2 && ups == 2 && stairsOk && reach(A) == A.properSize() && has(A.getRoom(A.Name() + "#0").displayText(), " L1 "),
		"floors " + lv + " downs " + downs + " ups " + ups + " stairs " + stairsOk + " reached " + reach(A) + "/" + A.properSize());
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "levels=") && has(CMLib.help().getHelpText("GENERATE", M, true), "ISLANDS"), "no help");
} finally {
	made.forEach(function (A) { if (A != null) CMLib.map().obliterateMapArea(A); });
	if (F.exists()) F["delete"]();
}
done();
