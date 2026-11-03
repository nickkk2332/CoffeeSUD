// G4: GENLAYOUT layout size [dir] [SEED=n] [LEVELS=n] draws a layout; GENERATE PREVIEW builds, lists and destroys; PREVIEW KEEP builds it for real with the same seed.
FIXFILE = "g4-preview";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg4.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg4_area" layout="TREE" requires="areaname=$,areasize=int">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS>'
	+ '<ROOM class="StdRoom"><TITLE select="any-1"><TITLE>A fixg4 bog</TITLE><TITLE>A fixg4 mire</TITLE><TITLE>A fixg4 fen</TITLE></TITLE><DESCRIPTION>Wet.</DESCRIPTION>'
	+ '<MOB class="GenMob" name="a fixg4 rat" level="3" display="A rat." /><EXIT class="Open" /></ROOM></AREA>'
	+ '<MOB id="fixg4_toad" class="GenMob" name="a fixg4 toad" level="2" display="A toad." /></AREADATA>').getBytes("UTF-8"));
var here = M.location();
var B = newMob("GenMob", here);
B.setName("Fixg4builder");
B.setSavable(false);
// an Archon builder (SYSOPMASK), so the template file can be read
B.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
B.baseCharStats().setCurrentClass(CMClass.getCharClass("Archon"));
B.recoverCharStats();
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var r = all.substring(mark); mark = all.length; return r; }
function cmd(C, words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand(C).execute(B, L, 0); return out(); }
var kept = null;
try {
	out();
	var d1 = cmd("GenLayout", ["GENLAYOUT", "TREE", "12", "SEED=4"]), d2 = cmd("GenLayout", ["GENLAYOUT", "TREE", "12", "SEED=4"]);
	check("genlayout", has(d1, "Layout TREE, size=") && has(d1, "leaf(") && has(d1, "Seed 4") && d1 == d2 && has(d1, "O"), d1);
	var d3 = cmd("GenLayout", ["GENLAYOUT", "LINEAR", "12", "SEED=4", "LEVELS=2"]);
	check("levels", has(d3, "Floor 1:") && has(d3, "Floor 2:") && has(d3, "Layout LINEAR"), d3);
	// a preview: listed, then gone
	var p = cmd("Generate", ["GENERATE", "PREVIEW", "AREA", "fixg4_area", "FROM", "randareas/fixg4.xml", "areaname=Fixg4Swamp", "areasize=6"]);
	var m = /Preview of AREA Fixg4Swamp \(seed (\d+)\): (\d+) rooms, (\d+) mobs/.exec(p.replace(/\s+/g, " "));
	check("preview", m != null && CMath.s_int(m[2]) >= 4 && CMath.s_int(m[3]) == CMath.s_int(m[2]) && has(p, "Fixg4Swamp#0") && has(p, "a fixg4 rat (") && has(p, "GENERATE PREVIEW KEEP")
		&& !has(p, "built unlinked") && CMLib.map().getArea("Fixg4Swamp") == null, p);
	// KEEP: the real one, the same seed and the same rooms
	var k = cmd("Generate", ["GENERATE", "PREVIEW", "KEEP"]);
	kept = CMLib.map().getArea("Fixg4Swamp");
	var same = kept != null && m != null && s(kept.getBlurbFlag("GENSEED")) == m[1] && kept.properSize() == CMath.s_int(m[2]);
	if (same) for (var r = kept.getProperMap(); r.hasMoreElements();) { var R = r.nextElement(); if (!has(p, s(R.roomID()) + " " + s(R.displayText()))) same = false; }
	check("keep", same && has(k, "Kept:"), k + " / " + (kept == null ? "none" : kept.getBlurbFlag("GENSEED")));
	// a mob: listed and gone
	var before = here.numInhabitants();
	var pm = cmd("Generate", ["GENERATE", "PREVIEW", "MOB", "fixg4_toad", "FROM", "randareas/fixg4.xml"]);
	check("mob", has(pm, "Preview of MOB") && has(pm, "a fixg4 toad (level 2)") && here.numInhabitants() == before && here.fetchInhabitant("a fixg4 toad") == null, pm);
	check("help", has(CMLib.help().getHelpText("GENLAYOUT", M, true), "LEVELS") && has(CMLib.help().getHelpText("GENERATE", M, true), "GENERATE PREVIEW"), "no help");
} finally {
	if (kept != null) CMLib.map().obliterateMapArea(kept);
	B.setPlayerStats(null);
	B.destroy();
	if (F.exists()) F["delete"]();
}
done();
