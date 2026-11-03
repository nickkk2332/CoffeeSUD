// G1: GENERATE SEED=n builds the same area each time, another seed another, no seed a new one it tells;
// seed="..." on a template tag; the save's own seed (GAME); the game's own rolls go on as before.
FIXFILE = "g1-seeds";
var GS = CM.core.GenSeed;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var here = M.location();
var dir = -1;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) if (dir < 0 && here.getRoomInDir(d) == null && here.getRawExit(d) == null) dir = d;
var dirName = s(CMLib.directions().getDirectionName(dir));
function areaNames() { var n = {}; for (var e = CMLib.map().areas(); e.hasMoreElements();) n[s(e.nextElement().Name())] = true; return n; }
function generate(seed) {
	var before = areaNames();
	var cmd = ["GENERATE", "NOSAVE", "AREA", "maze_dungeon", "FROM", "randareas/forest.xml", "areaname=Fixg1Woods", "areasize=6", "level_range=3", "aggrochance=0", "theme=forest"];
	if (seed != null) cmd.push("SEED=" + seed);
	cmd.push(dirName);
	var L = new java.util.ArrayList(); cmd.forEach(function (w) { L.add(w); });
	var st0 = CM.core.CMath.getRandState();
	CMClass.getCommand("Generate").execute(M, L, 0);
	var st1 = CM.core.CMath.getRandState();
	var A = null;
	for (var e = CMLib.map().areas(); e.hasMoreElements();) { var a = e.nextElement(); if (!before[s(a.Name())]) A = a; }
	var rooms = [];
	if (A != null) {
		for (var r = A.getProperMap(); r.hasMoreElements();) {
			var R = r.nextElement();
			var t = s(R.roomID()) + "|" + s(R.displayText()) + "|" + s(R.description());
			for (var i = 0; i < R.numInhabitants(); i++) { var m = R.fetchInhabitant(i); t += "|" + s(m.Name()) + ":" + m.basePhyStats().level(); }
			for (var j = 0; j < R.numItems(); j++) t += "|" + s(R.getItem(j).Name());
			rooms.push(t);
		}
	}
	rooms.sort();
	var out = { area: A, snap: rooms.join("\n"), flag: (A == null) ? "" : s(A.getBlurbFlag("GENSEED")), same: (st0 != null) && (st1 != null) && st0.longValue() == st1.longValue() };
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
	if (A != null) CMLib.map().obliterateMapArea(A);
	return out;
}
try {
	var a = generate(13), b = generate(13), c = generate(14);
	check("same", a.area != null && a.snap.length > 0 && a.snap == b.snap && a.flag == "13", "area " + a.area + " seed " + a.flag + "\n" + a.snap.substring(0, 300) + "\n---\n" + b.snap.substring(0, 300));
	check("other", c.area != null && c.snap != a.snap, "seed 14 built the same as 13");
	check("rolls", a.same && b.same, "the game's rolls changed: " + a.same + "/" + b.same);
	// no seed: a new one, told and kept, which builds the same again
	var n = generate(null), told = GS.last(), again = generate(told);
	check("told", n.area != null && n.flag == s(told) && n.snap == again.snap, "seed " + told + " flag " + n.flag);
	// seed="$mobseed" on a template tag
	var xml = '<MOB class="GenMob" id="fixg1_mob" level="1?60" seed="$mobseed" race="Human" gender="M">'
		+ '<NAME select="pick-1"><NAME>a fixg1 tinker</NAME><NAME>a fixg1 tailor</NAME><NAME>a fixg1 soldier</NAME><NAME>a fixg1 sailor</NAME>'
		+ '<NAME>a fixg1 rich man</NAME><NAME>a fixg1 poor man</NAME><NAME>a fixg1 beggar</NAME><NAME>a fixg1 thief</NAME></NAME>'
		+ '<DISPLAY>$mob_name stands here.</DISPLAY><ALIGNMENT>0</ALIGNMENT></MOB>';
	function mob(seed) {
		var root = CMLib.xml().parseAllXML(xml);
		var defined = new java.util.Hashtable();
		CMLib.percolator().buildDefinedIDSet(root, defined, new java.util.TreeSet());
		defined.put("MOBSEED", s(seed));
		var piece = defined.get("FIXG1_MOB");
		CMLib.percolator().preDefineReward(piece, defined);
		CMLib.percolator().defineReward(piece, defined);
		var L = CMLib.percolator().findMobs(piece, defined);
		var m = L.get(0); var t = s(m.Name()) + ":" + m.basePhyStats().level() + ":" + s(defined.get("GEN_SEED"));
		m.destroy();
		return t;
	}
	var m1 = mob(5), m2 = mob(5), m3 = mob(6), m4 = mob(7);
	check("tag", m1 == m2 && has(m1, ":5") && (m3 != m1 || m4 != m1), m1 + " / " + m2 + " / " + m3 + " / " + m4);
	// SEED=GAME: the save's own seed, with the name, so each thing named keeps its own
	GS.restore("77");
	var g1 = GS.pick("GAME", "Fixg1Cave"), g2 = GS.pick("GAME", "Fixg1Cave"), g3 = GS.pick("GAME", "Fixg1Barrow");
	check("game", g1 == g2 && g1 != g3 && s(GS.saved()) == "77" && GS.pick("40+2", null) == 42, g1 + " " + g2 + " " + g3);
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "SEED="), "no help");
} finally {
	GS.restore(null);
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
}
done();
