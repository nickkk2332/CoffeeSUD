// G3: ROOMTAG_DEPTH, DEPTHPCT, ISDEEPEST, INDEX, X, Y and AREA_MAXDEPTH, AREA_NUMROOMS for room templates; AREA order="depth|reverse-depth|random".
FIXFILE = "g3-depth";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var Dir = CM.core.Directions;
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg3.xml");
F.getParentFile().mkdirs();
var D = "pct $roomtag_depthpct deepest $roomtag_isdeepest max $area_maxdepth rooms $area_numrooms at $roomtag_x,$roomtag_y idx $roomtag_index";
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg3_area" layout="TREE" order="$order" requires="areaname=$,areasize=int,order=$">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS><ROOM select="first">'
	+ '<ROOM class="StdRoom" condition="$roomtag_isdeepest=\'true\' and $__fixboss&lt;1" define="__fixboss+=1"><TITLE>Fixg3 boss d$roomtag_depth</TITLE><DESCRIPTION>' + D + '</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '<ROOM class="StdRoom" condition="$roomtag_depth&gt;0 and $__fixfirst&lt;1" define="__fixfirst+=1"><TITLE>Fixg3 first d$roomtag_depth</TITLE><DESCRIPTION>' + D + '</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '<ROOM class="StdRoom"><TITLE>Fixg3 room d$roomtag_depth</TITLE><DESCRIPTION>' + D + '</DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</ROOM></AREA></AREADATA>').getBytes("UTF-8"));
var here = M.location();
var dir = -1;
for (var d = 0; d < Dir.NUM_DIRECTIONS(); d++) if (dir < 0 && here.getRoomInDir(d) == null && here.getRawExit(d) == null) dir = d;
function generate(order) {
	var before = {};
	for (var e = CMLib.map().areas(); e.hasMoreElements();) before[s(e.nextElement().Name())] = true;
	var L = new java.util.ArrayList();
	["GENERATE", "NOSAVE", "AREA", "fixg3_area", "FROM", "randareas/fixg3.xml", "areaname=Fixg3Warren", "areasize=12", "order=" + order, "SEED=31", s(CMLib.directions().getDirectionName(dir))].forEach(function (w) { L.add(w); });
	CMClass.getCommand("Generate").execute(M, L, 0);
	var A = null;
	for (var e2 = CMLib.map().areas(); e2.hasMoreElements();) { var a = e2.nextElement(); if (!before[s(a.Name())]) A = a; }
	var out = { area: A, rooms: [] };
	if (A != null) {
		// the real distances, by the rooms' own exits from the entrance
		var entry = here.getRoomInDir(dir), dist = {}, todo = [entry];
		dist[s(entry.roomID())] = 0;
		while (todo.length > 0) {
			var R = todo.shift();
			for (var x = 0; x < Dir.NUM_DIRECTIONS(); x++) {
				var R2 = R.rawDoors()[x];
				if (R2 != null && R2.getArea() != null && s(R2.getArea().Name()) == s(A.Name()) && dist[s(R2.roomID())] == null) { dist[s(R2.roomID())] = dist[s(R.roomID())] + 1; todo.push(R2); }
			}
		}
		for (var r = A.getProperMap(); r.hasMoreElements();) {
			var R3 = r.nextElement();
			var m = s(R3.description()).match(/pct (\d+) deepest (\w+) max (\d+) rooms (\d+) at (-?\d+),(-?\d+) idx (\d+)/);
			var t = s(R3.displayText()).match(/Fixg3 (\w+) d(\d+)/);
			out.rooms.push({ id: s(R3.roomID()), kind: t ? t[1] : "?", depth: t ? +t[2] : -1, real: dist[s(R3.roomID())], pct: m ? +m[1] : -1, deepest: m ? m[2] : "?", max: m ? +m[3] : -1, n: m ? +m[4] : -1, xy: m ? m[5] + "," + m[6] : "?", idx: m ? +m[7] : -1 });
		}
	}
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
	if (A != null) CMLib.map().obliterateMapArea(A);
	return out;
}
try {
	var g = generate("reverse-depth");
	var rooms = g.rooms, bad = [], max = 0, xys = {}, idxs = {};
	rooms.forEach(function (r) { if (r.real > max) max = r.real; });
	rooms.forEach(function (r) {
		if (r.depth != r.real || r.max != max || r.n != rooms.length || (r.deepest == "true") != (r.real == max) || r.pct != Math.round(100 * r.real / max)) bad.push(JSON.stringify(r));
		xys[r.xy] = true; idxs[r.idx] = true;
	});
	var boss = rooms.filter(function (r) { return r.kind == "boss"; });
	check("tags", g.area != null && rooms.length > 5 && bad.length == 0 && Object.keys(xys).length == rooms.length && Object.keys(idxs).length == rooms.length,
		"rooms " + rooms.length + " bad " + bad.slice(0, 3).join(" ; "));
	check("deepest", boss.length == 1 && boss[0].real == max, "boss rooms " + JSON.stringify(boss) + " max " + max);
	// order: the first room built after the entrance, deep first or shallow first
	var shallow = generate("depth");
	function first(rs) { var f = rs.filter(function (r) { return r.kind == "first"; }); return (f.length == 1) ? f[0].real : -1; }
	check("order", first(g.rooms) > first(shallow.rooms) && first(shallow.rooms) >= 1, "reverse-depth " + first(g.rooms) + " depth " + first(shallow.rooms) + " max " + max);
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "ROOMTAG_DEPTH"), "no help");
} finally {
	F["delete"]();
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
}
done();
